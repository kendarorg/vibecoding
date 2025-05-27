package org.kendar.sync.server;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.server.config.ServerConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.ArrayList;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Main class for the sync server application.
 */
@SpringBootApplication
public class SyncServerApplication implements CommandLineRunner {

    @Autowired
    private ServerConfig serverConfig;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<UUID, ClientSession> sessions = new HashMap<>();
    private boolean running = true;
    private boolean dryRun = false;

    /**
     * Main method to start the application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(SyncServerApplication.class, args);
    }

    /**
     * Command line runner that starts the TCP server.
     *
     * @param args Command line arguments
     */
    @Override
    public void run(String... args) {
        // Parse command line arguments
        for (String arg : args) {
            if (arg.equals("--dry-run")) {
                dryRun = true;
                System.out.println("Running in dry-run mode. No actual file operations will be performed.");
            }
        }

        // Start the TCP server in a separate thread
        executorService.submit(this::startTcpServer);
    }

    /**
     * Starts the TCP server.
     */
    private void startTcpServer() {
        try {
            ServerSettings settings = serverConfig.serverSettings();
            int port = settings.getPort();

            System.out.println("Starting TCP server on port " + port);

            try (ServerSocket serverSocket = new ServerSocket(port)) {
                while (running) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        System.out.println("Client connected: " + clientSocket.getInetAddress());

                        // Handle client connection in a separate thread
                        executorService.submit(() -> handleClient(clientSocket, settings));
                    } catch (IOException e) {
                        System.err.println("Error accepting client connection: " + e.getMessage());
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error starting TCP server: " + e.getMessage());
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
                connectMessage.getBackupType(),
                connectMessage.isDryRun() || dryRun
            );
            sessions.put(sessionId, session);

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
        // Implementation depends on the backup type and whether it's a dry run
        System.out.println("Received FILE_LIST message");

        // For now, just acknowledge the message with an empty list
        connection.sendMessage(new FileListResponseMessage(new ArrayList<>(), new ArrayList<>(), true, 1, 1));
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
        System.out.println("Received FILE_DESCRIPTOR message: " + message.getFileInfo().getRelativePath());

        // If this is a dry run, just acknowledge the message
        if (session.isDryRun()) {
            System.out.println("Dry run: Would create file " + message.getFileInfo().getRelativePath());
            connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
            return;
        }

        // Create the file or directory
        File file = new File(session.getFolder().getRealPath(), message.getFileInfo().getRelativePath());

        if (message.getFileInfo().isDirectory()) {
            file.mkdirs();
        } else {
            file.getParentFile().mkdirs();
        }

        connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
    }

    /**
     * Handles a file data message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The file data message
     * @throws IOException If an I/O error occurs
     */
    private void handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException {
        // If this is a dry run, just ignore the data
        if (session.isDryRun()) {
            return;
        }

        // Implementation depends on the backup type
        // For now, just print a message
        System.out.println("Received FILE_DATA message");
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
        System.out.println("Received FILE_END message");

        connection.sendMessage(FileEndAckMessage.success(message.getRelativePath()));
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
        System.out.println("Received SYNC_END message");

        connection.sendMessage(new SyncEndAckMessage(true, "Sync completed"));
    }

    /**
     * Represents a client session.
     */
    private static class ClientSession {
        private final UUID sessionId;
        private final ServerSettings.User user;
        private final ServerSettings.BackupFolder folder;
        private final BackupType backupType;
        private final boolean dryRun;

        public ClientSession(UUID sessionId, ServerSettings.User user, ServerSettings.BackupFolder folder, 
                            BackupType backupType, boolean dryRun) {
            this.sessionId = sessionId;
            this.user = user;
            this.folder = folder;
            this.backupType = backupType;
            this.dryRun = dryRun;
        }

        public UUID getSessionId() {
            return sessionId;
        }

        public ServerSettings.User getUser() {
            return user;
        }

        public ServerSettings.BackupFolder getFolder() {
            return folder;
        }

        public BackupType getBackupType() {
            return backupType;
        }

        public boolean isDryRun() {
            return dryRun;
        }
    }
}
