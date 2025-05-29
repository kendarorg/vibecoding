package org.kendar.sync.server.backup;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.utils.FileUtils;
import org.kendar.sync.server.server.ClientSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Handles backup operations for the PRESERVE backup type.
 * Files on the target that don't exist on the source are preserved.
 */
public class MirrorBackupHandler extends BackupHandler {

    public MirrorBackupHandler() {
    }

    @Override
    public void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        System.out.println("[MIRROR] Received FILE_LIST message");

        var filesOnClient = message.getFiles().stream().collect(Collectors.toMap(
                key -> key.getRelativePath(),
                value -> value
        ));

        var filesToRemove = message.getFiles().stream().
                map(key -> key.getRelativePath()).
                collect(Collectors.toSet());

        var allFiles = listAllFilesAndDirs(Path.of(session.getFolder().getRealPath()));
        var removedFiles = new ArrayList<String>();

        for (var file : allFiles) {
            var fts = FileUtils.makeUniformPath(file.toString().replace(session.getFolder().getRealPath(), ""));
            var filePath = session.getFolder().getRealPath() + "/" + fts;
            BasicFileAttributes attr = Files.readAttributes(Path.of(filePath), BasicFileAttributes.class);

            if (file.toFile().isDirectory()) {
                filesOnClient.remove(fts);
                filesToRemove.remove(fts);
                continue;
            }
            if (message.isBackup() && !filesOnClient.containsKey(fts)) {
                Files.delete(Path.of(filePath));
                continue;
            } else if (!message.isBackup()) {
                filesToRemove.remove(fts);
            }

            if (message.isBackup() && !shouldUpdate(filesOnClient.get(fts), file, attr)) {
                filesOnClient.remove(fts);
            } else if (!message.isBackup()) {
                if (filesOnClient.get(fts) == null) {
                    var fi = FileInfo.fromFile(file.toFile(), session.getFolder().getRealPath());
                    filesOnClient.put(fi.getRelativePath(), fi);
                } else if (!shouldUpdate(filesOnClient.get(fts), file, attr)) {
                    // If the file exists on the client but is not updated, we remove it
                    filesOnClient.remove(fts);
                }
            }
        }
        if (!message.isBackup()) {
            removedFiles.addAll(filesToRemove);
            for (var toRemove : filesToRemove) {
                filesOnClient.remove(toRemove);
            }
        }
        var filesToSend = filesOnClient.values().stream().filter(f -> !f.isDirectory()).toList();
        connection.sendMessage(new FileListResponseMessage(filesToSend, removedFiles, true, 1, 1));
        if (message.isBackup()) {
            return;
        }
        var startRestoreMessage = connection.receiveMessage();
        if (startRestoreMessage.getMessageType() != MessageType.START_RESTORE) {
            System.err.println("[MIRROR] Unexpected response 6: " + startRestoreMessage.getMessageType());
            return;
        }
        var connections = new ConcurrentLinkedQueue<TcpConnection>(session.getConnections());
        var maxConnections = connections.size();
        ExecutorService executorService = new ThreadPoolExecutor(maxConnections, maxConnections,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        var onlyFilesToTransfer = filesToSend.stream()
                .filter(f -> !f.isDirectory())
                .collect(Collectors.toMap(FileInfo::getRelativePath, f -> f));
        CountDownLatch completionLatch = new CountDownLatch(onlyFilesToTransfer.size());
        Semaphore semaphore = new Semaphore(maxConnections);
        for (var file : filesToSend) {
            if (file.isDirectory()) {
                continue;
            }
            executorService.submit(() -> {
                TcpConnection currentConnection = null;
                try {
                    currentConnection = connections.poll();
                    var connectionId = currentConnection.getConnectionId();
                    FileDescriptorMessage fileDescriptorMessage = new FileDescriptorMessage(file);
                    currentConnection.sendMessage(fileDescriptorMessage);

                    var response = currentConnection.receiveMessage();
                    if (response.getMessageType() != MessageType.FILE_DESCRIPTOR_ACK) {
                        System.err.println("[MIRROR] Unexpected response 4: " + response.getMessageType());
                        return;
                    }

                    FileDescriptorAckMessage fileDescriptorAck = (FileDescriptorAckMessage) response;
                    if (!fileDescriptorAck.isReady()) {
                        System.err.println("[MIRROR] Server not ready to receive file: " + fileDescriptorAck.getErrorMessage());
                        return;
                    }
                    if (file.isDirectory()) {
                        System.out.println("[MIRROR] Created directory: " + file.getRelativePath());
                        return;
                    }
                    if (!session.isDryRun()) {
                        var relPath = Path.of(session.getFolder().getRealPath(), file.getRelativePath());
                        File sourceFile = relPath.toFile();

                        long fileSize = sourceFile.length();
                        int maxPacketSize = currentConnection.getMaxPacketSize();

                        // Calculate how many blocks we need to send
                        int totalBlocks = (int) Math.ceil((double) fileSize / maxPacketSize);
                        if (totalBlocks == 0) totalBlocks = 1; // Ensure at least one block for empty files

                        System.out.println("[MIRROR-" + connectionId + "] Sending file " + file.getRelativePath() +
                                " in " + totalBlocks + " blocks (" + fileSize + " bytes)");

                        try (java.io.FileInputStream fis = new java.io.FileInputStream(sourceFile)) {
                            byte[] buffer = new byte[maxPacketSize];
                            int blockNumber = 0;
                            int bytesRead;

                            while ((bytesRead = fis.read(buffer)) != -1) {
                                // If we read less than the buffer size, create a smaller array with just the data
                                byte[] blockData = bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);

                                FileDataMessage fileDataMessage = new FileDataMessage(
                                        file.getRelativePath(), blockNumber, totalBlocks, blockData);
                                currentConnection.sendMessage(fileDataMessage);

                                System.out.println("[MIRROR-" + connectionId + "] Sent block " + (blockNumber + 1) +
                                        " of " + totalBlocks + " (" + blockData.length + " bytes)");

                                blockNumber++;
                            }
                        }
                    } else {
                        System.out.println("[MIRROR] Dry run: Would send file data for " + file.getRelativePath());
                    }

                    FileEndMessage fileEndMessage = new FileEndMessage(file.getRelativePath(), file);
                    currentConnection.sendMessage(fileEndMessage);

                    // Wait for file end ack
                    response = currentConnection.receiveMessage();
                    if (response.getMessageType() != MessageType.FILE_END_ACK) {
                        System.err.println("[MIRROR] Unexpected response 5: " + response.getMessageType());
                        return;
                    }

                    FileEndAckMessage fileEndAck = (FileEndAckMessage) response;
                    if (!fileEndAck.isSuccess()) {
                        System.err.println("[MIRROR] File transfer failed: " + fileEndAck.getErrorMessage());
                        return;
                    }

                    System.out.println("[MIRROR] Transferred file: " + file.getRelativePath());
                } catch (Exception e) {
                    System.err.println("[MIRROR] Error transferring file: " + file.getRelativePath() + " - " + e.getMessage());
                } finally {
                    if (currentConnection != null) connections.add(currentConnection);
                    completionLatch.countDown();
                    semaphore.release();
                }
            });
        }
        try {
            completionLatch.await();
            System.out.println("[MIRROR] All file transfers completed");
            connection.close();
        } catch (InterruptedException e) {
            System.err.println("[MIRROR] File transfer interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            executorService.shutdown();
        }
    }

    @Override
    public void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        int connectionId = connection.getConnectionId();
        System.out.println("[MIRROR] Received FILE_DESCRIPTOR message: " + message.getFileInfo().getRelativePath() +
                " on connection " + connectionId);

        // If this is a dry run, just acknowledge the message
        if (session.isDryRun()) {
            System.out.println("[MIRROR] Dry run: Would create file " + message.getFileInfo().getRelativePath());
            connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
            return;
        }

        connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
    }

    @Override
    public boolean handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException {
        // If this is a dry run, just ignore the data
        if (session.isDryRun()) {
            return true;
        }

        int connectionId = connection.getConnectionId();
        FileInfo fileInfo = null;

        // Get the file info from the session if it's a backup operation
        if (session.isBackup()) {
            fileInfo = session.getCurrentFile(connectionId);
            if (fileInfo == null) {
                System.err.println("[MIRROR] No file info found for connection " + connectionId);
                return true;
            }
            System.out.println("[MIRROR] Received FILE_DATA message for " + fileInfo.getRelativePath() +
                    " on connection " + connectionId +
                    " (block " + (message.getBlockNumber() + 1) + " of " + message.getTotalBlocks() +
                    ", " + message.getData().length + " bytes)");
        } else {
            System.out.println("[MIRROR] Received FILE_DATA message for " + message.getRelativePath() +
                    " on connection " + connectionId +
                    " (block " + (message.getBlockNumber() + 1) + " of " + message.getTotalBlocks() +
                    ", " + message.getData().length + " bytes)");
        }

        // Write the data to the file
        String relativePath = session.isBackup() ? fileInfo.getRelativePath() : message.getRelativePath();
        File file = new File(session.getFolder().getRealPath(), relativePath);
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file, !message.isFirstBlock())) {
            fos.write(message.getData());
        }
        return message.isLastBlock();

    }

    @Override
    public void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException {
        int connectionId = connection.getConnectionId();
        FileInfo fileInfo = null;

        // Get the file info - either from the message or from the session
        if (session.isBackup()) {
            fileInfo = message.getFileInfo() != null ? message.getFileInfo() : session.getCurrentFile(connectionId);
            if (fileInfo == null) {
                System.err.println("[MIRROR] No file info found for connection " + connectionId);
                connection.sendMessage(FileEndAckMessage.failure(message.getRelativePath(), "No file info found"));
                return;
            }
            System.out.println("[MIRROR] Received FILE_END message for " + fileInfo.getRelativePath() +
                    " on connection " + connectionId);
        } else {
            fileInfo = message.getFileInfo();
            System.out.println("[MIRROR] Received FILE_END message for " + message.getRelativePath() +
                    " on connection " + connectionId);
        }

        var realPath = Path.of(session.getFolder().getRealPath() + File.separator + fileInfo.getRelativePath());
        Files.setAttribute(realPath, "creationTime", FileTime.fromMillis(fileInfo.getCreationTime().toEpochMilli()));
        Files.setLastModifiedTime(realPath, FileTime.fromMillis(fileInfo.getModificationTime().toEpochMilli()));

        connection.sendMessage(FileEndAckMessage.success(fileInfo.getRelativePath()));
    }

    @Override
    public void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException {
        System.out.println("[MIRROR] Received SYNC_END message");

        connection.sendMessage(new SyncEndAckMessage(true, "Sync completed"));
    }
}
