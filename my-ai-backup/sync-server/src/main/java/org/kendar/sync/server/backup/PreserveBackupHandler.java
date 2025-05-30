package org.kendar.sync.server.backup;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.utils.FileUtils;
import org.kendar.sync.server.server.ClientSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Override
    protected String getHandlerType() {
        return "PRESERVE";
    }

    @Override
    protected Path getSourceFilePath(ClientSession session, FileInfo fileInfo) {
        return Path.of(session.getFolder().getRealPath(), fileInfo.getRelativePath());
    }

    private static final Logger log = LoggerFactory.getLogger(PreserveBackupHandler.class);
    @Override
    public void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        log.debug("[PRESERVE] Received FILE_LIST message");

        var filesOnClient = message.getFiles().stream().collect(Collectors.toMap(
                FileInfo::getRelativePath,
                value -> value
        ));

        var allFiles = listAllFiles(Path.of(session.getFolder().getRealPath()));

        for (var file : allFiles) {
            var fts = FileUtils.makeUniformPath(file.toString().replace(session.getFolder().getRealPath(), ""));
            var filePath = session.getFolder().getRealPath() + "/" + fts;
            BasicFileAttributes attr = Files.readAttributes(Path.of(filePath), BasicFileAttributes.class);

            if (message.isBackup() && !shouldUpdate(filesOnClient.get(fts), file, attr)) {
                filesOnClient.remove(fts);
            } else if (!message.isBackup()) {
                if (filesOnClient.get(fts) == null) {
                    var fi = FileInfo.fromFile(file.toFile(), session.getFolder().getRealPath());
                    filesOnClient.put(fi.getRelativePath(), fi);
                } else if (!shouldUpdate(filesOnClient.get(fts), file, attr)) {
                    filesOnClient.remove(fts);
                }
            }
        }

        var filesToSend = filesOnClient.values().stream().filter(f -> !f.isDirectory()).toList();
        connection.sendMessage(new FileListResponseMessage(filesToSend, new ArrayList<>(), true, 1, 1));

        if (message.isBackup()) {
            return;
        }

        handleFileRestore(connection, session, filesToSend);
    }

    @Override
    public void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        int connectionId = connection.getConnectionId();
        log.debug("[PRESERVE] Received FILE_DESCRIPTOR message: " + message.getFileInfo().getRelativePath() +
                " on connection " + connectionId);

        if (session.isDryRun()) {
            log.debug("[PRESERVE] Dry run: Would create file " + message.getFileInfo().getRelativePath());
            connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
            return;
        }

        connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
    }

    @Override
    public void handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException {
        if (session.isDryRun()) {
            return;
        }

        int connectionId = connection.getConnectionId();
        FileInfo fileInfo = null;

        if (session.isBackup()) {
            fileInfo = session.getCurrentFile(connectionId);
            if (fileInfo == null) {
                log.error("[PRESERVE] No file info found for connection " + connectionId);
                return;
            }
            log.debug("[PRESERVE] Received FILE_DATA message for " + fileInfo.getRelativePath() +
                    " on connection " + connectionId +
                    " (block " + (message.getBlockNumber() + 1) + " of " + message.getTotalBlocks() +
                    ", " + message.getData().length + " bytes)");
        } else {
            log.debug("[PRESERVE] Received FILE_DATA message for " + message.getRelativePath() +
                    " on connection " + connectionId +
                    " (block " + (message.getBlockNumber() + 1) + " of " + message.getTotalBlocks() +
                    ", " + message.getData().length + " bytes)");
        }

        String relativePath = session.isBackup() ? fileInfo.getRelativePath() : message.getRelativePath();
        File file = new File(session.getFolder().getRealPath(), relativePath);
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file, !message.isFirstBlock())) {
            fos.write(message.getData());
        }
        message.isLastBlock();
    }

    @Override
    public void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException {
        int connectionId = connection.getConnectionId();
        FileInfo fileInfo;

        if (session.isBackup()) {
            fileInfo = message.getFileInfo() != null ? message.getFileInfo() : session.getCurrentFile(connectionId);
            if (fileInfo == null) {
                log.error("[PRESERVE] No file info found for connection " + connectionId);
                connection.sendMessage(FileEndAckMessage.failure(message.getRelativePath(), "No file info found"));
                return;
            }
            log.debug("[PRESERVE] Received FILE_END message for " + fileInfo.getRelativePath() +
                    " on connection " + connectionId);
        } else {
            fileInfo = message.getFileInfo();
            log.debug("[PRESERVE] Received FILE_END message for " + message.getRelativePath() +
                    " on connection " + connectionId);
        }

        var realPath = Path.of(session.getFolder().getRealPath() + File.separator + fileInfo.getRelativePath());
        Files.setAttribute(realPath, "creationTime", FileTime.fromMillis(fileInfo.getCreationTime().toEpochMilli()));
        Files.setLastModifiedTime(realPath, FileTime.fromMillis(fileInfo.getModificationTime().toEpochMilli()));

        connection.sendMessage(FileEndAckMessage.success(fileInfo.getRelativePath()));
    }

    @Override
    public void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException {
        log.debug("[PRESERVE] Received SYNC_END message");
        connection.sendMessage(new SyncEndAckMessage(true, "Sync completed"));
    }
}