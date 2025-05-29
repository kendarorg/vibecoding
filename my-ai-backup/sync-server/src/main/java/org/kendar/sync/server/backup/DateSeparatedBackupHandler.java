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
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
            var fts = FileUtils.makeUniformPath(file.toString().replace(session.getFolder().getRealPath(), ""));
            var filePath = session.getFolder().getRealPath()+File.separator+fts;
            BasicFileAttributes attr = Files.readAttributes(Path.of(filePath), BasicFileAttributes.class);
            if(!fts.matches(".*\\d{4}-\\d{2}-\\d{2}.*")){
                if(!shouldUpdate(filesOnClient.get(fts), file,attr)){
                    filesOnClient.remove(fts);
                }
                if(!message.isBackup()){
                    if(filesOnClient.get(fts)==null){
                        filesOnClient.put(fts,FileInfo.fromFile(file.toFile(),session.getFolder().getRealPath()));
                    }
                }
            }else{
                var newFts = fts.substring(11);
                if(!shouldUpdate(filesOnClient.get(newFts), file,attr)){
                    filesOnClient.remove(fts);
                }// Remove the date prefix
                if(!message.isBackup()){
                    if(filesOnClient.get(newFts)==null){
                        var fi = FileInfo.fromFile(file.toFile(),session.getFolder().getRealPath());
                        fi.setRelativePath(newFts);
                        filesOnClient.put(newFts,fi);
                    }
                }
            }
        }
        var filesToSend = filesOnClient.values().stream().filter(f->!f.isDirectory()).toList();
        // For DATE_SEPARATED backup type, we don't need to compare files or delete anything
        // Just acknowledge the message with an empty list
        connection.sendMessage(new FileListResponseMessage(filesToSend, new ArrayList<>(), true, 1, 1));
        if(message.isBackup()){
            return;
        }

        for (var file : filesToSend) {
            if(file.isDirectory()){
                continue;
            }
            FileDescriptorMessage fileDescriptorMessage = new FileDescriptorMessage(file);
            connection.sendMessage(fileDescriptorMessage);

            var response = connection.receiveMessage();
            if (response.getMessageType() != MessageType.FILE_DESCRIPTOR_ACK) {
                System.err.println("[SERVER] Unexpected response 4: " + response.getMessageType());
                return;
            }

            FileDescriptorAckMessage fileDescriptorAck = (FileDescriptorAckMessage) response;
            if (!fileDescriptorAck.isReady()) {
                System.err.println("[SERVER] Server not ready to receive file: " + fileDescriptorAck.getErrorMessage());
                continue;
            }
            if (file.isDirectory()) {
                System.out.println("[SERVER] Created directory: " + file.getRelativePath());
                continue;
            }
            if (!session.isDryRun()) {
                String date = new SimpleDateFormat("yyyy-MM-dd").
                        format(new java.util.Date (file.getCreationTime().toEpochMilli()));
                var relPath = Path.of(session.getFolder().getRealPath(), date,file.getRelativePath());
                if(!Files.exists(relPath)){
                    relPath = Path.of(session.getFolder().getRealPath(), file.getRelativePath());
                }

                File sourceFile =relPath.toFile();
                byte[] fileData = FileUtils.readFile(sourceFile);

                FileDataMessage fileDataMessage = new FileDataMessage(file.getRelativePath(), 0, 1, fileData);
                connection.sendMessage(fileDataMessage);
            } else {
                System.out.println("[SERVER] Dry run: Would send file data for " + file.getRelativePath());
            }

            FileEndMessage fileEndMessage = new FileEndMessage(file.getRelativePath(), file);
            connection.sendMessage(fileEndMessage);

            // Wait for file end ack
            response = connection.receiveMessage();
            if (response.getMessageType() != MessageType.FILE_END_ACK) {
                System.err.println("[SERVER] Unexpected response 5: " + response.getMessageType());
                return;
            }

            FileEndAckMessage fileEndAck = (FileEndAckMessage) response;
            if (!fileEndAck.isSuccess()) {
                System.err.println("[SERVER] File transfer failed: " + fileEndAck.getErrorMessage());
                continue;
            }

            System.out.println("[SERVER] Transferred file: " + file.getRelativePath());
        }
    }

    private ConcurrentHashMap<String,FileInfo> filesOnClient = new ConcurrentHashMap<>();

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
                new java.util.Date (fileInfo.getCreationTime().toEpochMilli()));

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
                new java.util.Date (fileInfo.getCreationTime().toEpochMilli()));
        var realPath = Path.of(session.getFolder().getRealPath()+File.separator+dateDir+File.separator+fileInfo.getRelativePath());
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
