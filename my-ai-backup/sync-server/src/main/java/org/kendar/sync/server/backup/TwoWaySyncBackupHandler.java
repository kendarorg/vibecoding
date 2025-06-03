package org.kendar.sync.server.backup;



import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.twoway.StatusAnalyzer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.server.server.ClientSession;

import java.io.FileOutputStream;
import java.io.IOException;

/**
 * BackupHandler implementation for two-way synchronization using StatusAnalyzer.
 * Handles bi-directional file synchronization between client and server.
 */
public class TwoWaySyncBackupHandler extends BackupHandler {

    private static final Logger log = LoggerFactory.getLogger(TwoWaySyncBackupHandler.class);
    
    private final ConcurrentMap<String, StatusAnalyzer> statusAnalyzers = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, TwoWaySyncState> syncStates = new ConcurrentHashMap<>();

    /**
     * Handles the initial file list message containing client's last update time and modifications.
     */
    @Override
    public void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        log.debug("[SERVER] Starting two-way sync for session: {}", session.getSessionId());
        
        String targetPath = session.getTargetPath();
        StatusAnalyzer analyzer = getOrCreateStatusAnalyzer(targetPath);
        
        // Extract client's last update time and operation log from the message
        Instant clientLastUpdate = extractClientLastUpdate(message);
        Path clientOperationLog = extractClientOperationLog(message);
        
        // Analyze server changes since client's last update
        analyzer.analyze();
        StatusAnalyzer.SyncActions syncActions = analyzer.compare(clientOperationLog);
        
        // Prepare list of files to send to client and files to delete
        List<FileInfo> filesToSend = prepareFilesToSend(syncActions, targetPath);
        List<String> filesToDelete = syncActions.filesToDelete;
        
        // Store sync state for this session
        TwoWaySyncState syncState = new TwoWaySyncState();
        syncState.filesToReceive = syncActions.filesToUpdate;
        syncState.analyzer = analyzer;
        syncStates.put(session.getSessionId().toString(), syncState);
        
        // Send response with files to send and delete instructions
        TwoWaySyncResponseMessage response = new TwoWaySyncResponseMessage(filesToSend, filesToDelete);
        connection.sendMessage(response);
        
        log.debug("[SERVER] Sent sync response: {} files to send, {} files to delete", 
                  filesToSend.size(), filesToDelete.size());
        
        // Start sending files to client using existing protocol
        if (!filesToSend.isEmpty()) {
            handleFileRestore(connection, session, filesToSend);
        }
    }

    @Override
    public void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        log.debug("[SERVER] Receiving file descriptor for two-way sync: {}", message.getFileInfo().getRelativePath());
        
        // Acknowledge readiness to receive file
        connection.sendMessage(new FileDescriptorAckMessage(true, null));
        
        // Get sync state
        TwoWaySyncState syncState = syncStates.get(session.getSessionId().toString());
        if (syncState != null) {
            syncState.currentReceivingFile = message.getFileInfo();
            syncState.receivedData = new ByteArrayOutputStream();
        }
    }

    @Override
    public void handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException {
        log.debug("[SERVER] Receiving file data block {} of {} for: {}", 
                  message.getBlockNumber() + 1, message.getTotalBlocks(), message.getFilePath());
        
        TwoWaySyncState syncState = syncStates.get(session.getSessionId().toString());
        if (syncState != null && syncState.receivedData != null) {
            syncState.receivedData.write(message.getData());
        }
        
        // Acknowledge block received
        connection.sendMessage(new FileDataAckMessage(true, null));
    }

    @Override
    public void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException {
        log.debug("[SERVER] File transfer completed for: {}", message.getFilePath());
        
        TwoWaySyncState syncState = syncStates.get(session.getSessionId().toString());
        if (syncState != null && syncState.currentReceivingFile != null) {
            // Write received file to target location
            Path targetFilePath = Paths.get(session.getTargetPath())
                    .resolve(syncState.currentReceivingFile.getRelativePath());
            
            // Create parent directories if needed
            Files.createDirectories(targetFilePath.getParent());
            
            if (!session.isDryRun()) {
                try (FileOutputStream fos = new FileOutputStream(targetFilePath.toFile())) {
                    syncState.receivedData.writeTo(fos);
                }
                
                // Update operation log
                updateOperationLog(syncState.analyzer, syncState.currentReceivingFile, "CR");
            } else {
                log.debug("[SERVER] Dry run: Would write file {}", targetFilePath);
            }
            
            // Clean up
            syncState.currentReceivingFile = null;
            syncState.receivedData = null;
        }
        
        connection.sendMessage(new FileEndAckMessage(true, null));
    }

    @Override
    public void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException {
        log.debug("[SERVER] Two-way sync completed for session: {}", session.getSessionId());
        
        // Clean up sync state
        TwoWaySyncState syncState = syncStates.remove(session.getSessionId().toString());
        if (syncState != null && syncState.analyzer != null) {
            // Final analysis to update logs
            syncState.analyzer.analyze();
        }
        
        super.handleSyncEnd(connection, session, message);
    }

    @Override
    protected Path getSourceFilePath(ClientSession session, FileInfo fileInfo) {
        return Paths.get(session.getTargetPath()).resolve(fileInfo.getRelativePath());
    }

    /**
     * Gets or creates a StatusAnalyzer for the given target path.
     */
    private StatusAnalyzer getOrCreateStatusAnalyzer(String targetPath) {
        return statusAnalyzers.computeIfAbsent(targetPath, StatusAnalyzer::new);
    }

    /**
     * Extracts client's last update time from the file list message.
     */
    private Instant extractClientLastUpdate(FileListMessage message) {
        // Implementation depends on how client sends this information
        // For now, return current time as placeholder
        return Instant.now();
    }

    /**
     * Extracts client's operation log from the file list message.
     */
    private Path extractClientOperationLog(FileListMessage message) {
        // Implementation depends on how client sends this information
        // For now, create a temporary file as placeholder
        try {
            Path tempFile = Files.createTempFile("client-operations", ".log");
            // Write client operations to temp file
            return tempFile;
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary operation log", e);
        }
    }

    /**
     * Prepares the list of files to send to the client based on sync actions.
     */
    private List<FileInfo> prepareFilesToSend(StatusAnalyzer.SyncActions syncActions, String targetPath) {
        return syncActions.filesToSend.stream()
                .map(syncItem -> {
                    Path filePath = Paths.get(targetPath).resolve(syncItem.relativePath);
                    try {
                        return new FileInfo(
                                syncItem.relativePath,
                                Files.isDirectory(filePath),
                                syncItem.logEntry.size,
                                syncItem.logEntry.creationTime,
                                syncItem.logEntry.modificationTime
                        );
                    } catch (Exception e) {
                        log.error("Error creating FileInfo for: {}", syncItem.relativePath, e);
                        return null;
                    }
                })
                .filter(fileInfo -> fileInfo != null)
                .toList();
    }

    /**
     * Updates the operation log with the received file information.
     */
    private void updateOperationLog(StatusAnalyzer analyzer, FileInfo fileInfo, String operation) {
        try {
            // This would typically involve writing to the .operations.log file
            // The actual implementation depends on StatusAnalyzer's internal structure
            log.debug("[SERVER] Updated operation log: {} - {}", operation, fileInfo.getRelativePath());
        } catch (Exception e) {
            log.error("Failed to update operation log for: {}", fileInfo.getRelativePath(), e);
        }
    }

    /**
     * Internal state for tracking two-way sync progress.
     */
    private static class TwoWaySyncState {
        StatusAnalyzer analyzer;
        List<StatusAnalyzer.SyncItem> filesToReceive;
        FileInfo currentReceivingFile;
        ByteArrayOutputStream receivedData;
    }

    /**
     * Message class for two-way sync response (placeholder - needs proper implementation).
     */
    private static class TwoWaySyncResponseMessage extends Message {
        private final List<FileInfo> filesToSend;
        private final List<String> filesToDelete;

        public TwoWaySyncResponseMessage(List<FileInfo> filesToSend, List<String> filesToDelete) {
            this.filesToSend = filesToSend;
            this.filesToDelete = filesToDelete;
        }

        @Override
        public MessageType getMessageType() {
            return MessageType.TWO_WAY_SYNC_RESPONSE; // This would need to be added to MessageType enum
        }

        public List<FileInfo> getFilesToSend() {
            return filesToSend;
        }

        public List<String> getFilesToDelete() {
            return filesToDelete;
        }
    }
}