package org.kendar.sync.client;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.twoway.StatusAnalyzer;
import org.kendar.sync.lib.utils.FileUtils;
import org.kendar.sync.lib.utils.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class SyncClientSync extends BaseSyncClientProcess<SyncClientSync> {

    private static final Logger log = LoggerFactory.getLogger(SyncClientSync.class);

    public void performSync(TcpConnection connection, CommandLineArgs args, int maxConnections, int maxPacketSize,
                            boolean ignoreSystemFiles,boolean ignoreHiddenFiles,List<String> patternsToIgnore) throws IOException {
        log.debug("[CLIENT] Starting backup 1 from {} to {}", args.getSourceFolder(), args.getTargetFolder());

        // Get the list of files to back up
        List<FileInfo> files = new ArrayList<>();
        File sourceDir = new File(args.getSourceFolder());

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            log.error("[CLIENT] 3 Source folder does not exist or is not a directory");
            return;
        }
        StatusAnalyzer statusAnalyzer = new StatusAnalyzer(sourceDir.toString());
        var changes = statusAnalyzer.analyze();


        var lastUpdateTime = statusAnalyzer.getLastUpdateTime();
        if (lastUpdateTime.isEmpty()) {
            lastUpdateTime = Optional.of(Instant.now());
        }
        //Send the file sync message
        var fileSyncMessage = new FileSyncMessage();
        fileSyncMessage.setChanges(changes);
        fileSyncMessage.setLastlyUpdateTime(lastUpdateTime.get());
        connection.sendMessage(fileSyncMessage);

        // Wait for file list response
        Message response = connection.receiveMessage();
        if(response==null){
            log.error("[CLIENT] No response received from server");
            return;
        }
        if (response.getMessageType() != MessageType.FILE_LIST_RESPONSE) {
            log.error("[CLIENT] Unexpected response 3: {}", response.getMessageType());
            return;
        }


        FileListResponseMessage fileListResponse = (FileListResponseMessage) response;

        // Prepare the list of files to transfer
        List<FileInfo> filesToTransfer = fileListResponse.getFilesToTransfer();

        new Thread(() -> {
            // Process files to delete
            for (String relativePath : fileListResponse.getFilesToDelete()) {
                File fileToDelete = new File(args.getSourceFolder(), relativePath);

                if (!args.isDryRun()) {
                    if (fileToDelete.exists()) {
                        if (!fileToDelete.delete()) {
                            continue;
                        }
                    }
                } else {
                    log.debug("[CLIENT] Dry run: Would delete file {}", fileToDelete.getAbsolutePath());
                }

                log.debug("[CLIENT] Deleted file: {}", relativePath);
            }
        }).start();

        log.debug("[CLIENT] Transferring {} files with {} parallel connections", filesToTransfer.size(), maxConnections);

        // Use a fixed pool of 10 threads for parallel file transfers
         executorService = new ThreadPoolExecutor(maxConnections, maxConnections,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        CountDownLatch completionLatch = new CountDownLatch(filesToTransfer.size());
        ConcurrentLinkedQueue<TcpConnection> connections = new ConcurrentLinkedQueue<>();// Start from 1 as the main connection is 0
        Semaphore semaphore = new Semaphore(maxConnections);
        for (int i = 0; i < maxConnections; i++) {
            TcpConnection subConnection = getTcpConnection(connection, args, i, maxPacketSize);
            connections.add(subConnection);
        }

        // Process files to transfer in parallel
        for (FileInfo file : filesToTransfer) {
            executorService.submit(() -> {

                TcpConnection currentConnection = null;
                try {
                    semaphore.acquire();
                    if(!isRunning()) {
                        close();
                        //log.debug("[CLIENT-{}] Client stopped 3", connection.getConnectionId());
                        return;
                    }
                    currentConnection = connections.poll();
                    if (currentConnection == null) {
                        throw new RuntimeException("[CLIENT] No connection available");
                    }
                    log.debug("[CLIENT-{}] transferring file {}", currentConnection.getConnectionId(), file.getRelativePath());
                    transferFile(file, args, currentConnection);
                } catch (Exception e) {
                    log.error("[CLIENT] Error transferring file 1 {}: {}", file.getRelativePath(), e.getMessage());
                } finally {
                    if (currentConnection != null) connections.add(currentConnection);
                    semaphore.release();
                    completionLatch.countDown();
                }
            });
        }

        // Wait for all transfers to complete
        try {
            completionLatch.await();
            log.debug("[CLIENT] All file transfers completed 2");
        } catch (InterruptedException e) {
            log.error("[CLIENT] File transfer interrupted 2: {}", e.getMessage());
        }

        System.out.println("[CLIENT] Send all local to remote files");
        connection.sendMessage(new FileSyncMessageAck());

        // Wait for file list response
        response = connection.receiveMessage();
        if (response.getMessageType() != MessageType.FILE_LIST_RESPONSE) {
            log.error("[CLIENT] Unexpected response 6: {}", response.getMessageType());
            return;
        }

        var retrieveListResponse = (FileListResponseMessage) response;

        var mapToTransferInitialRetrieve = retrieveListResponse.getFilesToTransfer().stream()
                .collect(Collectors.toMap(fileInfo -> FileUtils.makeUniformPath(fileInfo.getRelativePath()), fileInfo -> fileInfo));
        var mapToTransferRetrieve = new ConcurrentHashMap<>(mapToTransferInitialRetrieve);


        // Use a fixed pool of 10 threads for parallel file transfers
        var completionLatchRetrieve = new CountDownLatch(mapToTransferRetrieve.size());
        ConcurrentLinkedQueue<TcpConnection> connectionsRetrieve = new ConcurrentLinkedQueue<>();// Start from 1 as the main connection is 0
        var semaphoreRetrieve = new Semaphore(maxConnections);
        for (int i = 0; i < maxConnections; i++) {
            TcpConnection subConnection = getTcpConnection(connection, args, i, maxPacketSize);
            connectionsRetrieve.add(subConnection);
            Message startRestoreMessage = new StartRestore();
            startRestoreMessage.initialize(subConnection.getConnectionId(),
                    subConnection.getSessionId(), 0);
            subConnection.sendMessage(startRestoreMessage);
            var message = subConnection.receiveMessage();
            if (message.getMessageType() != MessageType.START_RESTORE_ACK) {
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
        if (message.getMessageType() != MessageType.START_RESTORE_ACK) {
            log.error("[CLIENT] Unexpected message 6: {}", message.getMessageType());
            throw new IOException("Unexpected message 6: " + message.getMessageType());
        }

        // Process files to transfer
        while (!mapToTransferRetrieve.isEmpty()) {
            try {
                semaphoreRetrieve.acquire();
            } catch (InterruptedException e) {
                //NOOP
            }
            executorService.submit(() -> performSingleFileRestore(args, connectionsRetrieve, mapToTransferRetrieve, semaphoreRetrieve,
                    completionLatchRetrieve));
        }
        try {
            //noinspection ConstantValue
            while (!mapToTransferRetrieve.isEmpty()) {
                Sleeper.sleep(100);
            }
            completionLatchRetrieve.await();
            log.debug("[CLIENT] All file transfers completed 8");
            var cc =counter.incrementAndGet();
        } catch (InterruptedException e) {
            log.error("[CLIENT] File transfer interrupted 8: {}", e.getMessage());
        } finally {
            var cc = counter.get();
            statusAnalyzer.analyze();
            executorService.shutdown();
        }

    }
    private AtomicInteger counter = new AtomicInteger(0);
}
