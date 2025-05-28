package org.kendar.sync.client;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Main class for the sync client application.
 */
public class SyncClientApp {
    private static final int DEFAULT_PORT = 8080;
    private static final int DEFAULT_MAX_PACKET_SIZE = 1024 * 1024; // 1 MB
    private static final int DEFAULT_MAX_CONNECTIONS = 5;

    /**
     * Main method to start the application.
     *
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        // Parse command line arguments
        CommandLineArgs commandLineArgs = parseCommandLineArgs(args);

        if (commandLineArgs.isHelp()) {
            printHelp();
            return;
        }

        // Validate arguments
        if (!validateArgs(commandLineArgs)) {
            return;
        }

        try {
            // Connect to server
            System.out.println("Connecting to server " + commandLineArgs.getServerAddress() + ":" + commandLineArgs.getServerPort());

            if (commandLineArgs.isDryRun()) {
                System.out.println("Running in dry-run mode. No actual file operations will be performed.");
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
                    System.err.println("Unexpected response: " + response.getMessageType());
                    return;
                }

                ConnectResponseMessage connectResponse = (ConnectResponseMessage) response;
                if (!connectResponse.isAccepted()) {
                    System.err.println("Connection rejected: " + connectResponse.getErrorMessage());
                    return;
                }

                System.out.println("Connected to server");

                // Perform backup or restore
                if (commandLineArgs.isBackup()) {
                    performBackup(connection, commandLineArgs);
                } else {
                    performRestore(connection, commandLineArgs);
                }

                // Send sync end message
                connection.sendMessage(new SyncEndMessage());

                // Wait for sync end ack
                response = connection.receiveMessage();
                if (response.getMessageType() != MessageType.SYNC_END_ACK) {
                    System.err.println("Unexpected response: " + response.getMessageType());
                    return;
                }

                SyncEndAckMessage syncEndAck = (SyncEndAckMessage) response;
                if (!syncEndAck.isSuccess()) {
                    System.err.println("Sync failed: " + syncEndAck.getErrorMessage());
                    return;
                }

                System.out.println("Sync completed successfully");
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    /**
     * Performs a backup operation.
     *
     * @param connection The TCP connection
     * @param args The command line arguments
     * @throws IOException If an I/O error occurs
     */
    private static void performBackup(TcpConnection connection, CommandLineArgs args) throws IOException {
        System.out.println("Starting backup from " + args.getSourceFolder() + " to " + args.getTargetFolder());

        // Get list of files to backup
        List<FileInfo> files = new ArrayList<>();
        File sourceDir = new File(args.getSourceFolder());

        if (!sourceDir.exists() || !sourceDir.isDirectory()) {
            System.err.println("Source folder does not exist or is not a directory");
            return;
        }

        // Recursively scan the source directory
        scanDirectory(sourceDir, sourceDir.getAbsolutePath(), files);

        System.out.println("Found " + files.size() + " files to backup");

        // Send file list message
        FileListMessage fileListMessage = new FileListMessage(files, args.isBackup(), 1, 1);
        connection.sendMessage(fileListMessage);

        // Wait for file list response
        Message response = connection.receiveMessage();
        if (response.getMessageType() != MessageType.FILE_LIST_RESPONSE) {
            System.err.println("Unexpected response: " + response.getMessageType());
            return;
        }

        FileListResponseMessage fileListResponse = (FileListResponseMessage) response;
        var mapToTransfer = fileListResponse.getFilesToTransfer()
                .stream().collect(Collectors.toMap(fileInfo -> {
                    return fileInfo.getRelativePath();
                }, fileInfo -> fileInfo));

        // Process files to transfer
        for (FileInfo file : files) {
            // Send file descriptor
            if(!mapToTransfer.containsKey(file.getRelativePath())) {
                continue;
            }
            FileDescriptorMessage fileDescriptorMessage = new FileDescriptorMessage(file);
            connection.sendMessage(fileDescriptorMessage);

            // Wait for file descriptor ack
            response = connection.receiveMessage();
            if (response.getMessageType() != MessageType.FILE_DESCRIPTOR_ACK) {
                System.err.println("Unexpected response: " + response.getMessageType());
                return;
            }

            FileDescriptorAckMessage fileDescriptorAck = (FileDescriptorAckMessage) response;
            if (!fileDescriptorAck.isReady()) {
                System.err.println("Server not ready to receive file: " + fileDescriptorAck.getErrorMessage());
                continue;
            }

            // If it's a directory, no need to send data
            if (file.isDirectory()) {
                System.out.println("Created directory: " + file.getRelativePath());
                continue;
            }

            // Send file data
            if (!args.isDryRun()) {
                File sourceFile = new File(file.getPath());
                byte[] fileData = FileUtils.readFile(sourceFile);

                FileDataMessage fileDataMessage = new FileDataMessage(file.getRelativePath(), 0, 1, fileData);
                connection.sendMessage(fileDataMessage);
            } else {
                System.out.println("Dry run: Would send file data for " + file.getRelativePath());
            }

            // Send file end
            FileEndMessage fileEndMessage = new FileEndMessage(file.getRelativePath(), file);
            connection.sendMessage(fileEndMessage);

            // Wait for file end ack
            response = connection.receiveMessage();
            if (response.getMessageType() != MessageType.FILE_END_ACK) {
                System.err.println("Unexpected response: " + response.getMessageType());
                return;
            }

            FileEndAckMessage fileEndAck = (FileEndAckMessage) response;
            if (!fileEndAck.isSuccess()) {
                System.err.println("File transfer failed: " + fileEndAck.getErrorMessage());
                continue;
            }

            System.out.println("Transferred file: " + file.getRelativePath());
        }
    }

    /**
     * Performs a restore operation.
     *
     * @param connection The TCP connection
     * @param args The command line arguments
     * @throws IOException If an I/O error occurs
     */
    private static void performRestore(TcpConnection connection, CommandLineArgs args) throws IOException {
        System.out.println("Starting restore from " + args.getTargetFolder() + " to " + args.getSourceFolder());

        // Send file list message
        FileListMessage fileListMessage = new FileListMessage(new ArrayList<>(), args.isBackup(), 1, 1);
        connection.sendMessage(fileListMessage);

        // Wait for file list response
        Message response = connection.receiveMessage();
        if (response.getMessageType() != MessageType.FILE_LIST_RESPONSE) {
            System.err.println("Unexpected response: " + response.getMessageType());
            return;
        }

        FileListResponseMessage fileListResponse = (FileListResponseMessage) response;

        // Process files to transfer
        for (FileInfo file : fileListResponse.getFilesToTransfer()) {
            // Wait for file descriptor
            Message message = connection.receiveMessage();
            if (message.getMessageType() != MessageType.FILE_DESCRIPTOR) {
                System.err.println("Unexpected message: " + message.getMessageType());
                return;
            }

            FileDescriptorMessage fileDescriptorMessage = (FileDescriptorMessage) message;
            FileInfo fileInfo = fileDescriptorMessage.getFileInfo();

            System.out.println("Receiving file: " + fileInfo.getRelativePath());

            // Create the file or directory
            File targetFile = new File(args.getSourceFolder(), fileInfo.getRelativePath());

            if (fileInfo.isDirectory()) {
                if (!args.isDryRun()) {
                    targetFile.mkdirs();
                } else {
                    System.out.println("Dry run: Would create directory " + targetFile.getAbsolutePath());
                }

                // Send file descriptor ack
                FileDescriptorAckMessage fileDescriptorAck = FileDescriptorAckMessage.ready(fileInfo.getRelativePath());
                connection.sendMessage(fileDescriptorAck);

                continue;
            }

            // Create parent directories
            if (!args.isDryRun()) {
                targetFile.getParentFile().mkdirs();
            } else {
                System.out.println("Dry run: Would create parent directories for " + targetFile.getAbsolutePath());
            }

            // Send file descriptor ack
            FileDescriptorAckMessage fileDescriptorAck = FileDescriptorAckMessage.ready(fileInfo.getRelativePath());
            connection.sendMessage(fileDescriptorAck);

            // Wait for file data
            message = connection.receiveMessage();
            if (message.getMessageType() != MessageType.FILE_DATA) {
                System.err.println("Unexpected message: " + message.getMessageType());
                return;
            }

            FileDataMessage fileDataMessage = (FileDataMessage) message;

            // Write file data
            if (!args.isDryRun()) {
                FileUtils.writeFile(targetFile, fileDataMessage.getData());
            } else {
                System.out.println("Dry run: Would write file data to " + targetFile.getAbsolutePath());
            }

            // Wait for file end
            message = connection.receiveMessage();
            if (message.getMessageType() != MessageType.FILE_END) {
                System.err.println("Unexpected message: " + message.getMessageType());
                return;
            }

            // Send file end ack
            FileEndAckMessage fileEndAck = FileEndAckMessage.success(fileInfo.getRelativePath());
            connection.sendMessage(fileEndAck);

            System.out.println("Received file: " + fileInfo.getRelativePath());
        }

        // Process files to delete
        for (String relativePath : fileListResponse.getFilesToDelete()) {
            File fileToDelete = new File(args.getSourceFolder(), relativePath);

            if (!args.isDryRun()) {
                if (fileToDelete.exists()) {
                    fileToDelete.delete();
                }
            } else {
                System.out.println("Dry run: Would delete file " + fileToDelete.getAbsolutePath());
            }

            System.out.println("Deleted file: " + relativePath);
        }
    }

    /**
     * Recursively scans a directory and adds all files to the list.
     *
     * @param directory The directory to scan
     * @param basePath The base path for calculating relative paths
     * @param files The list to add files to
     * @throws IOException If an I/O error occurs
     */
    private static void scanDirectory(File directory, String basePath, List<FileInfo> files) throws IOException {
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
     * Parses command line arguments.
     *
     * @param args The command line arguments
     * @return The parsed arguments
     */
    private static CommandLineArgs parseCommandLineArgs(String[] args) {
        CommandLineArgs commandLineArgs = new CommandLineArgs();

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            switch (arg) {
                case "--help":
                case "-h":
                    commandLineArgs.setHelp(true);
                    break;
                case "--source":
                case "-s":
                    if (i + 1 < args.length) {
                        commandLineArgs.setSourceFolder(args[++i]);
                    }
                    break;
                case "--target":
                case "-t":
                    if (i + 1 < args.length) {
                        commandLineArgs.setTargetFolder(args[++i]);
                    }
                    break;
                case "--backup":
                case "-b":
                    commandLineArgs.setBackup(true);
                    break;
                case "--restore":
                case "-r":
                    commandLineArgs.setBackup(false);
                    break;
                case "--server":
                    if (i + 1 < args.length) {
                        commandLineArgs.setServerAddress(args[++i]);
                    }
                    break;
                case "--port":
                case "-p":
                    if (i + 1 < args.length) {
                        try {
                            commandLineArgs.setServerPort(Integer.parseInt(args[++i]));
                        } catch (NumberFormatException e) {
                            System.err.println("Invalid port number: " + args[i]);
                        }
                    }
                    break;
                case "--username":
                case "-u":
                    if (i + 1 < args.length) {
                        commandLineArgs.setUsername(args[++i]);
                    }
                    break;
                case "--password":
                case "-pw":
                    if (i + 1 < args.length) {
                        commandLineArgs.setPassword(args[++i]);
                    }
                    break;
                case "--dry-run":
                case "-d":
                    commandLineArgs.setDryRun(true);
                    break;
                case "--type":
                    if (i + 1 < args.length) {
                        String typeArg = args[++i].toUpperCase();
                        try {
                            commandLineArgs.setBackupType(BackupType.valueOf(typeArg));
                        } catch (IllegalArgumentException e) {
                            System.err.println("Invalid backup type: " + typeArg);
                            System.err.println("Valid types are: PRESERVE, MIRROR, DATE_SEPARATED");
                        }
                    }
                    break;
            }
        }

        return commandLineArgs;
    }

    /**
     * Validates command line arguments.
     *
     * @param args The command line arguments
     * @return Whether the arguments are valid
     */
    private static boolean validateArgs(CommandLineArgs args) {
        boolean valid = true;

        if (args.getSourceFolder() == null) {
            System.err.println("Source folder is required (--source)");
            valid = false;
        }else if(!Files.exists(Path.of(args.getSourceFolder()))) {
            System.err.println("Source folder does not exists (--source)");
            valid = false;
        }

        if (args.getTargetFolder() == null) {
            System.err.println("Target folder is required (--target)");
            valid = false;
        }

        if (args.getServerAddress() == null) {
            System.err.println("Server address is required (--server)");
            valid = false;
        }

        if (args.getUsername() == null) {
            System.err.println("Username is required (--username)");
            valid = false;
        }

        if (args.getPassword() == null) {
            System.err.println("Password is required (--password)");
            valid = false;
        }

        if (!valid) {
            System.err.println("\nUse --help for usage information");
        }

        return valid;
    }

    /**
     * Prints help information.
     */
    private static void printHelp() {
        System.out.println("Usage: java -jar sync-client.jar [options]");
        System.out.println("Options:");
        System.out.println("  --help, -h                  Show this help message");
        System.out.println("  --source, -s <folder>       Source folder");
        System.out.println("  --target, -t <folder>       Target folder (virtual folder on server)");
        System.out.println("  --backup, -b                Perform backup (default)");
        System.out.println("  --restore, -r               Perform restore");
        System.out.println("  --server <address>          Server address");
        System.out.println("  --port, -p <port>           Server port (default: 8080)");
        System.out.println("  --username, -u <username>   Username for authentication");
        System.out.println("  --password, -pw <password>  Password for authentication");
        System.out.println("  --dry-run, -d               Perform a dry run (no actual file operations)");
        System.out.println("  --type <type>               Backup type: PRESERVE, MIRROR, DATE_SEPARATED (default: PRESERVE)");
    }

    /**
     * Class to hold command line arguments.
     */
    public static class CommandLineArgs {
        private String sourceFolder;
        private String targetFolder;
        private boolean backup = true;
        private String serverAddress;
        private int serverPort = DEFAULT_PORT;
        private String username;
        private String password;
        private boolean dryRun = false;
        private BackupType backupType = BackupType.PRESERVE;
        private boolean help = false;

        public String getSourceFolder() {
            return sourceFolder;
        }

        public void setSourceFolder(String sourceFolder) {
            this.sourceFolder = sourceFolder;
        }

        public String getTargetFolder() {
            return targetFolder;
        }

        public void setTargetFolder(String targetFolder) {
            this.targetFolder = targetFolder;
        }

        public boolean isBackup() {
            return backup;
        }

        public void setBackup(boolean backup) {
            this.backup = backup;
        }

        public String getServerAddress() {
            return serverAddress;
        }

        public void setServerAddress(String serverAddress) {
            this.serverAddress = serverAddress;
        }

        public int getServerPort() {
            return serverPort;
        }

        public void setServerPort(int serverPort) {
            this.serverPort = serverPort;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isDryRun() {
            return dryRun;
        }

        public void setDryRun(boolean dryRun) {
            this.dryRun = dryRun;
        }

        public BackupType getBackupType() {
            return backupType;
        }

        public void setBackupType(BackupType backupType) {
            this.backupType = backupType;
        }

        public boolean isHelp() {
            return help;
        }

        public void setHelp(boolean help) {
            this.help = help;
        }
    }
}
