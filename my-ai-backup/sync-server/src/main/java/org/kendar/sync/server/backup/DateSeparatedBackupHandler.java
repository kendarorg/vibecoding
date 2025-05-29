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
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Handles backup operations for the DATE_SEPARATED backup type.
 * Files on the target that don't exist on the source are preserved.
 * Files are organized in directories based on their modification date.
 */
public class DateSeparatedBackupHandler extends BackupHandler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ConcurrentHashMap<String, FileInfo> filesOnClient = new ConcurrentHashMap<>();

    public DateSeparatedBackupHandler() {
    }

    @Override
    public void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        System.out.println("[DATE_SEPARATED] Received FILE_LIST message");

        var filesOnClient = message.getFiles().stream().collect(Collectors.toMap(
                key -> key.getRelativePath(),
                value -> value
        ));

        var allFiles = listAllFiles(Path.of(session.getFolder().getRealPath()));
        for (var file : allFiles) {
            var fts = FileUtils.makeUniformPath(file.toString().replace(session.getFolder().getRealPath(), ""));
            var filePath = session.getFolder().getRealPath() + File.separator + fts;
            BasicFileAttributes attr = Files.readAttributes(Path.of(filePath), BasicFileAttributes.class);
            if (!fts.matches(".*\\d{4}-\\d{2}-\\d{2}.*")) {
                if (!shouldUpdate(filesOnClient.get(fts), file, attr)) {
                    filesOnClient.remove(fts);
                }
                if (!message.isBackup()) {
                    if (filesOnClient.get(fts) == null) {
                        filesOnClient.put(fts, FileInfo.fromFile(file.toFile(), session.getFolder().getRealPath()));
                    }
                }
            } else {
                var newFts = fts.substring(11);
                if (!shouldUpdate(filesOnClient.get(newFts), file, attr)) {
                    filesOnClient.remove(fts);
                }// Remove the date prefix
                if (!message.isBackup()) {
                    if (filesOnClient.get(newFts) == null) {
                        var fi = FileInfo.fromFile(file.toFile(), session.getFolder().getRealPath());
                        fi.setRelativePath(newFts);
                        filesOnClient.put(newFts, fi);
                    }
                }
            }
        }
        var filesToSend = filesOnClient.values().stream().filter(f -> !f.isDirectory()).toList();
        // For DATE_SEPARATED backup type, we don't need to compare files or delete anything
        // Just acknowledge the message with an empty list
        connection.sendMessage(new FileListResponseMessage(filesToSend, new ArrayList<>(), true, 1, 1));
        if (message.isBackup()) {
            return;
        }
        var startRestoreMessage = connection.receiveMessage();
        if (startRestoreMessage.getMessageType() != MessageType.START_RESTORE) {
            System.err.println("[DATE_SEPARATED] Unexpected response 6: " + startRestoreMessage.getMessageType());
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
                        System.err.println("[DATE_SEPARATED] Unexpected response 4: " + response.getMessageType());
                        return;
                    }

                    FileDescriptorAckMessage fileDescriptorAck = (FileDescriptorAckMessage) response;
                    if (!fileDescriptorAck.isReady()) {
                        System.err.println("[DATE_SEPARATED] Server not ready to receive file: " + fileDescriptorAck.getErrorMessage());
                        return;
                    }
                    if (file.isDirectory()) {
                        System.out.println("[DATE_SEPARATED] Created directory: " + file.getRelativePath());
                        return;
                    }
                    if (!session.isDryRun()) {
                        String date = new SimpleDateFormat("yyyy-MM-dd").
                                format(new java.util.Date(file.getCreationTime().toEpochMilli()));
                        var relPath = Path.of(session.getFolder().getRealPath(), date, file.getRelativePath());
                        if (!Files.exists(relPath)) {
                            relPath = Path.of(session.getFolder().getRealPath(), file.getRelativePath());
                        }

                        File sourceFile = relPath.toFile();
                        long fileSize = sourceFile.length();
                        int maxPacketSize = currentConnection.getMaxPacketSize();

                        // Calculate how many blocks we need to send
                        int totalBlocks = (int) Math.ceil((double) fileSize / maxPacketSize);
                        if (totalBlocks == 0) totalBlocks = 1; // Ensure at least one block for empty files

                        System.out.println("[DATE_SEPARATED-" + connectionId + "] Sending file " + file.getRelativePath() +
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

                                System.out.println("[DATE_SEPARATED-" + connectionId + "] Sent block " + (blockNumber + 1) +
                                        " of " + totalBlocks + " (" + blockData.length + " bytes)");

                                blockNumber++;
                            }
                        }
                    } else {
                        System.out.println("[DATE_SEPARATED] Dry run: Would send file data for " + file.getRelativePath());
                    }

                    FileEndMessage fileEndMessage = new FileEndMessage(file.getRelativePath(), file);
                    currentConnection.sendMessage(fileEndMessage);

                    // Wait for file end ack
                    response = currentConnection.receiveMessage();
                    if (response.getMessageType() != MessageType.FILE_END_ACK) {
                        System.err.println("[DATE_SEPARATED] Unexpected response 5: " + response.getMessageType());
                        return;
                    }

                    FileEndAckMessage fileEndAck = (FileEndAckMessage) response;
                    if (!fileEndAck.isSuccess()) {
                        System.err.println("[DATE_SEPARATED] File transfer failed: " + fileEndAck.getErrorMessage());
                        return;
                    }

                    System.out.println("[DATE_SEPARATED] Transferred file: " + file.getRelativePath());
                } catch (Exception e) {
                    System.err.println("[DATE_SEPARATED] Error transferring file: " + file.getRelativePath() + " - " + e.getMessage());
                } finally {
                    if (currentConnection != null) connections.add(currentConnection);
                    completionLatch.countDown();
                    semaphore.release();
                }
            });
        }
        try {
            completionLatch.await();
            System.out.println("[DATE_SEPARATED] All file transfers completed");
        } catch (InterruptedException e) {
            System.err.println("[DATE_SEPARATED] File transfer interrupted: " + e.getMessage());
            Thread.currentThread().interrupt();
        } finally {
            executorService.shutdown();
        }
    }

    @Override
    public void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        int connectionId = connection.getConnectionId();
        System.out.println("[DATE_SEPARATED] Received FILE_DESCRIPTOR message: " + message.getFileInfo().getRelativePath() +
                " on connection " + connectionId);

        // If this is a dry run, just acknowledge the message
        if (session.isDryRun()) {
            System.out.println("Dry run: Would create file " + message.getFileInfo().getRelativePath());
            connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
            return;
        }

        // For DATE_SEPARATED backup type, we need to create a directory structure based on the file's modification date
        FileInfo fileInfo = message.getFileInfo();
        filesOnClient.put(fileInfo.getRelativePath(), fileInfo);

        connection.sendMessage(FileDescriptorAckMessage.ready(fileInfo.getRelativePath()));
    }

    public ConcurrentHashMap<String, FileInfo> getFilesOnClient() {
        return filesOnClient;
    }

    @Override
    public boolean handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException {
        // If this is a dry run, just ignore the data
        if (session.isDryRun()) {
            return true;
        }

        System.out.println("[DATE_SEPARATED] Received FILE_DATA message");
        var fileInfo = filesOnClient.get(message.getRelativePath());
        String dateDir = new java.text.SimpleDateFormat("yyyy-MM-dd").format(
                new java.util.Date(fileInfo.getCreationTime().toEpochMilli()));

        // Create the full path for the file
        String relativePath = message.getRelativePath();
        File targetFile = new File(new File(session.getFolder().getRealPath(), dateDir), relativePath);

        // Create parent directories if needed
        targetFile.getParentFile().mkdirs();

        // Write the data to the file
        try (FileOutputStream fos = new FileOutputStream(targetFile, !message.isFirstBlock())) {
            fos.write(message.getData());
        }
        return message.isLastBlock();
    }

    @Override
    public void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException {
        System.out.println("[DATE_SEPARATED] Received FILE_END message");

        var fileInfo = message.getFileInfo();

        String dateDir = new java.text.SimpleDateFormat("yyyy-MM-dd").format(
                new java.util.Date(fileInfo.getCreationTime().toEpochMilli()));
        var realPath = Path.of(session.getFolder().getRealPath() + File.separator + dateDir + File.separator + fileInfo.getRelativePath());
        Files.setAttribute(realPath, "creationTime", FileTime.fromMillis(fileInfo.getCreationTime().toEpochMilli()));
        Files.setLastModifiedTime(realPath, FileTime.fromMillis(fileInfo.getModificationTime().toEpochMilli()));
        filesOnClient.remove(fileInfo.getRelativePath());
        connection.sendMessage(FileEndAckMessage.success(message.getRelativePath()));
    }

    @Override
    public void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException {
        System.out.println("[DATE_SEPARATED] Received SYNC_END message");

        connection.sendMessage(new SyncEndAckMessage(true, "Sync completed"));
    }
}
