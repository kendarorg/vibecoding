package org.kendar.sync.client;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.utils.Attributes;
import org.kendar.sync.lib.utils.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;

public class BaseSyncClientProcess {
    private static final Logger log = LoggerFactory.getLogger(BaseSyncClientProcess.class);

    /**
     * Recursively scans a directory and adds all files to the list.
     *
     * @param directory         The directory to scan
     * @param basePath          The base path for calculating relative paths
     * @param files             The list to add files to
     * @param ignoreHiddenFiles
     * @param ignoreSystemFiles
     * @throws IOException If an I/O error occurs
     */
    protected void scanDirectory(File directory, String basePath, List<FileInfo> files, boolean ignoreHiddenFiles, boolean ignoreSystemFiles) throws IOException {
        // Add the directory itself
        files.add(FileInfo.fromFile(directory, basePath));

        // Scan all files and subdirectories
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if(child.isHidden() && ignoreHiddenFiles) continue;
                var attr = FileUtils.readFileAttributes(child.toPath());
                if(attr.isSymbolicLink()) continue;
                if(child.getName().startsWith(".") && ignoreSystemFiles) continue;
                if (child.isDirectory()) {
                    scanDirectory(child, basePath, files, ignoreHiddenFiles, ignoreSystemFiles);
                } else {
                    files.add(FileInfo.fromFile(child, basePath));
                }
            }
        }
    }

    /**
     * Transfers a single file using a dedicated connection.
     *
     * @param file       The file to transfer
     * @param args       The command line arguments
     * @param connection The connection
     * @throws IOException If an I/O error occurs
     */
    protected void transferFile(FileInfo file, CommandLineArgs args, TcpConnection connection) throws IOException {
        String threadName = Thread.currentThread().getName();
        var connectionId = connection.getConnectionId();
        log.debug("[CLIENT-{}] Starting transfer of {}", connectionId, file.getRelativePath());
        if(file.getExtendedUmask()==0){
            var attr = FileUtils.readFileAttributes(Path.of(file.getPath()));
            if(attr!=null){
                file.setExtendedUmask(attr.getExtendedUmask());
            } else {
                log.warn("[CLIENT-{}] Could not read attributes for file: {}", connectionId, file.getRelativePath());
            }
        }

        // Create the new connection to the server

        // Send file descriptor
        FileDescriptorMessage fileDescriptorMessage = new FileDescriptorMessage(file);
        connection.sendMessage(fileDescriptorMessage);
        // Wait for file descriptor ack
        Message response = connection.receiveMessage();
        if (response.getMessageType() != MessageType.FILE_DESCRIPTOR_ACK) {
            log.error("[CLIENT-{}] Unexpected response: {}", connectionId, response.getMessageType());
            return;
        }

        FileDescriptorAckMessage fileDescriptorAck = (FileDescriptorAckMessage) response;
        if (!fileDescriptorAck.isReady()) {
            log.error("[CLIENT-{}] Server not ready to receive file: {}", connectionId, fileDescriptorAck.getErrorMessage());
            return;
        }

        // If it's a directory, no need to send data
        if (Attributes.isDirectory(file.getExtendedUmask())) {
            log.debug("[CLIENT-{}] Created directory: {}", connectionId, file.getRelativePath());
            return;
        }

        // Send file data
        if (!args.isDryRun()) {
            File sourceFile = new File(Path.of(args.getSourceFolder(), file.getRelativePath()).toString());
            long fileSize = sourceFile.length();
            int maxPacketSize = connection.getMaxPacketSize();

            // Calculate how many blocks we need to send
            int totalBlocks = (int) Math.ceil((double) fileSize / maxPacketSize);
            if (totalBlocks == 0) totalBlocks = 1; // Ensure at least one block for empty files

            log.debug("[CLIENT-{}] Sending file {} in {} blocks ({} bytes)", connectionId, file.getRelativePath(), totalBlocks, fileSize);

            try (java.io.FileInputStream fis = new java.io.FileInputStream(sourceFile)) {
                byte[] buffer = new byte[maxPacketSize];
                int blockNumber = 0;
                int bytesRead;

                while ((bytesRead = fis.read(buffer)) != -1) {
                    // If we read less than the buffer size, create a smaller array with just the data
                    byte[] blockData = bytesRead == buffer.length ? buffer : java.util.Arrays.copyOf(buffer, bytesRead);

                    FileDataMessage fileDataMessage = new FileDataMessage(
                            file.getRelativePath(), blockNumber, totalBlocks, blockData);
                    connection.sendMessage(fileDataMessage);

                    var fileAck = connection.receiveMessage();
                    if (fileAck.getMessageType() != MessageType.FILE_DATA_ACK) {
                        log.error("[CLIENT-{}] Unexpected response 9: {}", connectionId, response.getMessageType());
                        return;
                    }

                    log.debug("[CLIENT-{}] Sent block {} of {} ({} bytes)", connectionId, blockNumber + 1, totalBlocks, blockData.length);

                    blockNumber++;
                }
            }
        } else {
            log.debug("[CLIENT-{}] Dry run: Would send file data for {}", connectionId, file.getRelativePath());
        }

        // Send the file end message
        log.debug("[CLIENT-{}] Send end: {}", connectionId, file.getRelativePath());
        FileEndMessage fileEndMessage = new FileEndMessage(file.getRelativePath(), file);
        connection.sendMessage(fileEndMessage);

        // Wait for file end ack
        response = connection.receiveMessage();
        if (response.getMessageType() != MessageType.FILE_END_ACK) {
            log.error("[CLIENT-{}] Unexpected response: {}", connectionId, response.getMessageType());
            return;
        }

        FileEndAckMessage fileEndAck = (FileEndAckMessage) response;
        if (!fileEndAck.isSuccess()) {
            log.error("[CLIENT-{}] File transfer failed: {}", connectionId, fileEndAck.getErrorMessage());
            return;
        }

        log.debug("[CLIENT-{}] Transferred file: {}", connectionId, file.getRelativePath());

    }

    protected void performSingleFileRestore(CommandLineArgs args, ConcurrentLinkedQueue<TcpConnection> connections, ConcurrentHashMap<String, FileInfo> mapToTransfer, Semaphore semaphore, CountDownLatch completionLatch) {
        TcpConnection currentConnection = null;
        FileInfo fileInfo = null;
        try {
            currentConnection = connections.poll();
            if (currentConnection == null) {
                log.error("[CLIENT] No available connections for file transfer");
                return;
            }
            // Wait for file descriptor
            Message message = currentConnection.receiveMessage();
            if (message == null) {
                return;
            }
            if (message.getMessageType() != MessageType.FILE_DESCRIPTOR) {
                log.error("[CLIENT] Unexpected message 1: {}", message.getMessageType());
                return;
            }

            FileDescriptorMessage fileDescriptorMessage = (FileDescriptorMessage) message;
            fileInfo = fileDescriptorMessage.getFileInfo();
            if (!mapToTransfer.containsKey(FileUtils.makeUniformPath(fileInfo.getRelativePath()))) {
                log.debug("[CLIENT] Skipping file not in transfer list: {}", fileInfo.getRelativePath());
                // Send file descriptor ack
                FileDescriptorAckMessage fileDescriptorAck = FileDescriptorAckMessage.ready(fileInfo.getRelativePath());
                currentConnection.sendMessage(fileDescriptorAck);
                return;
            }

            log.debug("[CLIENT] Receiving file: {}", fileInfo.getRelativePath());

            // Create the file or directory
            File targetFile = new File(args.getSourceFolder(), fileInfo.getRelativePath());
            if (Attributes.isDirectory(fileInfo.getExtendedUmask())) {
                if (!args.isDryRun()) {
                    //noinspection ResultOfMethodCallIgnored
                    targetFile.mkdirs();
                } else {
                    log.debug("[CLIENT] Dry run: Would create directory {}", targetFile.getAbsolutePath());
                }

                // Send file descriptor ack
                FileDescriptorAckMessage fileDescriptorAck = FileDescriptorAckMessage.ready(fileInfo.getRelativePath());
                currentConnection.sendMessage(fileDescriptorAck);

                return;
            }

            // Create parent directories
            if (!args.isDryRun()) {
                //noinspection ResultOfMethodCallIgnored
                targetFile.getParentFile().mkdirs();
            } else {
                log.debug("[CLIENT] Dry run: Would create parent directories for {}", targetFile.getAbsolutePath());
            }

            // Send file descriptor ack
            FileDescriptorAckMessage fileDescriptorAck = FileDescriptorAckMessage.ready(fileInfo.getRelativePath());
            currentConnection.sendMessage(fileDescriptorAck);

            // Wait for file data
            message = currentConnection.receiveMessage();
            if (message.getMessageType() != MessageType.FILE_DATA) {
                log.error("[CLIENT] Unexpected message 2: {}", message.getMessageType());
                return;
            }

            FileDataMessage fileDataMessage = (FileDataMessage) message;

            while (true) {
                // Write file data
                if (!args.isDryRun()) {
                    // Create parent directories if needed
                    //noinspection ResultOfMethodCallIgnored
                    targetFile.getParentFile().mkdirs();

                    // Write the data to the file
                    try (FileOutputStream fos = new FileOutputStream(targetFile, fileDataMessage.isFirstBlock())) {
                        fos.write(fileDataMessage.getData());
                    }
                } else {
                    log.debug("[CLIENT] Dry run: Would write file data to {}", targetFile.getAbsolutePath());
                }
                currentConnection.sendMessage(new FileDataAck());
                message = currentConnection.receiveMessage();
                if (message.getMessageType() != MessageType.FILE_DATA) {
                    if (!fileDataMessage.isLastBlock()) {
                        log.error("[CLIENT] Unexpected message 3: {}", message.getMessageType());
                        return;
                    } else {
                        break;
                    }
                }
                fileDataMessage = (FileDataMessage) message;
            }

            // Wait for the file end message
            if (message.getMessageType() != MessageType.FILE_END) {
                log.error("[CLIENT] Unexpected message 4: {}", message.getMessageType());
                return;
            }

            // Send file end ack
            FileEndAckMessage fileEndAck = FileEndAckMessage.success(fileInfo.getRelativePath());
            currentConnection.sendMessage(fileEndAck);

            var realPath = targetFile.toPath();
            var attr = Files.readAttributes(realPath, BasicFileAttributes.class);
            FileUtils.writeFileAttributes(realPath,fileInfo.getExtendedUmask(),attr);
            Files.setAttribute(realPath, "creationTime", FileTime.fromMillis(fileInfo.getCreationTime().toEpochMilli()));
            Files.setLastModifiedTime(realPath, FileTime.fromMillis(fileInfo.getModificationTime().toEpochMilli()));


            log.debug("[CLIENT] Received file: {}", fileInfo.getRelativePath());
        } catch (Exception e) {
            log.error("[CLIENT] Error receiving file: {}", e.getMessage());
        } finally {
            try {
                if (currentConnection != null) connections.add(currentConnection);
                if (fileInfo != null) mapToTransfer.remove(FileUtils.makeUniformPath(fileInfo.getRelativePath()));
                completionLatch.countDown();
                semaphore.release();
            } catch (Exception e) {
                log.error("[CLIENT] Error releasing resources: {}", e.getMessage());
            }
        }
    }

    protected TcpConnection getTcpConnection(TcpConnection connection,
                                             CommandLineArgs args, int i, int maxPacketSize) throws IOException {
        Socket socket = new Socket(args.getServerAddress(), args.getServerPort());
        return new TcpConnection(socket, connection.getSessionId(),
                i + 1, maxPacketSize);
    }
}
