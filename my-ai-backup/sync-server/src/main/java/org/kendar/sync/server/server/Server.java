package org.kendar.sync.server.server;

import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.utils.Sleeper;
import org.kendar.sync.server.backup.BackupHandler;
import org.kendar.sync.server.backup.DateSeparatedBackupHandler;
import org.kendar.sync.server.backup.MirrorBackupHandler;
import org.kendar.sync.server.backup.PreserveBackupHandler;
import org.kendar.sync.server.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    public static final int TIMEOUT_SECONDS = 30;  // 30 seconds by default

    private static final Logger log = LoggerFactory.getLogger(Server.class);
    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<UUID, ClientSession> sessions = new HashMap<>();
    private final Map<BackupType, BackupHandler> backupHandlers = new HashMap<>();
    private final boolean dryRun;
    private final ServerConfig serverConfig;
    private boolean running = true;
    private ServerSocket mainSocket;
    private final SessionMonitor sessionMonitor;

    public Server(ServerConfig serverConfig, boolean dryRun) {
        this.serverConfig = serverConfig;
        this.dryRun = false;

        // Initialize backup handlers
        backupHandlers.put(BackupType.PRESERVE, new PreserveBackupHandler());
        backupHandlers.put(BackupType.MIRROR, new MirrorBackupHandler());
        backupHandlers.put(BackupType.DATE_SEPARATED, new DateSeparatedBackupHandler());

        // Initialize session monitor to check for hung sessions every 10 seconds
        this.sessionMonitor = new SessionMonitor(sessions, 10);
    }

    /**
     * Starts the TCP server.
     */
    public void startTcpServer() {
        try {
            ServerSettings settings = serverConfig.serverSettings();
            int port = settings.getPort();

            log.info("Starting TCP server on port {}", port);

            // Start the session monitor
            sessionMonitor.start();

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                mainSocket = serverSocket;
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        log.debug("[SERVER] Client connected: {}", clientSocket.getInetAddress());

                        // Handle client connection in a separate thread
                        executorService.submit(() -> handleClient(clientSocket, settings));
                    } catch (IOException e) {
                        //TODO log.error("[SERVER] Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if (running) {
                log.error("Error starting TCP server: {}", e.getMessage());
            }
        }
    }

    /**
     * Handles a client connection.
     *
     * @param clientSocket The client socket
     * @param settings     The server settings
     */
    private void handleClient(Socket clientSocket, ServerSettings settings) {
        try {
            // Create a new TCP connection
            UUID sessionId = UUID.randomUUID();
            TcpConnection connection = new TcpConnection(
                    clientSocket,
                    sessionId,
                    0,
                    settings.getMaxPacketSize()
            );

            // Wait for the connection message
            Message message = connection.receiveMessage();
            if (message == null) {
                log.error("[SERVER] Client disconnected before sending CONNECT message");
                connection.close();
                return;
            }
            if (message.getMessageType() == MessageType.START_RESTORE) {
                var session = sessions.get(message.getSessionId());
                connection.setSessionId(message.getSessionId());
                connection.setConnectionId(message.getConnectionId());
                connection.setSession(()->session.touch());
                session.setConnection(connection);
                connection.sendMessage(new StartRestoreAck());
                return;
            } else if (message.getMessageType() == MessageType.FILE_DESCRIPTOR) {
                try {
                    var session = sessions.get(message.getSessionId());
                    while (session != null) {
                        if (message == null) return;
                        connection.setSessionId(message.getSessionId());
                        connection.setConnectionId(message.getConnectionId());
                        var sess= session;
                        connection.setSession(()->sess.touch());
                        session.setConnection(connection);
                        handleFileDescriptor(connection, session, (FileDescriptorMessage) message);
                        message = connection.receiveMessage();
                        var lastMessage = message;
                        while (message.getMessageType() == MessageType.FILE_DATA) {
                            handleFileData(connection, session, (FileDataMessage) message);
                            message = connection.receiveMessage();
                        }
                        if(message.getMessageType()!=MessageType.FILE_END){
                            log.error("[SERVER] Unexpected message 1: {}", message.getMessageType());
                            return;
                        }
                        //message = connection.receiveMessage();
                        handleFileEnd(connection, session, (FileEndMessage) message);
                        session = sessions.get(message.getSessionId());
                        message = connection.receiveMessage();
                    }
                    log.debug("[SERVER] Client disconnected ");
                } catch (Exception ex) {
                    log.error("[SERVER] Client disconnected {}", ex.getMessage());
                    return;
                }
            }

            if (message.getMessageType() != MessageType.CONNECT) {
                connection.sendMessage(new ErrorMessage("ERR_PROTOCOL", "Expected CONNECT message received: " + message.getMessageType()));
                connection.close();
                return;
            }

            ConnectMessage connectMessage = (ConnectMessage) message;

            // Authenticate user
            String username = connectMessage.getUsername();
            String password = connectMessage.getPassword();

            var userOpt = settings.authenticate(username, password);
            if (userOpt.isEmpty()) {
                connection.sendMessage(new ErrorMessage("ERR_AUTH", "Authentication failed"));
                connection.close();
                return;
            }

            var user = userOpt.get();

            // Check if the user has access to the requested folder
            String targetFolder = connectMessage.getTargetFolder();
            var folderOpt = settings.getUserFolder(user.getId(), targetFolder);
            if (folderOpt.isEmpty()) {
                connection.sendMessage(new ErrorMessage("ERR_ACCESS", "Access to folder denied"));
                connection.close();
                return;
            }

            var folder = folderOpt.get();

            // Create session
            ClientSession session = new ClientSession(
                    sessionId,
                    user,
                    folder,
                    folder.getBackupType(),
                    connectMessage.isDryRun() || dryRun,
                    TIMEOUT_SECONDS
            );
            sessions.put(sessionId, session);
            session.setMainConnection(connection);

            // Set the session in the connection and touch it
            connection.setSession(()->session.touch());

            // Send connect response
            connection.sendMessage(new ConnectResponseMessage(true, null, settings.getMaxPacketSize(), settings.getMaxConnections()));

            // Handle messages
            while (true) {
                message = connection.receiveMessage();
                if(message==null){
                    break;
                }
                switch (message.getMessageType()) {
                    case FILE_LIST:
                        handleFileList(connection, session, (FileListMessage) message);
                        break;
                    case SYNC_END:
                        handleSyncEnd(connection, session, (SyncEndMessage) message);
                        // End of session
                        session.closeConnections();
                        sessions.remove(sessionId);
                        connection.close();
                        return;
                    default:
                        connection.sendMessage(new ErrorMessage("ERR_PROTOCOL", "Unexpected message type: " + message.getMessageType()));
                }
            }
        } catch (IOException e) {
            //TODO log.error("[SERVER] Error handling client: " + e.getMessage());
            try {
                clientSocket.close();
            } catch (IOException ex) {
                // Ignore
            }
        }
    }

    /**
     * Handles a file list message.
     *
     * @param connection The TCP connection
     * @param session    The client session
     * @param message    The file list message
     * @throws IOException If an I/O error occurs
     */
    private void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        // Set whether this is a backup or restore operation
        session.setBackup(message.isBackup());

        // Get the appropriate backup handler for the session's backup type
        BackupHandler handler = backupHandlers.get(session.getBackupType());
        if (handler == null) {
            log.error("No handler found for backup type 1: {}", session.getBackupType());
            connection.sendMessage(new ErrorMessage("ERR_BACKUP_TYPE", "Unsupported backup type: " + session.getBackupType()));
            return;
        }

        // Delegate to the backup handler
        handler.handleFileList(connection, session, message);
    }

    /**
     * Handles a file descriptor message.
     *
     * @param connection The TCP connection
     * @param session    The client session
     * @param message    The file descriptor message
     * @throws IOException If an I/O error occurs
     */
    private void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        // Store the current file info in the session using connection ID as index
        if (session.isBackup()) {
            session.setCurrentFile(connection.getConnectionId(), message.getFileInfo());
        }

        // Get the appropriate backup handler for the session's backup type
        BackupHandler handler = backupHandlers.get(session.getBackupType());
        if (handler == null) {
            log.error("No handler found for backup type 2: {}", session.getBackupType());
            connection.sendMessage(new ErrorMessage("ERR_BACKUP_TYPE", "Unsupported backup type: " + session.getBackupType()));
            return;
        }

        // Delegate to the backup handler
        handler.handleFileDescriptor(connection, session, message);
    }

    /**
     * Handles a file data message.
     *
     * @param connection The TCP connection
     * @param session    The client session
     * @param message    The file data message
     * @throws IOException If an I/O error occurs
     */
    private void handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException {
        // Get the appropriate backup handler for the session's backup type
        BackupHandler handler = backupHandlers.get(session.getBackupType());
        if (handler == null) {
            log.error("No handler found for backup type 3: {}", session.getBackupType());
            connection.sendMessage(new ErrorMessage("ERR_BACKUP_TYPE", "Unsupported backup type: " + session.getBackupType()));
            return;
        }

        // Delegate to the backup handler
        handler.handleFileData(connection, session, message);
        connection.sendMessage(new FileDataAck());
    }

    /**
     * Handles a file end message.
     *
     * @param connection The TCP connection
     * @param session    The client session
     * @param message    The file end message
     * @throws IOException If an I/O error occurs
     */
    private void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException {

        BackupHandler handler = backupHandlers.get(session.getBackupType());
        if (handler == null) {
            log.error("No handler found for backup type 4: {}", session.getBackupType());
            connection.sendMessage(new ErrorMessage("ERR_BACKUP_TYPE", "Unsupported backup type: " + session.getBackupType()));
            return;
        }

        // Delegate to the backup handler
        handler.handleFileEnd(connection, session, message);
    }

    /**
     * Handles a sync end message.
     *
     * @param connection The TCP connection
     * @param session    The client session
     * @param message    The sync end message
     * @throws IOException If an I/O error occurs
     */
    private void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException {
        // Get the appropriate backup handler for the session's backup type
        BackupHandler handler = backupHandlers.get(session.getBackupType());
        if (handler == null) {
            log.error("No handler found for backup type 5: {}", session.getBackupType());
            connection.sendMessage(new ErrorMessage("ERR_BACKUP_TYPE", "Unsupported backup type: " + session.getBackupType()));
            return;
        }

        // Delegate to the backup handler
        handler.handleSyncEnd(connection, session, message);
    }

    public void stop() {
        running = false;
        try {
            mainSocket.close();
        } catch (Exception ex) {
            //NOOP
        }

        try {
            sessionMonitor.close();
        } catch (Exception ex) {
            log.error("Error closing session monitor: {}", ex.getMessage());
        }
    }
}
