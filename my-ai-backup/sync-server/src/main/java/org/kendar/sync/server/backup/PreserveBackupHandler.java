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
import java.util.stream.Collectors;

/**
 * Handles backup operations for the PRESERVE backup type.
 * Files on the target that don't exist on the source are preserved.
 */
public class PreserveBackupHandler extends BackupHandler {

    public PreserveBackupHandler() {
    }

    @Override
    public void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        System.out.println("[PRESERVE] Received FILE_LIST message");

        var filesOnClient = message.getFiles().stream().collect(Collectors.toMap(
                key -> key.getRelativePath(),
                value -> value
        ));

        var allFiles = listAllFiles(Path.of(session.getFolder().getRealPath()));

        for (var file : allFiles) {
            var fts = FileUtils.makeUniformPath(file.toString().replace(session.getFolder().getRealPath(), ""));
            var filePath = session.getFolder().getRealPath() +"/"+ fts;
            BasicFileAttributes attr = Files.readAttributes(Path.of(filePath), BasicFileAttributes.class);

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
        var filesToSend = filesOnClient.values().stream().filter(f->!f.isDirectory()).toList();
        connection.sendMessage(new FileListResponseMessage(filesToSend, new ArrayList<>(), true, 1, 1));
        if (message.isBackup()) {
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
                var relPath = Path.of(session.getFolder().getRealPath(), file.getRelativePath());
                File sourceFile = relPath.toFile();
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

    @Override
    public void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        int connectionId = connection.getConnectionId();
        System.out.println("[PRESERVE] Received FILE_DESCRIPTOR message: " + message.getFileInfo().getRelativePath() + 
                         " on connection " + connectionId);

        // If this is a dry run, just acknowledge the message
        if (session.isDryRun()) {
            System.out.println("[PRESERVE] Dry run: Would create file " + message.getFileInfo().getRelativePath());
            connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
            return;
        }

        connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
    }

    @Override
    public void handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException {
        // If this is a dry run, just ignore the data
        if (session.isDryRun()) {
            return;
        }

        int connectionId = connection.getConnectionId();
        FileInfo fileInfo = null;

        // Get the file info from the session if it's a backup operation
        if (session.isBackup()) {
            fileInfo = session.getCurrentFile(connectionId);
            if (fileInfo == null) {
                System.err.println("[PRESERVE] No file info found for connection " + connectionId);
                return;
            }
            System.out.println("[PRESERVE] Received FILE_DATA message for " + fileInfo.getRelativePath() + 
                             " on connection " + connectionId + 
                             " (block " + (message.getBlockNumber() + 1) + " of " + message.getTotalBlocks() + 
                             ", " + message.getData().length + " bytes)");
        } else {
            System.out.println("[PRESERVE] Received FILE_DATA message for " + message.getRelativePath() + 
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
    }

    @Override
    public void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException {
        int connectionId = connection.getConnectionId();
        FileInfo fileInfo = null;

        // Get the file info - either from the message or from the session
        if (session.isBackup()) {
            fileInfo = message.getFileInfo() != null ? message.getFileInfo() : session.getCurrentFile(connectionId);
            if (fileInfo == null) {
                System.err.println("[PRESERVE] No file info found for connection " + connectionId);
                connection.sendMessage(FileEndAckMessage.failure(message.getRelativePath(), "No file info found"));
                return;
            }
            System.out.println("[PRESERVE] Received FILE_END message for " + fileInfo.getRelativePath() + 
                             " on connection " + connectionId);
        } else {
            fileInfo = message.getFileInfo();
            System.out.println("[PRESERVE] Received FILE_END message for " + message.getRelativePath() + 
                             " on connection " + connectionId);
        }

        var realPath = Path.of(session.getFolder().getRealPath() + File.separator + fileInfo.getRelativePath());
        Files.setAttribute(realPath, "creationTime", FileTime.fromMillis(fileInfo.getCreationTime().toEpochMilli()));
        Files.setLastModifiedTime(realPath, FileTime.fromMillis(fileInfo.getModificationTime().toEpochMilli()));

        connection.sendMessage(FileEndAckMessage.success(fileInfo.getRelativePath()));
    }

    @Override
    public void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException {
        System.out.println("[PRESERVE] Received SYNC_END message");

        connection.sendMessage(new SyncEndAckMessage(true, "Sync completed"));
    }
}
