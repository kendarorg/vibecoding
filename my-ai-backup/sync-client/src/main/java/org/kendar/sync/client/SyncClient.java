package org.kendar.sync.client;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.utils.FileUtils;
import org.kendar.sync.lib.utils.Sleeper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class SyncClient {

    public static final int DEFAULT_MAX_PACKET_SIZE = 1024 * 1024; // 1 MB
    private static final int DEFAULT_MAX_CONNECTIONS = 5;

    protected TcpConnection getTcpConnection(TcpConnection connection,
                                             CommandLineArgs args, int i, int maxPacketSize) throws IOException {
        Socket socket = new Socket(args.getServerAddress(), args.getServerPort());
        TcpConnection subConnection = new TcpConnection(socket, connection.getSessionId(),
                i + 1, maxPacketSize);
        return subConnection;
    }

    /**
     * Performs a restore operation.
     *
     * @param connection     The TCP connection
     * @param args           The command line arguments
     * @param maxConnections
     * @param maxPacketSize
     * @throws IOException If an I/O error occurs
     */
    private void performRestore(TcpConnection connection, CommandLineArgs args, int maxConnections, int maxPacketSize) throws IOException {
        System.out.println("[CLIENT] Starting restore from " + args.getTargetFolder() + " to " + args.getSourceFolder());

        // Get list of files to backup
        List<FileInfo> files = new ArrayList<>();
        File sourceDir = new File(args.getSourceFolder());

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            System.err.println("[CLIENT] Source folder does not exist or is not a directory");
            return;
        }

        // Recursively scan the source directory
        scanDirectory(sourceDir, sourceDir.getAbsolutePath(), files);

        System.out.println("[CLIENT] Found " + files.size() + " files to backup");

        // Send file list message
        FileListMessage fileListMessage = new FileListMessage(files, args.isBackup(), 1, 1);
        connection.sendMessage(fileListMessage);

        // Wait for file list response
        Message response = connection.receiveMessage();
        if (response.getMessageType() != MessageType.FILE_LIST_RESPONSE) {
            System.err.println("[CLIENT] Unexpected response 6: " + response.getMessageType());
            return;
        }

        FileListResponseMessage fileListResponse = (FileListResponseMessage) response;

        var mapToTransferInital = fileListResponse.getFilesToTransfer().stream()
                .collect(Collectors.toMap(fileInfo -> {
                    return FileUtils.makeUniformPath(fileInfo.getRelativePath());
                }, fileInfo -> fileInfo));
        var mapToTransfer = new ConcurrentHashMap<>(mapToTransferInital);

        // Use a fixed pool of 10 threads for parallel file transfers
        ExecutorService executorService = new ThreadPoolExecutor(maxConnections, maxConnections,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        CountDownLatch completionLatch = new CountDownLatch(mapToTransfer.size());
        ConcurrentLinkedQueue<TcpConnection> connections = new ConcurrentLinkedQueue<>();// Start from 1 as main connection is 0
        Semaphore semaphore = new Semaphore(maxConnections);
        for (int i = 0; i < maxConnections; i++) {
            TcpConnection subConnection = getTcpConnection(connection, args, i, maxPacketSize);
            connections.add(subConnection);
            Message startRestoreMessage = new StartRestore();
            startRestoreMessage.initialize(subConnection.getConnectionId(),
                    subConnection.getSessionId(), 0);
            subConnection.sendMessage(startRestoreMessage);
        }
        Sleeper.sleep(1000);
        Message startRestoreMessage = new StartRestore();
        startRestoreMessage.initialize(connection.getConnectionId(),
                connection.getSessionId(), 0);
        connection.sendMessage(startRestoreMessage);

        // Process files to transfer
        while (!mapToTransfer.isEmpty()) {
            try {
                semaphore.acquire();
            } catch (InterruptedException e) {

            }
            executorService.submit(() -> {
                Message fileName = null;
                TcpConnection currentConnection = null;
                try {
                    currentConnection = connections.poll();
                    // Wait for file descriptor
                    Message message = currentConnection.receiveMessage();
                    if (message == null) {
                        return;
                    }
                    if (message.getMessageType() != MessageType.FILE_DESCRIPTOR) {
                        System.err.println("[CLIENT] Unexpected message 1: " + message.getMessageType());
                        return;
                    }

                    fileName = message;
                    FileDescriptorMessage fileDescriptorMessage = (FileDescriptorMessage) message;
                    FileInfo fileInfo = fileDescriptorMessage.getFileInfo();
                    if (!mapToTransfer.containsKey(FileUtils.makeUniformPath(fileInfo.getRelativePath()))) {
                        System.out.println("[CLIENT] Skipping file not in transfer list: " + fileInfo.getRelativePath());
                        // Send file descriptor ack
                        FileDescriptorAckMessage fileDescriptorAck = FileDescriptorAckMessage.ready(fileInfo.getRelativePath());
                        currentConnection.sendMessage(fileDescriptorAck);
                        return;
                    }
                    mapToTransfer.remove(FileUtils.makeUniformPath(fileInfo.getRelativePath()));
                    semaphore.release();
                    System.out.println("[CLIENT] Receiving file: " + fileInfo.getRelativePath());

                    // Create the file or directory
                    File targetFile = new File(args.getSourceFolder(), fileInfo.getRelativePath());

                    if (fileInfo.isDirectory()) {
                        if (!args.isDryRun()) {
                            targetFile.mkdirs();
                        } else {
                            System.out.println("[CLIENT] Dry run: Would create directory " + targetFile.getAbsolutePath());
                        }

                        // Send file descriptor ack
                        FileDescriptorAckMessage fileDescriptorAck = FileDescriptorAckMessage.ready(fileInfo.getRelativePath());
                        currentConnection.sendMessage(fileDescriptorAck);

                        return;
                    }

                    // Create parent directories
                    if (!args.isDryRun()) {
                        targetFile.getParentFile().mkdirs();
                    } else {
                        System.out.println("[CLIENT] Dry run: Would create parent directories for " + targetFile.getAbsolutePath());
                    }

                    // Send file descriptor ack
                    FileDescriptorAckMessage fileDescriptorAck = FileDescriptorAckMessage.ready(fileInfo.getRelativePath());
                    currentConnection.sendMessage(fileDescriptorAck);

                    // Wait for file data
                    message = currentConnection.receiveMessage();
                    if (message.getMessageType() != MessageType.FILE_DATA) {
                        System.err.println("[CLIENT] Unexpected message 2: " + message.getMessageType());
                        return;
                    }

                    FileDataMessage fileDataMessage = (FileDataMessage) message;

                    while (true) {
                        // Write file data
                        if (!args.isDryRun()) {
                            // Create parent directories if needed
                            targetFile.getParentFile().mkdirs();

                            // Write the data to the file
                            try (FileOutputStream fos = new FileOutputStream(targetFile, !fileDataMessage.isFirstBlock())) {
                                fos.write(fileDataMessage.getData());
                            }
                        } else {
                            System.out.println("[CLIENT] Dry run: Would write file data to " + targetFile.getAbsolutePath());
                        }
                        message = currentConnection.receiveMessage();
                        if (message.getMessageType() != MessageType.FILE_DATA) {
                            if (!fileDataMessage.isLastBlock()) {
                                System.err.println("[CLIENT] Unexpected message 3: " + message.getMessageType());
                                return;
                            } else {
                                break;
                            }
                        }
                        fileDataMessage = (FileDataMessage) message;
                    }

                    // Wait for file end
                    if (message.getMessageType() != MessageType.FILE_END) {
                        System.err.println("[CLIENT] Unexpected message 4: " + message.getMessageType());
                        return;
                    }

                    // Send file end ack
                    FileEndAckMessage fileEndAck = FileEndAckMessage.success(fileInfo.getRelativePath());
                    currentConnection.sendMessage(fileEndAck);

                    var realPath = targetFile.toPath();
                    Files.setAttribute(realPath, "creationTime", FileTime.fromMillis(fileInfo.getCreationTime().toEpochMilli()));
                    Files.setLastModifiedTime(realPath, FileTime.fromMillis(fileInfo.getModificationTime().toEpochMilli()));


                    System.out.println("[CLIENT] Received file: " + fileInfo.getRelativePath());
                } catch (Exception e) {
                    System.err.println("[CLIENT] Error receiving file: " + e.getMessage());
                } finally {
                    if (currentConnection != null) connections.add(currentConnection);
                    completionLatch.countDown();
                    semaphore.release();
                }
            });
        }
        try {
            completionLatch.await();
            System.out.println("[CLIENT] All file transfers completed 1");
            connection.close();
        } catch (InterruptedException e) {
            System.err.println("[CLIENT] File transfer interrupted 1: " + e.getMessage());
        } finally {
            executorService.shutdown();
        }

        // Process files to delete
        for (String relativePath : fileListResponse.getFilesToDelete()) {
            File fileToDelete = new File(args.getSourceFolder(), relativePath);

            if (!args.isDryRun()) {
                if (fileToDelete.exists()) {
                    fileToDelete.delete();
                }
            } else {
                System.out.println("[CLIENT] Dry run: Would delete file " + fileToDelete.getAbsolutePath());
            }

            System.out.println("[CLIENT] Deleted file: " + relativePath);
        }
    }

    /**
     * Recursively scans a directory and adds all files to the list.
     *
     * @param directory The directory to scan
     * @param basePath  The base path for calculating relative paths
     * @param files     The list to add files to
     * @throws IOException If an I/O error occurs
     */
    private void scanDirectory(File directory, String basePath, List<FileInfo> files) throws IOException {
        // Add the directory itself
        files.add(FileInfo.fromFile(directory, basePath));

        // Scan all files and subdirectories
        File[] children = directory.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) {
                    scanDirectory(child, basePath, files);
                } else {
                    files.add(FileInfo.fromFile(child, basePath));
                }
            }
        }
    }

    /**
     * Validates command line arguments.
     *
     * @param args The command line arguments
     * @return Whether the arguments are valid
     */
    private boolean validateArgs(CommandLineArgs args) {
        boolean valid = true;

        if (args.getSourceFolder() == null) {
            System.err.println("[CLIENT] Source folder is required (--source)");
            valid = false;
        } else if (!Files.exists(Path.of(args.getSourceFolder()))) {
            System.err.println("[CLIENT] Source folder does not exists (--source)");
            valid = false;
        }

        if (args.getTargetFolder() == null) {
            System.err.println("[CLIENT] Target folder is required (--target)");
            valid = false;
        }

        if (args.getServerAddress() == null) {
            System.err.println("[CLIENT] Server address is required (--server)");
            valid = false;
        }

        if (args.getUsername() == null) {
            System.err.println("[CLIENT] Username is required (--username)");
            valid = false;
        }

        if (args.getPassword() == null) {
            System.err.println("[CLIENT] Password is required (--password)");
            valid = false;
        }

        if (!valid) {
            System.err.println("\nUse --help for usage information");
        }

        return valid;
    }

    /**
     * Transfers a single file using a dedicated connection.
     *
     * @param file       The file to transfer
     * @param args       The command line arguments
     * @param connection Theconnection
     * @throws IOException If an I/O error occurs
     */
    private void transferFile(FileInfo file, CommandLineArgs args, TcpConnection connection) throws IOException {
        String threadName = Thread.currentThread().getName();
        var connectionId = connection.getConnectionId();
        System.out.println("[CLIENT-" + connectionId + "] Starting transfer of " + file.getRelativePath());

        // Create new connection to the server

        // Send file descriptor
        FileDescriptorMessage fileDescriptorMessage = new FileDescriptorMessage(file);
        connection.sendMessage(fileDescriptorMessage);
        // Wait for file descriptor ack
        Message response = connection.receiveMessage();
        if (response.getMessageType() != MessageType.FILE_DESCRIPTOR_ACK) {
            System.err.println("[CLIENT-" + connectionId + "] Unexpected response: " + response.getMessageType());
            return;
        }

        FileDescriptorAckMessage fileDescriptorAck = (FileDescriptorAckMessage) response;
        if (!fileDescriptorAck.isReady()) {
            System.err.println("[CLIENT-" + connectionId + "] Server not ready to receive file: " + fileDescriptorAck.getErrorMessage());
            return;
        }

        // If it's a directory, no need to send data
        if (file.isDirectory()) {
            System.out.println("[CLIENT-" + connectionId + "] Created directory: " + file.getRelativePath());
            return;
        }

        // Send file data
        if (!args.isDryRun()) {
            File sourceFile = new File(file.getPath());
            long fileSize = sourceFile.length();
            int maxPacketSize = connection.getMaxPacketSize();

            // Calculate how many blocks we need to send
            int totalBlocks = (int) Math.ceil((double) fileSize / maxPacketSize);
            if (totalBlocks == 0) totalBlocks = 1; // Ensure at least one block for empty files

            System.out.println("[CLIENT-" + connectionId + "] Sending file " + file.getRelativePath() +
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
                    connection.sendMessage(fileDataMessage);

                    System.out.println("[CLIENT-" + connectionId + "] Sent block " + (blockNumber + 1) +
                            " of " + totalBlocks + " (" + blockData.length + " bytes)");

                    blockNumber++;
                }
            }
        } else {
            System.out.println("[CLIENT-" + connectionId + "] Dry run: Would send file data for " + file.getRelativePath());
        }

        // Send file end
        System.out.println("[CLIENT-" + connectionId + "] Send end: " + file.getRelativePath());
        FileEndMessage fileEndMessage = new FileEndMessage(file.getRelativePath(), file);
        connection.sendMessage(fileEndMessage);

        // Wait for file end ack
        response = connection.receiveMessage();
        if (response.getMessageType() != MessageType.FILE_END_ACK) {
            System.err.println("[CLIENT-" + connectionId + "] Unexpected response: " + response.getMessageType());
            return;
        }

        FileEndAckMessage fileEndAck = (FileEndAckMessage) response;
        if (!fileEndAck.isSuccess()) {
            System.err.println("[CLIENT-" + connectionId + "] File transfer failed: " + fileEndAck.getErrorMessage());
            return;
        }

        System.out.println("[CLIENT-" + connectionId + "] Transferred file: " + file.getRelativePath());

    }

    public void doSync(CommandLineArgs commandLineArgs) {
        // Validate arguments
        if (!validateArgs(commandLineArgs)) {
            return;
        }

        try {
            // Connect to server
            System.out.println("[CLIENT] Connecting to server " + commandLineArgs.getServerAddress() + ":" + commandLineArgs.getServerPort());

            if (commandLineArgs.isDryRun()) {
                System.out.println("[CLIENT] Running in dry-run mode. No actual file operations will be performed.");
            }

            Socket socket = new Socket(commandLineArgs.getServerAddress(), commandLineArgs.getServerPort());
            UUID sessionId = UUID.randomUUID();

            try (TcpConnection connection = new TcpConnection(
                    socket,
                    sessionId,
                    0,
                    DEFAULT_MAX_PACKET_SIZE)) {

                // Send connect message
                ConnectMessage connectMessage = new ConnectMessage(
                        commandLineArgs.getUsername(),
                        commandLineArgs.getPassword(),
                        commandLineArgs.getTargetFolder(),
                        DEFAULT_MAX_PACKET_SIZE,
                        DEFAULT_MAX_CONNECTIONS,
                        commandLineArgs.getBackupType(),
                        commandLineArgs.isDryRun()
                );

                connection.sendMessage(connectMessage);

                // Wait for connect response
                Message response = connection.receiveMessage();
                if (response.getMessageType() != MessageType.CONNECT_RESPONSE) {
                    System.err.println("[CLIENT] Unexpected response 1: " + response.getMessageType());
                    return;
                }

                ConnectResponseMessage connectResponse = (ConnectResponseMessage) response;
                if (!connectResponse.isAccepted()) {
                    System.err.println("[CLIENT] Connection rejected: " + connectResponse.getErrorMessage());
                    return;
                }
                connection.setSessionId(connectResponse.getSessionId());
                var maxConnections = Math.min(commandLineArgs.getMaxConnections(), connectResponse.getMaxConnections());
                var maxPacketSize = Math.min(commandLineArgs.getMaxSize(), connectResponse.getMaxPacketSize());
                if (maxConnections == 0) maxConnections = connectResponse.getMaxConnections();
                if (maxPacketSize == 0) maxPacketSize = connectResponse.getMaxPacketSize();

                System.out.println("[CLIENT] Connected to server");

                // Perform backup or restore
                if (commandLineArgs.isBackup()) {
                    performBackup(connection, commandLineArgs, maxConnections, maxPacketSize);
                } else {
                    performRestore(connection, commandLineArgs, maxConnections, maxPacketSize);
                }

                // Send sync end message
                connection.sendMessage(new SyncEndMessage());

                // Wait for sync end ack
                response = connection.receiveMessage();
                if (response.getMessageType() != MessageType.SYNC_END_ACK) {
                    System.err.println("[CLIENT] Unexpected response 2: " + response.getMessageType());
                    return;
                }

                SyncEndAckMessage syncEndAck = (SyncEndAckMessage) response;
                if (!syncEndAck.isSuccess()) {
                    System.err.println("[CLIENT] Sync failed: " + syncEndAck.getErrorMessage());
                    return;
                }

                System.out.println("[CLIENT] Sync completed successfully");
            }
        } catch (IOException e) {
            //TODO System.err.println("[CLIENT] Error: " + e.getMessage());
        }
    }

    /**
     * Performs a backup operation.
     *
     * @param connection     The TCP connection
     * @param args           The command line arguments
     * @param maxConnections
     * @param maxPacketSize
     * @throws IOException If an I/O error occurs
     */
    private void performBackup(TcpConnection connection, CommandLineArgs args, int maxConnections, int maxPacketSize) throws IOException {
        System.out.println("[CLIENT] Starting backup from " + args.getSourceFolder() + " to " + args.getTargetFolder());

        // Get list of files to backup
        List<FileInfo> files = new ArrayList<>();
        File sourceDir = new File(args.getSourceFolder());

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            System.err.println("[CLIENT] Source folder does not exist or is not a directory");
            return;
        }

        // Recursively scan the source directory
        scanDirectory(sourceDir, sourceDir.getAbsolutePath(), files);

        System.out.println("[CLIENT] Found " + files.size() + " files to backup");

        // Send file list message
        FileListMessage fileListMessage = new FileListMessage(files, args.isBackup(), 1, 1);
        connection.sendMessage(fileListMessage);

        // Wait for file list response
        Message response = connection.receiveMessage();
        if (response.getMessageType() != MessageType.FILE_LIST_RESPONSE) {
            System.err.println("[CLIENT] Unexpected response 3: " + response.getMessageType());
            return;
        }


        FileListResponseMessage fileListResponse = (FileListResponseMessage) response;
        var mapToTransfer = fileListResponse.getFilesToTransfer()
                .stream().collect(Collectors.toMap(fileInfo -> {
                    return FileUtils.makeUniformPath(fileInfo.getRelativePath());
                }, fileInfo -> fileInfo));

        // Prepare list of files to transfer
        List<FileInfo> filesToTransfer = files.stream()
                .filter(file -> mapToTransfer.containsKey(FileUtils.makeUniformPath(file.getRelativePath())))
                .collect(Collectors.toList());


        System.out.println("[CLIENT] Transferring " + filesToTransfer.size() + " files with 10 parallel connections");

        // Use a fixed pool of 10 threads for parallel file transfers
        ExecutorService executorService = new ThreadPoolExecutor(maxConnections, maxConnections,
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<>());
        CountDownLatch completionLatch = new CountDownLatch(filesToTransfer.size());
        ConcurrentLinkedQueue<TcpConnection> connections = new ConcurrentLinkedQueue<>();// Start from 1 as main connection is 0
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
                    System.out.println("[CLIENT] transferring file " + file.getRelativePath());
                    //semaphore.acquire();
                    currentConnection = connections.poll();
                    transferFile(file, args, currentConnection);
                } catch (Exception e) {
                    System.err.println("[CLIENT] Error transferring file " + file.getRelativePath() + ": " + e.getMessage());
                } finally {
                    if (currentConnection != null) connections.add(currentConnection);
                    //semaphore.release();
                    completionLatch.countDown();
                }
            });
        }

        // Wait for all transfers to complete
        try {
            completionLatch.await();
            System.out.println("[CLIENT] All file transfers completed 2");
            connection.close();
        } catch (InterruptedException e) {
            System.err.println("[CLIENT] File transfer interrupted 2: " + e.getMessage());
        } finally {
            executorService.shutdown();
        }
    }

}
