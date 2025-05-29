package org.kendar.sync.server.server;

import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.server.SyncServerApplication;
import org.kendar.sync.server.backup.BackupHandler;
import org.kendar.sync.server.backup.DateSeparatedBackupHandler;
import org.kendar.sync.server.backup.MirrorBackupHandler;
import org.kendar.sync.server.backup.PreserveBackupHandler;
import org.kendar.sync.server.config.ServerConfig;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Server {

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<UUID, ClientSession> sessions = new HashMap<>();
    private final Map<BackupType, BackupHandler> backupHandlers = new HashMap<>();
    private boolean running = true;
    private boolean dryRun = false;
    private ServerSocket mainSocket;

    public Server(ServerConfig serverConfig, boolean dryRun) {
        this.serverConfig = serverConfig;
        this.dryRun = dryRun;

        // Initialize backup handlers
        backupHandlers.put(BackupType.PRESERVE, new PreserveBackupHandler());
        backupHandlers.put(BackupType.MIRROR, new MirrorBackupHandler());
        backupHandlers.put(BackupType.DATE_SEPARATED, new DateSeparatedBackupHandler());
    }
    private ServerConfig serverConfig;
    /**
     * Starts the TCP server.
     */
    public void startTcpServer() {
        try {
            ServerSettings settings = serverConfig.serverSettings();
            int port = settings.getPort();

            System.out.println("Starting TCP server on port " + port);

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                mainSocket = serverSocket;
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("[SERVER] Client connected: " + clientSocket.getInetAddress());

                        // Handle client connection in a separate thread
                        executorService.submit(() -> handleClient(clientSocket, settings));
                    } catch (IOException e) {
                        System.err.println("[SERVER] Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            if(running) {
                System.err.println("Error starting TCP server: " + e.getMessage());
            }
        }
    }

    /**
     * Handles a client connection.
     *
     * @param clientSocket The client socket
     * @param settings The server settings
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

            // Wait for connect message
            Message message = connection.receiveMessage();
            if(message.getMessageType()==MessageType.FILE_DESCRIPTOR) {
                try {
                    var session = sessions.get(message.getSessionId());
                    while (session!=null) {
                        connection.setSessionId(message.getSessionId());
                        connection.setConnectionId(message.getConnectionId());
                        session.setConnection( connection);
                        handleFileDescriptor(connection, session, (FileDescriptorMessage) message);
                        message = connection.receiveMessage();
                        while(!handleFileData(connection, session, (FileDataMessage) message)){
                            message = connection.receiveMessage();
                        }
                        message = connection.receiveMessage();
                        handleFileEnd(connection, session, (FileEndMessage) message);
                        session = sessions.get(message.getSessionId());
                        message = connection.receiveMessage();
                    }
                    System.out.println("[SERVER] Client disconnected " );
                }catch (Exception ex){
                    System.err.println("[SERVER] Client disconnected " +ex.getMessage());
                    return;
                }
            }

            if (message.getMessageType() != MessageType.CONNECT) {
                connection.sendMessage(new ErrorMessage("ERR_PROTOCOL", "Expected CONNECT message"));
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

            // Check if user has access to the requested folder
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
                    connectMessage.isDryRun() || dryRun
            );
            sessions.put(sessionId, session);
            session.setMainConnection(connection);

            // Send connect response
            connection.sendMessage(new ConnectResponseMessage(true, null, settings.getMaxPacketSize(), settings.getMaxConnections()));

            // Handle messages
            while (true) {
                message = connection.receiveMessage();
                switch (message.getMessageType()) {
                    case FILE_LIST:
                        handleFileList(connection, session, (FileListMessage) message);
                        break;
                    case FILE_DESCRIPTOR:
                        handleFileDescriptor(connection, session, (FileDescriptorMessage) message);
                        break;
                    case FILE_DATA:
                        handleFileData(connection, session, (FileDataMessage) message);
                        break;
                    case FILE_END:
                        handleFileEnd(connection, session, (FileEndMessage) message);
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
            System.err.println("Error handling client: " + e.getMessage());
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
     * @param session The client session
     * @param message The file list message
     * @throws IOException If an I/O error occurs
     */
    private void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        //System.out.println("[SERVER] Received FILE_LIST message");

        // Set whether this is a backup or restore operation
        session.setBackup(message.isBackup());

        // Get the appropriate backup handler for the session's backup type
        BackupHandler handler = backupHandlers.get(session.getBackupType());
        if (handler == null) {
            System.err.println("No handler found for backup type: " + session.getBackupType());
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
     * @param session The client session
     * @param message The file descriptor message
     * @throws IOException If an I/O error occurs
     */
    private void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        //System.out.println("[SERVER] Received FILE_DESCRIPTOR message: " + message.getFileInfo().getRelativePath() +
        //                 " on connection " + connection.getConnectionId());

        // Store the current file info in the session using connection ID as index
        if (session.isBackup()) {
            session.setCurrentFile(connection.getConnectionId(), message.getFileInfo());
        }

        // Get the appropriate backup handler for the session's backup type
        BackupHandler handler = backupHandlers.get(session.getBackupType());
        if (handler == null) {
            System.err.println("No handler found for backup type: " + session.getBackupType());
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
     * @param session The client session
     * @param message The file data message
     * @throws IOException If an I/O error occurs
     */
    private boolean handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException {
        int connectionId = connection.getConnectionId();
        //System.out.println("[SERVER] Received FILE_DATA message on connection " + connectionId);

        // Get the appropriate backup handler for the session's backup type
        BackupHandler handler = backupHandlers.get(session.getBackupType());
        if (handler == null) {
            System.err.println("No handler found for backup type: " + session.getBackupType());
            connection.sendMessage(new ErrorMessage("ERR_BACKUP_TYPE", "Unsupported backup type: " + session.getBackupType()));
            return false;
        }

        // Delegate to the backup handler
        return handler.handleFileData(connection, session, message);
    }

    /**
     * Handles a file end message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The file end message
     * @throws IOException If an I/O error occurs
     */
    private void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException {

        BackupHandler handler = backupHandlers.get(session.getBackupType());
        if (handler == null) {
            System.err.println("No handler found for backup type: " + session.getBackupType());
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
     * @param session The client session
     * @param message The sync end message
     * @throws IOException If an I/O error occurs
     */
    private void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException {
        //System.out.println("[SERVER] Received SYNC_END message");

        // Get the appropriate backup handler for the session's backup type
        BackupHandler handler = backupHandlers.get(session.getBackupType());
        if (handler == null) {
            System.err.println("No handler found for backup type: " + session.getBackupType());
            connection.sendMessage(new ErrorMessage("ERR_BACKUP_TYPE", "Unsupported backup type: " + session.getBackupType()));
            return;
        }

        // Delegate to the backup handler
        handler.handleSyncEnd(connection, session, message);
    }

    public void stop() {
        running=false;
        try{
            mainSocket.close();
        }catch (Exception ex){

        }
    }
}
