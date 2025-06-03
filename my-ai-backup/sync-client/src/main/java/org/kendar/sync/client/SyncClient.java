package org.kendar.sync.client;

import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.utils.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

@SuppressWarnings("DuplicatedCode")
public class SyncClient {

    public static final int DEFAULT_MAX_PACKET_SIZE = 1024 * 1024; // 1 MB
    private static final int DEFAULT_MAX_CONNECTIONS = 5;

    private static final Logger log = LoggerFactory.getLogger(SyncClient.class);


    /**
     * Validates command line arguments.
     *
     * @param args The command line arguments
     * @return Whether the arguments are valid
     */
    private boolean validateArgs(CommandLineArgs args) {
        boolean valid = true;

        if (args.getSourceFolder() == null) {
            log.error("[CLIENT] Source folder is required (--source)");
            valid = false;
        } else if (!Files.exists(Path.of(args.getSourceFolder()))) {
            log.error("[CLIENT] Source folder does not exists (--source)");
            valid = false;
        }

        if (args.getTargetFolder() == null) {
            log.error("[CLIENT] Target folder is required (--target)");
            valid = false;
        }

        if (args.getServerAddress() == null) {
            log.error("[CLIENT] Server address is required (--server)");
            valid = false;
        }

        if (args.getUsername() == null) {
            log.error("[CLIENT] Username is required (--username)");
            valid = false;
        }

        if (args.getPassword() == null) {
            log.error("[CLIENT] Password is required (--password)");
            valid = false;
        }

        if (!valid) {
            log.error("\nUse --help for usage information");
        }

        return valid;
    }


    public void doSync(CommandLineArgs commandLineArgs) {
        // Validate arguments
        if (!validateArgs(commandLineArgs)) {
            return;
        }

        try {
            // Connect to server
            log.debug("[CLIENT] Connecting to server {}:{}", commandLineArgs.getServerAddress(), commandLineArgs.getServerPort());

            if (commandLineArgs.isDryRun()) {
                log.debug("[CLIENT] Running in dry-run mode. No actual file operations will be performed.");
            }

            Socket socket = new Socket(commandLineArgs.getServerAddress(), commandLineArgs.getServerPort());
            UUID sessionId = UUID.randomUUID();

            try (TcpConnection connection = new TcpConnection(
                    socket,
                    sessionId,
                    0,
                    DEFAULT_MAX_PACKET_SIZE)) {

                // Send the connection message
                ConnectMessage connectMessage = new ConnectMessage(
                        commandLineArgs.getUsername(),
                        commandLineArgs.getPassword(),
                        commandLineArgs.getTargetFolder(),
                        DEFAULT_MAX_PACKET_SIZE,
                        DEFAULT_MAX_CONNECTIONS,
                        commandLineArgs.isDryRun()
                );

                connection.sendMessage(connectMessage);

                // Wait for the connection response
                Message response = connection.receiveMessage();
                if (response.getMessageType() != MessageType.CONNECT_RESPONSE) {
                    log.error("[CLIENT] Unexpected response 1: {}", response.getMessageType());
                    return;
                }

                ConnectResponseMessage connectResponse = (ConnectResponseMessage) response;
                if (!connectResponse.isAccepted()) {
                    log.error("[CLIENT] Connection rejected: {}", connectResponse.getErrorMessage());
                    return;
                }
                connection.setSessionId(connectResponse.getSessionId());
                var maxConnections = Math.min(commandLineArgs.getMaxConnections(), connectResponse.getMaxConnections());
                var maxPacketSize = Math.min(commandLineArgs.getMaxSize(), connectResponse.getMaxPacketSize());
                if (maxConnections == 0) maxConnections = connectResponse.getMaxConnections();
                if (maxPacketSize == 0) maxPacketSize = connectResponse.getMaxPacketSize();

                log.debug("[CLIENT] Connected to server");

                // Perform backup or restore
                if (connectResponse.getBackupType() == BackupType.TWO_WAY_SYNC) {
                    new SyncClientSync().peformSync(connection, commandLineArgs, maxConnections, maxPacketSize);
                } else if (commandLineArgs.isBackup()) {
                    new SyncClientBackup().performBackup(connection, commandLineArgs, maxConnections, maxPacketSize);
                } else {
                    new SyncClientRestore().performRestore(connection, commandLineArgs, maxConnections, maxPacketSize);
                }
                Sleeper.sleep(200);
                // Send sync end message
                connection.sendMessage(new SyncEndMessage());

                // Wait for sync end ack
                response = connection.receiveMessage();
                if (response.getMessageType() != MessageType.SYNC_END_ACK) {
                    log.error("[CLIENT] Unexpected response 2: {}", response.getMessageType());
                    return;
                }

                SyncEndAckMessage syncEndAck = (SyncEndAckMessage) response;
                if (!syncEndAck.isSuccess()) {
                    log.error("[CLIENT] Sync failed: {}", syncEndAck.getErrorMessage());
                    return;
                }

                log.debug("[CLIENT] Sync completed successfully");
                connection.close();
            }
        } catch (InterruptedException | IOException e) {
            //TODO log.error("[CLIENT] Error: " + e.getMessage());
        }
    }


}
