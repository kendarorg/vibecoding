package org.kendar.sync.server.backup;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.utils.Attributes;
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
@SuppressWarnings("DuplicatedCode")
public class PreserveBackupHandler extends BackupHandler {

    private static final Logger log = LoggerFactory.getLogger(PreserveBackupHandler.class);

    @Override
    protected Path getSourceFilePath(ClientSession session, FileInfo fileInfo) {
        return Path.of(session.getFolder().getRealPath(), fileInfo.getRelativePath());
    }

    @Override
    public void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        log.debug("[SERVER] Received FILE_LIST message");

        var filesOnClient = message.getFiles().stream().collect(Collectors.toMap(
                FileInfo::getRelativePath,
                value -> value
        ));

        var allFiles = listAllFiles(Path.of(session.getFolder().getRealPath()));

        for (var file : allFiles) {
            var fts = FileUtils.makeUniformPath(file.toString()).replace(FileUtils.makeUniformPath(session.getFolder().getRealPath()), "");
            var filePath = session.getFolder().getRealPath() + "/" + fts;

            var attr = FileUtils.readFileAttributes(Path.of(filePath));

            if (shouldIgnoreFileByAttrAndPattern(session, file, attr)) continue;

            if (message.isBackup() && shouldUpdate(filesOnClient.get(fts), file, attr)) {
                filesOnClient.remove(fts);
            } else if (!message.isBackup()) {
                if (filesOnClient.get(fts) == null) {
                    var fi = FileInfo.fromFile(file.toFile(), session.getFolder().getRealPath());
                    filesOnClient.put(fi.getRelativePath(), fi);
                } else if (shouldUpdate(filesOnClient.get(fts), file, attr)) {
                    filesOnClient.remove(fts);
                }
            }
        }

        var filesToSend = filesOnClient.values().stream().filter(f ->
                !Attributes.isDirectory(f.getExtendedUmask())).collect(Collectors.toList());
        connection.sendMessage(new FileListResponseMessage(filesToSend, new ArrayList<>(), true, 1, 1));

        if (message.isBackup()) {
            return;
        }

        handleFileRestore(connection, session, filesToSend);
    }


    @Override
    public void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        int connectionId = connection.getConnectionId();
        log.debug("[SERVER] Received FILE_DESCRIPTOR message: {} on connection {}", message.getFileInfo().getRelativePath(), connectionId);

        if (session.isDryRun()) {
            log.debug("[SERVER] Dry run: Would create file {}", message.getFileInfo().getRelativePath());
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
                log.error("[SERVER] 1 No file info found for connection {}", connectionId);
                return;
            }
            log.debug("[SERVER] Received FILE_DATA message for {} on connection {} (block {} of {}, {} bytes)", fileInfo.getRelativePath(), connectionId, message.getBlockNumber() + 1, message.getTotalBlocks(), message.getData().length);
        } else {
            log.debug("[SERVER] Received FILE_DATA message for {} on connection {} (block {} of {}, {} bytes)", message.getRelativePath(), connectionId, message.getBlockNumber() + 1, message.getTotalBlocks(), message.getData().length);
        }

        String relativePath;
        if (session.isBackup()) {
            if (fileInfo == null || fileInfo.getRelativePath() == null) {
                throw new RuntimeException("No file info found for connection 1 " + connectionId);
            }
            relativePath = fileInfo.getRelativePath();
        } else {
            if (message.getRelativePath() == null) {
                throw new RuntimeException("No file info found for connection 2 " + connectionId);
            }
            relativePath = message.getRelativePath();
        }
        File file = new File(session.getFolder().getRealPath(), relativePath);
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        try (FileOutputStream fos = new FileOutputStream(file, message.isFirstBlock())) {
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
                log.error("[SERVER] 2 No file info found for connection {}", connectionId);
                connection.sendMessage(FileEndAckMessage.failure(message.getRelativePath(), "No file info found"));
                return;
            }
            log.debug("[SERVER] 3 Received FILE_END message for {} on connection {}", fileInfo.getRelativePath(), connectionId);
        } else {
            fileInfo = message.getFileInfo();
            log.debug("[SERVER] 4 Received FILE_END message for {} on connection {}", message.getRelativePath(), connectionId);
        }

        var realPath = Path.of(session.getFolder().getRealPath() + File.separator + fileInfo.getRelativePath());
        var attr = Files.readAttributes(realPath, BasicFileAttributes.class);
        FileUtils.writeFileAttributes(realPath,fileInfo.getExtendedUmask(),attr);
        FileUtils.setFileTimes(realPath.toFile(),fileInfo.getCreationTime(), fileInfo.getModificationTime());
        connection.sendMessage(FileEndAckMessage.success(fileInfo.getRelativePath()));
    }
}