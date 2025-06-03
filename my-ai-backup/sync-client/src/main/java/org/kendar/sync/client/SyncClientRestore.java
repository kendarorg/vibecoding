package org.kendar.sync.client;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class SyncClientRestore extends BaseSyncClientProcess{
    private static final Logger log = LoggerFactory.getLogger(SyncClientRestore.class);
    /**
     * Performs a restore operation.
     *
     * @param connection     The TCP connection
     * @param args           The command line arguments
     * @param maxConnections Max connections to use for parallel transfers
     * @param maxPacketSize  Maximum packet size for transfers
     * @throws IOException If an I/O error occurs
     */
    public void performRestore(TcpConnection connection, CommandLineArgs args, int maxConnections, int maxPacketSize) throws IOException {
        log.debug("[CLIENT] Starting restore from {} to {}", args.getTargetFolder(), args.getSourceFolder());

        // Get the list of files to back up
        List<FileInfo> files = new ArrayList<>();
        File sourceDir = new File(args.getSourceFolder());

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            log.error("[CLIENT] 1 Source folder does not exist or is not a directory");
            return;
        }

        // Recursively scan the source directory
        scanDirectory(sourceDir, sourceDir.getAbsolutePath(), files);

        log.debug("[CLIENT] 2 Found {} files to backup", files.size());

        // Send the file list message
        FileListMessage fileListMessage = new FileListMessage(files, args.isBackup(), 1, 1);
        connection.sendMessage(fileListMessage);

        // Wait for file list response
        Message response = connection.receiveMessage();
        if (response.getMessageType() != MessageType.FILE_LIST_RESPONSE) {
            log.error("[CLIENT] Unexpected response 6: {}", response.getMessageType());
            return;
        }

        FileListResponseMessage fileListResponse = (FileListResponseMessage) response;

        var mapToTransferInitial = fileListResponse.getFilesToTransfer().stream()
                .collect(Collectors.toMap(fileInfo -> FileUtils.makeUniformPath(fileInfo.getRelativePath()), fileInfo -> fileInfo));
        var mapToTransfer = new ConcurrentHashMap<>(mapToTransferInitial);

        // Use a fixed pool of 10 threads for parallel file transfers
        ExecutorService executorService = new ThreadPoolExecutor(maxConnections, maxConnections,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        CountDownLatch completionLatch = new CountDownLatch(mapToTransfer.size());
        ConcurrentLinkedQueue<TcpConnection> connections = new ConcurrentLinkedQueue<>();// Start from 1 as the main connection is 0
        Semaphore semaphore = new Semaphore(maxConnections);
        for (int i = 0; i < maxConnections; i++) {
            TcpConnection subConnection = getTcpConnection(connection, args, i, maxPacketSize);
            connections.add(subConnection);
            Message startRestoreMessage = new StartRestore();
            startRestoreMessage.initialize(subConnection.getConnectionId(),
                    subConnection.getSessionId(), 0);
            subConnection.sendMessage(startRestoreMessage);
            var message = subConnection.receiveMessage();
            if(message.getMessageType() != MessageType.START_RESTORE_ACK){
                log.error("[CLIENT] Unexpected message 5: {}", message.getMessageType());
                throw new IOException("Unexpected message 5: " + message.getMessageType());
            }
        }

        //Sleeper.sleep(1000);
        Message startRestoreMessage = new StartRestore();
        startRestoreMessage.initialize(connection.getConnectionId(),
                connection.getSessionId(), 0);
        connection.sendMessage(startRestoreMessage);

        var message = connection.receiveMessage();
        if(message.getMessageType() != MessageType.START_RESTORE_ACK){
            log.error("[CLIENT] Unexpected message 6: {}", message.getMessageType());
            throw new IOException("Unexpected message 6: " + message.getMessageType());
        }

        // Process files to transfer
        while (!mapToTransfer.isEmpty()) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {
                //NOOP
            }
            executorService.submit(() -> performSingleFileRestore(args, connections, mapToTransfer, semaphore, completionLatch));
        }
        try {
            completionLatch.await();
            log.debug("[CLIENT] All file transfers completed 1");
            connection.close();
        } catch (InterruptedException e) {
            log.error("[CLIENT] File transfer interrupted 1: {}", e.getMessage());
        } finally {
            executorService.shutdown();
        }

        // Process files to delete
        for (String relativePath : fileListResponse.getFilesToDelete()) {
            File fileToDelete = new File(args.getSourceFolder(), relativePath);

            if (!args.isDryRun()) {
                if (fileToDelete.exists()) {
                    if (!fileToDelete.delete()) {
                        throw new IOException("Failed to delete file: " +
                                fileToDelete.getAbsolutePath());
                    }
                }
            } else {
                log.debug("[CLIENT] Dry run: Would delete file {}", fileToDelete.getAbsolutePath());
            }

            log.debug("[CLIENT] Deleted file: {}", relativePath);
        }
    }
}
