package org.kendar.sync.server.backup;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.twoway.ConflictItem;
import org.kendar.sync.lib.twoway.LogEntry;
import org.kendar.sync.lib.twoway.StatusAnalyzer;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class SyncBackupHandler extends BackupHandler {
    private static final Logger log = LoggerFactory.getLogger(SyncBackupHandler.class);
    private StatusAnalyzer statusAnalyzer;


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

        log.debug("[SERVER] Received FILE_DATA message");
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
        log.debug("[SERVER] Received FILE_END message");

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
        Files.setAttribute(realPath, "creationTime", FileTime.fromMillis(fileInfo.getCreationTime().toEpochMilli()));
        Files.setLastModifiedTime(realPath, FileTime.fromMillis(fileInfo.getModificationTime().toEpochMilli()));

        connection.sendMessage(FileEndAckMessage.success(fileInfo.getRelativePath()));
    }

    @Override
    protected Path getSourceFilePath(ClientSession session, FileInfo fileInfo) {
        return Path.of(session.getFolder().getRealPath(), fileInfo.getRelativePath());
    }

    @Override
    public void handleFileSync(TcpConnection connection, ClientSession session, FileSyncMessage message) {
        try {
            statusAnalyzer = new StatusAnalyzer(session.getFolder().getRealPath());
            var localChanges = statusAnalyzer.analyze();
            var localLastUpdateTime = statusAnalyzer.getLastUpdateTime();
            if (localLastUpdateTime.isEmpty()) {
                localLastUpdateTime = Optional.of(Instant.now()); //TODO
            }
            var remoteChanges = message.getChanges().stream().collect(Collectors.
                    toMap(LogEntry::getRelativePath, c -> c));
            var result = statusAnalyzer.compare(remoteChanges);
            var conflictFiles = result.getConflicts().stream().map(ConflictItem::getRelativePath).collect(Collectors.joining("\n"));
            Files.writeString(Path.of(session.getFolder().getRealPath(), ".conflicts.log"), conflictFiles);

            for (var delete : result.getFilesToDelete()) {
                var pathToDelete = Path.of(session.getFolder().getRealPath(), delete);
                if (Files.exists(pathToDelete)) {
                    Files.delete(pathToDelete);
                }
            }
            var filesToRemoveRemote = new ArrayList<String>();
            for (var delete : result.getFilesToDeleteRemote()) {
                var pathToDelete = Path.of(session.getFolder().getRealPath(), delete);
                filesToRemoveRemote.add(delete);
            }

            List<FileInfo> filesToSend = new ArrayList<>();
            for (var file : result.getFilesToUpdate()) {
                if(file.getRelativePath().equalsIgnoreCase(".conflicts.log")){
                    continue; //Do not send conflicts log
                }
                var path = Path.of(session.getFolder().getRealPath(), file.getRelativePath());
                var fi = new FileInfo();

                if(Files.exists(path)){
                    fi = FileInfo.fromFile(path.toFile(), session.getFolder().getRealPath());
                }

                var li = file.getLogEntry();
                fi.setRelativePath(file.getRelativePath());
                fi.setCreationTime(li.getCreationTime());
                fi.setModificationTime(li.getModificationTime());
                fi.setSize(li.getSize());
                filesToSend.add(fi);
            }
            connection.sendMessage(new FileListResponseMessage(filesToSend, filesToRemoveRemote, true, 1, 1));

            var response = connection.receiveMessage();
            if (response.getMessageType() != MessageType.FILE_SYNC_ACK) {
                log.error("[CLIENT] Unexpected response 6: {}", response.getMessageType());
                return;
            }

            List<FileInfo> filesToReceive = new ArrayList<>();
            for (var file : result.getFilesToSend()) {
                if(file.getRelativePath().equalsIgnoreCase(".conflicts.log")){
                    continue; //Do not send conflicts log
                }
                var path = Path.of(session.getFolder().getRealPath(), file.getRelativePath());
                filesToReceive.add(FileInfo.fromFile(path.toFile(), session.getFolder().getRealPath()));
            }
            session.closeChildConnections();

            connection.sendMessage(new FileListResponseMessage(filesToReceive, List.of(), true, 1, 1));
            handleFileRestore(connection, session, filesToReceive);
            log.debug("[SERVER] File sync completed successfully. Local changes: {}, Remote changes: {}", localChanges.size(), remoteChanges.size());


        } catch (Exception ex) {
            log.error("[SERVER] Error analyzing status: {}", ex.getMessage(), ex);
        } finally {
            try {
                statusAnalyzer.analyze();
            } catch (IOException e) {
                log.trace("[SERVER] error on analyzing", e);
            }
        }
    }
}
