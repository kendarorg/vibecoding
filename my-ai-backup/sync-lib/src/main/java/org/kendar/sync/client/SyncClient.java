package org.kendar.sync.client;

import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.utils.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Timer;
import java.util.UUID;
import java.util.stream.Collectors;

public class SyncClient {

    public static final int DEFAULT_MAX_PACKET_SIZE = 1024 * 1024; // 1 MB
    private static final int DEFAULT_MAX_CONNECTIONS = 5;

    private static final Logger log = LoggerFactory.getLogger(SyncClient.class);
    private Timer timer;
    private Socket socket;

    public void setKeepAlive(int keepAlive) {
        this.keepAlive = keepAlive;
    }

    private int keepAlive=-1;


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

    private boolean isRunning = true;
    public void disconnect(){
        isRunning =false;
        if(socket != null){
            try {
                socket.close();
            } catch (Exception e) {

            }
        }
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

            socket = new Socket(commandLineArgs.getServerAddress(), commandLineArgs.getServerPort());
            UUID sessionId = UUID.randomUUID();
            var maxPacketSize = DEFAULT_MAX_PACKET_SIZE;
            if(commandLineArgs.getMaxSize()==0){
                maxPacketSize = commandLineArgs.getMaxSize();
            }
            try (TcpConnection connection = new TcpConnection(
                    socket,
                    sessionId,
                    0,
                    maxPacketSize,
                    false)) {

                // Send the connection message
                ConnectMessage connectMessage = new ConnectMessage(
                        commandLineArgs.getUsername(),
                        commandLineArgs.getPassword(),
                        commandLineArgs.getTargetFolder(),
                        DEFAULT_MAX_PACKET_SIZE,
                        DEFAULT_MAX_CONNECTIONS,
                        commandLineArgs.isDryRun(),
                        commandLineArgs.getHostName(),
                        commandLineArgs.isIgnoreSystemFiles(),
                        commandLineArgs.isIgnoreHiddenFiles(),
                        commandLineArgs.getIgnoredPatterns() != null ? commandLineArgs.getIgnoredPatterns() : List.of()
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
                var keepAlive = 3000L;
                if(this.keepAlive>0){
                    keepAlive = this.keepAlive;
                }
                this.timer = new Timer("idle-timeout-task", true);
                this.timer.schedule(new SyncClient.TimerTask(connection,this.timer), keepAlive, keepAlive);

                connection.setSessionId(connectResponse.getSessionId());
                var maxConnections = Math.min(commandLineArgs.getMaxConnections(), connectResponse.getMaxConnections());
                maxPacketSize = Math.min(commandLineArgs.getMaxSize(), connectResponse.getMaxPacketSize());
                connection.setMaxPacketSize(maxPacketSize);
                if (maxConnections == 0) maxConnections = connectResponse.getMaxConnections();
                if (maxPacketSize == 0) maxPacketSize = connectResponse.getMaxPacketSize();

                log.debug("[CLIENT] Connected to server");

                // Perform backup or restore
                if (connectResponse.getBackupType() == BackupType.TWO_WAY_SYNC) {
                    new SyncClientSync().setCheckRunning(()->this.isRunning).performSync(connection, commandLineArgs, maxConnections, maxPacketSize,
                            connectResponse.isIgnoreSystemFiles(),
                            connectResponse.isIgnoreHiddenFiles(),
                            connectResponse.getIgnoredPatterns());
                } else if (commandLineArgs.isBackup()) {
                    new SyncClientBackup().setCheckRunning(()->this.isRunning).performBackup(connection, commandLineArgs, maxConnections, maxPacketSize,
                            connectResponse.isIgnoreSystemFiles(),
                            connectResponse.isIgnoreHiddenFiles(),
                            connectResponse.getIgnoredPatterns());
                } else {
                    new SyncClientRestore().setCheckRunning(()->this.isRunning).performRestore(connection, commandLineArgs, maxConnections, maxPacketSize,
                            connectResponse.isIgnoreSystemFiles(),
                            connectResponse.isIgnoreHiddenFiles(),
                            connectResponse.getIgnoredPatterns());
                }
                Sleeper.sleep(200);
                log.debug("[CLIENT] Completed main operation, shutting down");
                // Send sync end message
                connection.sendMessage(new SyncEndMessage());

                log.debug("[CLIENT-{}] Waiting for end ",connection.getConnectionId()); //KEND

                // Wait for sync end ack
                response = connection.receiveMessage();
                if (response ==null || response.getMessageType() != MessageType.SYNC_END_ACK) {
                    log.warn("[CLIENT] Unexpected response 2: {}", response.getMessageType());
                    return;
                }

                SyncEndAckMessage syncEndAck = (SyncEndAckMessage) response;
                if (!syncEndAck.isSuccess()) {
                    log.error("[CLIENT] Sync failed: {}", syncEndAck.getErrorMessage());
                    return;
                }
                log.debug("[CLIENT] Shutting down");
                Sleeper.sleep(100);
                connection.close();
                this.timer.cancel();

                log.debug("[CLIENT] Sync completed successfully");
            }
        } catch ( IOException e) {
            log.trace("[CLIENT] Error: {}", e.getMessage());
        }
    }



    public class TimerTask extends java.util.TimerTask {
        private final Timer timer;
        private final TcpConnection mainConnection;

        public TimerTask(TcpConnection mainConnection, Timer timer) {
            this.mainConnection = mainConnection;
            this.timer = timer;
        }

        @Override
        public void run() {
            if(mainConnection!=null) {
                try {
                    if(!mainConnection.isClosed()) {
                        log.debug("[CLIENT-{}] KEEPALIVE",mainConnection.getConnectionId());
                        mainConnection.sendMessage(new KeepAlive());
                    }
                } catch (Exception e) {
                    log.error("[CLIENT-{}] KEEPALIVE",mainConnection.getConnectionId(),e);
                    timer.cancel();
                }
            }
        }
    }
}
