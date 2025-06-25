package org.kendar.sync.client;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.FileListMessage;
import org.kendar.sync.lib.protocol.FileListResponseMessage;
import org.kendar.sync.lib.protocol.Message;
import org.kendar.sync.lib.protocol.MessageType;
import org.kendar.sync.lib.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class SyncClientBackup extends BaseSyncClientProcess {
    private final Logger log = LoggerFactory.getLogger(SyncClientBackup.class);

    /**
     * Performs a backup operation.
     *
     * @param connection     The TCP connection
     * @param args           The command line arguments
     * @param maxConnections Maximum connections to use for parallel transfers
     * @param maxPacketSize  Maximum packet size for transfers
     * @throws IOException If an I/O error occurs
     */
    public void performBackup(TcpConnection connection, CommandLineArgs args, int maxConnections, int maxPacketSize,
                              boolean ignoreSystemFiles,boolean ignoreHiddenFiles,List<String> patternsToIgnore) throws IOException {
        log.debug("[CLIENT] Starting backup from {} to {}", args.getSourceFolder(), args.getTargetFolder());

        // Get the list of files to back up
        List<FileInfo> files = new ArrayList<>();
        File sourceDir = new File(args.getSourceFolder());

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            log.error("[CLIENT] 3 Source folder does not exist or is not a directory");
            return;
        }

        // Recursively scan the source directory
        scanDirectory(sourceDir, sourceDir.getAbsolutePath(), files,args.isIgnoreHiddenFiles(),args.isIgnoreSystemFiles());

        log.debug("[CLIENT] 4 Found {} files to backup", files.size());

        // Send the file list message
        FileListMessage fileListMessage = new FileListMessage(files, args.isBackup(), 1, 1);
        connection.sendMessage(fileListMessage);

        // Wait for file list response
        Message response = connection.receiveMessage();
        if (response.getMessageType() != MessageType.FILE_LIST_RESPONSE) {
            log.error("[CLIENT] Unexpected response 3: {}", response.getMessageType());
            return;
        }


        FileListResponseMessage fileListResponse = (FileListResponseMessage) response;
        var mapToTransfer = fileListResponse.getFilesToTransfer()
                .stream().collect(Collectors.toMap(fileInfo -> FileUtils.makeUniformPath(fileInfo.getRelativePath()), fileInfo -> fileInfo));

        // Prepare the list of files to transfer
        List<FileInfo> filesToTransfer = files.stream()
                .filter(file -> mapToTransfer.containsKey(FileUtils.makeUniformPath(file.getRelativePath())))
                .collect(Collectors.toList());


        log.debug("[CLIENT] Transferring {} files with {} parallel connections", filesToTransfer.size(), maxConnections);

        // Use a fixed pool of 10 threads for parallel file transfers
        ExecutorService executorService = new ThreadPoolExecutor(maxConnections, maxConnections,
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
                    currentConnection = connections.poll();
                    if (currentConnection == null) {
                        throw new RuntimeException("[CLIENT] No connection available");
                    }
                    log.debug("[CLIENT-{}] transferring file {}", currentConnection.getConnectionId(), file.getRelativePath());
                    transferFile(file, args, currentConnection);
                } catch (Exception e) {
                    log.error("[CLIENT] Error transferring file 2 {}: {}", file.getRelativePath(), e.getMessage());
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
            log.debug("[CLIENT] All file transfers completed 3");
            //XXX connection.close();
        } catch (InterruptedException e) {
            log.error("[CLIENT] File transfer interrupted 2: {}", e.getMessage());
        } finally {
            executorService.shutdown();
        }
    }
}
