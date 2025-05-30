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
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Handles backup operations for the DATE_SEPARATED backup type.
 * Files on the target that don't exist on the source are preserved.
 * Files are organized in directories based on their modification date.
 */
public class DateSeparatedBackupHandler extends BackupHandler {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private final ConcurrentHashMap<String, FileInfo> filesOnClient = new ConcurrentHashMap<>();

    @Override
    protected String getHandlerType() {
        return "DATE_SEPARATED";
    }

    @Override
    protected Path getSourceFilePath(ClientSession session, FileInfo fileInfo) {
        String date = new SimpleDateFormat("yyyy-MM-dd")
                .format(new java.util.Date(fileInfo.getCreationTime().toEpochMilli()));
        var relPath = Path.of(session.getFolder().getRealPath(), date, fileInfo.getRelativePath());
        if (!Files.exists(relPath)) {
            relPath = Path.of(session.getFolder().getRealPath(), fileInfo.getRelativePath());
        }
        return relPath;
    }

    private static final Logger log = LoggerFactory.getLogger(DateSeparatedBackupHandler.class);
    @Override
    public void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        log.debug("[DATE_SEPARATED] Received FILE_LIST message");

        var filesOnClient = message.getFiles().stream().collect(Collectors.toMap(
                FileInfo::getRelativePath,
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
                var newFts = fts.substring(11); // Remove the date prefix
                if (!shouldUpdate(filesOnClient.get(newFts), file, attr)) {
                    filesOnClient.remove(fts);
                }
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
        connection.sendMessage(new FileListResponseMessage(filesToSend, new ArrayList<>(), true, 1, 1));

        if (message.isBackup()) {
            return;
        }

        handleFileRestore(connection, session, filesToSend);
    }

    @Override
    public void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        int connectionId = connection.getConnectionId();
        log.debug("[DATE_SEPARATED] Received FILE_DESCRIPTOR message: " + message.getFileInfo().getRelativePath() +
                " on connection " + connectionId);

        if (session.isDryRun()) {
            log.debug("Dry run: Would create file " + message.getFileInfo().getRelativePath());
            connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
            return;
        }

        FileInfo fileInfo = message.getFileInfo();
        filesOnClient.put(fileInfo.getRelativePath(), fileInfo);

        connection.sendMessage(FileDescriptorAckMessage.ready(fileInfo.getRelativePath()));
    }

    public ConcurrentHashMap<String, FileInfo> getFilesOnClient() {
        return filesOnClient;
    }

    @Override
    public void handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException {
        if (session.isDryRun()) {
            return;
        }

        log.debug("[DATE_SEPARATED] Received FILE_DATA message");
        var fileInfo = filesOnClient.get(message.getRelativePath());
        String dateDir = new java.text.SimpleDateFormat("yyyy-MM-dd").format(
                new java.util.Date(fileInfo.getCreationTime().toEpochMilli()));

        String relativePath = message.getRelativePath();
        File targetFile = new File(new File(session.getFolder().getRealPath(), dateDir), relativePath);

        targetFile.getParentFile().mkdirs();

        try (FileOutputStream fos = new FileOutputStream(targetFile, !message.isFirstBlock())) {
            fos.write(message.getData());
        }
        message.isLastBlock();
    }

    @Override
    public void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException {
        log.debug("[DATE_SEPARATED] Received FILE_END message");

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
        log.debug("[DATE_SEPARATED] Received SYNC_END message");
        connection.sendMessage(new SyncEndAckMessage(true, "Sync completed"));
    }
}