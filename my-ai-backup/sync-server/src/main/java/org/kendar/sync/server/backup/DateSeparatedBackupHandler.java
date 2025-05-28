package org.kendar.sync.server.backup;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.server.server.ClientSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles backup operations for the DATE_SEPARATED backup type.
 * Files on the target that don't exist on the source are preserved.
 * Files are organized in directories based on their modification date.
 */
public class DateSeparatedBackupHandler extends BackupHandler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public DateSeparatedBackupHandler() {
    }

    /**
     * Lists all directories under a source path that match a date pattern (YYYY-MM-DD).
     *
     * @param sourcePath The source directory path to search in
     * @return List of directories matching the date pattern
     * @throws IOException If an I/O error occurs
     */
    private List<Path> listDateDirectories(Path sourcePath) throws IOException {
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            return new ArrayList<>();
        }

        return Files.list(sourcePath)
                .filter(Files::isDirectory)
                .filter(p -> p.getFileName().toString().matches("\\d{4}-\\d{2}-\\d{2}"))
                .collect(Collectors.toList());
    }

    @Override
    public void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        System.out.println("[DATE_SEPARATED] Received FILE_LIST message");

        var filesOnClient = message.getFiles().stream().collect(Collectors.toMap(
                key-> key.getRelativePath(),
                value -> value
        ));

        var allFiles = listAllFiles(Path.of(session.getFolder().getRealPath()));
        for(var file:allFiles){
            var fts = file.toString().replace(session.getFolder().getRealPath(), "");
            var filePath = session.getFolder().getRealPath()+fts;
            BasicFileAttributes attr = Files.readAttributes(Path.of(filePath), BasicFileAttributes.class);
            if(!fts.matches("\\d{4}-\\d{2}-\\d{2}")){
                if(!shouldUpdate(filesOnClient.get(fts), file,attr)){
                    filesOnClient.remove(fts);
                }
            }else{
                var newFts = fts.substring(11);
                if(!shouldUpdate(filesOnClient.get(newFts), file,attr)){
                    filesOnClient.remove(fts);
                }// Remove the date prefix
            }
        }
        var filesToSend = filesOnClient.values().stream().toList();
        // For DATE_SEPARATED backup type, we don't need to compare files or delete anything
        // Just acknowledge the message with an empty list
        connection.sendMessage(new FileListResponseMessage(filesToSend, new ArrayList<>(), true, 1, 1));
    }

    @Override
    public void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        System.out.println("[DATE_SEPARATED] Received FILE_DESCRIPTOR message: " + message.getFileInfo().getRelativePath());

        // If this is a dry run, just acknowledge the message
        if (session.isDryRun()) {
            System.out.println("Dry run: Would create file " + message.getFileInfo().getRelativePath());
            connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
            return;
        }

        // For DATE_SEPARATED backup type, we need to create a directory structure based on the file's modification date
        FileInfo fileInfo = message.getFileInfo();

        if (fileInfo.isDirectory()) {
            // For directories, just create them as is
            File dir = new File(session.getFolder().getRealPath(), fileInfo.getRelativePath());
            dir.mkdirs();
        } else {
            // For files, create a date-based directory structure
            LocalDate modificationDate = fileInfo.getCreationTime()
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate();

            String dateDir = modificationDate.format(DATE_FORMATTER);

            // Create the date directory
            File dateDirFile = new File(session.getFolder().getRealPath(), dateDir);
            dateDirFile.mkdirs();

            // Create parent directories for the file if needed
            String relativePath = fileInfo.getRelativePath();
            String parentPath = new File(relativePath).getParent();
            if (parentPath != null) {
                File parentDir = new File(dateDirFile, parentPath);
                parentDir.mkdirs();
            }
        }

        connection.sendMessage(FileDescriptorAckMessage.ready(fileInfo.getRelativePath()));
    }

    @Override
    public void handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException {
        // If this is a dry run, just ignore the data
        if (session.isDryRun()) {
            return;
        }

        System.out.println("[DATE_SEPARATED] Received FILE_DATA message");

        // For DATE_SEPARATED backup type, we need to write the data to a file in the date-based directory structure
        // Get the file's modification date from the file descriptor
        // For simplicity, we'll use the current date if we can't determine the file's modification date
        LocalDate currentDate = LocalDate.now();
        String dateDir = currentDate.format(DATE_FORMATTER);

        // Create the full path for the file
        String relativePath = message.getRelativePath();
        File targetFile = new File(new File(session.getFolder().getRealPath(), dateDir), relativePath);

        // Create parent directories if needed
        targetFile.getParentFile().mkdirs();

        // Write the data to the file
        try (FileOutputStream fos = new FileOutputStream(targetFile, !message.isFirstBlock())) {
            fos.write(message.getData());
        }
    }

    @Override
    public void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException {
        System.out.println("[DATE_SEPARATED] Received FILE_END message");

        connection.sendMessage(FileEndAckMessage.success(message.getRelativePath()));
    }

    @Override
    public void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException {
        System.out.println("[DATE_SEPARATED] Received SYNC_END message");

        connection.sendMessage(new SyncEndAckMessage(true, "Sync completed"));
    }
}
