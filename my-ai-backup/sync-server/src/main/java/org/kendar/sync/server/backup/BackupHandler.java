package org.kendar.sync.server.backup;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.server.server.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Base class for handling backup operations based on the backup type.
 */
public abstract class BackupHandler {

    private static final Logger log = LoggerFactory.getLogger(BackupHandler.class);

    /**
     * Handles a file list message.
     *
     * @param connection The TCP connection
     * @param session    The client session
     * @param message    The file list message
     * @throws IOException If an I/O error occurs
     */
    public abstract void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException;

    /**
     * Handles a file descriptor message.
     *
     * @param connection The TCP connection
     * @param session    The client session
     * @param message    The file descriptor message
     * @throws IOException If an I/O error occurs
     */
    public abstract void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException;

    /**
     * Handles a file data message.
     *
     * @param connection The TCP connection
     * @param session    The client session
     * @param message    The file data message
     * @throws IOException If an I/O error occurs
     */
    public abstract void handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException;

    /**
     * Handles a file end message.
     *
     * @param connection The TCP connection
     * @param session    The client session
     * @param message    The file end message
     * @throws IOException If an I/O error occurs
     */
    public abstract void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException;

    /**
     * Handles a sync end message.
     *
     * @param connection The TCP connection
     * @param session    The client session
     * @param message    The sync end message
     * @throws IOException If an I/O error occurs
     */

    public void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException {
        log.debug("[SERVER] Received SYNC_END message");
        connection.sendMessage(new SyncEndAckMessage(true, "Sync completed"));
    }

    /**
     * Gets the source file path for the given file info during restore operations.
     * This allows different handlers to customize where files are read from.
     */
    protected abstract Path getSourceFilePath(ClientSession session, FileInfo fileInfo);

    /**
     * Common implementation for handling file restore operations.
     * This method handles the common workflow of sending files to the client during restore.
     */
    protected void handleFileRestore(TcpConnection connection, ClientSession session, List<FileInfo> filesToSend) throws IOException {
        var startRestoreMessage = connection.receiveMessage();
        if (startRestoreMessage.getMessageType() != MessageType.START_RESTORE) {
            log.error("[SERVER] Unexpected response 1: {}", startRestoreMessage.getMessageType());
            return;
        }

        var connections = new ConcurrentLinkedQueue<>(session.getConnections());
        var maxConnections = connections.size();
        ExecutorService executorService = new ThreadPoolExecutor(maxConnections, maxConnections,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());

        var onlyFilesToTransfer = filesToSend.stream()
                .filter(f -> !f.isDirectory())
                .collect(Collectors.toMap(FileInfo::getRelativePath, f -> f));
        CountDownLatch completionLatch = new CountDownLatch(onlyFilesToTransfer.size());

        for (var file : filesToSend) {
            if (file.isDirectory()) {
                continue;
            }
            executorService.submit(() -> transferFile(connections, session, file, completionLatch));
        }

        try {
            completionLatch.await();
            log.debug("[SERVER] All file transfers completed");
            connection.close();
        } catch (InterruptedException e) {
            log.error("[SERVER] File transfer interrupted: {}", e.getMessage());
        } finally {
            executorService.shutdown();
        }
    }

    /**
     * Transfers a single file to the client.
     */
    private void transferFile(ConcurrentLinkedQueue<TcpConnection> connections, ClientSession session,
                              FileInfo file, CountDownLatch completionLatch) {
        TcpConnection currentConnection = null;
        try {
            currentConnection = connections.poll();
            if (currentConnection == null) {
                log.error("[SERVER] No available connections to transfer file: {}", file.getRelativePath());
                return;
            }
            var connectionId = currentConnection.getConnectionId();

            // Send file descriptor
            FileDescriptorMessage fileDescriptorMessage = new FileDescriptorMessage(file);
            currentConnection.sendMessage(fileDescriptorMessage);

            var response = currentConnection.receiveMessage();
            if (response.getMessageType() != MessageType.FILE_DESCRIPTOR_ACK) {
                log.error("[SERVER] Unexpected response 2: {}", response.getMessageType());
                return;
            }

            FileDescriptorAckMessage fileDescriptorAck = (FileDescriptorAckMessage) response;
            if (!fileDescriptorAck.isReady()) {
                log.error("[SERVER] Server not ready to receive file: {}", fileDescriptorAck.getErrorMessage());
                return;
            }

            if (file.isDirectory()) {
                log.debug("[SERVER] Created directory: {}", file.getRelativePath());
                return;
            }

            // Send file data
            if (!session.isDryRun()) {
                sendFileData(currentConnection, session, file, connectionId);
            } else {
                log.debug("[SERVER] Dry run: Would send file data for {}", file.getRelativePath());
            }

            // Send the file termination message
            FileEndMessage fileEndMessage = new FileEndMessage(file.getRelativePath(), file);
            currentConnection.sendMessage(fileEndMessage);

            // Wait for file end ack
            response = currentConnection.receiveMessage();
            if (response.getMessageType() != MessageType.FILE_END_ACK) {
                log.error("[SERVER] Unexpected response 3: {}", response.getMessageType());
                return;
            }

            FileEndAckMessage fileEndAck = (FileEndAckMessage) response;
            if (!fileEndAck.isSuccess()) {
                log.error("[SERVER] File transfer failed: {}", fileEndAck.getErrorMessage());
                return;
            }

            log.debug("[SERVER] Transferred file: {}", file.getRelativePath());
        } catch (Exception e) {
            log.error("[SERVER] Error transferring file: {} - {}", file.getRelativePath(), e.getMessage());
        } finally {
            if (currentConnection != null) connections.add(currentConnection);
            completionLatch.countDown();
        }
    }

    /**
     * Sends file data in chunks to the client.
     */
    private void sendFileData(TcpConnection connection, ClientSession session, FileInfo file, int connectionId) throws IOException {
        Path sourcePath = getSourceFilePath(session, file);
        File sourceFile = sourcePath.toFile();

        long fileSize = sourceFile.length();
        int maxPacketSize = connection.getMaxPacketSize();

        // Calculate how many blocks we need to send
        int totalBlocks = (int) Math.ceil((double) fileSize / maxPacketSize);
        if (totalBlocks == 0) totalBlocks = 1; // Ensure at least one block for empty files

        log.debug("[SERVER-{}] Sending file {} in {} blocks ({} bytes)", connectionId, file.getRelativePath(), totalBlocks, fileSize);

        try (FileInputStream fis = new FileInputStream(sourceFile)) {
            byte[] buffer = new byte[maxPacketSize];
            int blockNumber = 0;
            int bytesRead;

            while ((bytesRead = fis.read(buffer)) != -1) {
                // If we read less than the buffer size, create a smaller array with just the data
                byte[] blockData = bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);

                FileDataMessage fileDataMessage = new FileDataMessage(
                        file.getRelativePath(), blockNumber, totalBlocks, blockData);
                connection.sendMessage(fileDataMessage);

                log.debug("[SERVER-{}] Sent block {} of {} ({} bytes)", connectionId, blockNumber + 1, totalBlocks, blockData.length);

                blockNumber++;
            }
        }
    }

    protected List<Path> listAllFiles(Path sourcePath) throws IOException {
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            return new ArrayList<>();
        }

        try(var result = Files.walk(sourcePath)) {
            return result.filter(path -> !Files.isDirectory(path))
                    .collect(Collectors.toList());
        }
    }

    protected List<Path> listAllFilesAndDirs(Path sourcePath) throws IOException {
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            return new ArrayList<>();
        }

        try(var result = Files.walk(sourcePath)) {
            return result.collect(Collectors.toList());
        }
    }

    protected boolean shouldUpdate(FileInfo fileInfo, Path file, BasicFileAttributes attr) {
        if (fileInfo == null) return false;
        if (fileInfo.getModificationTime().isAfter(attr.lastModifiedTime().toInstant())) return false;
        if (fileInfo.getCreationTime().isAfter(attr.creationTime().toInstant())) return false;
        return fileInfo.getSize() == attr.size();
    }
}