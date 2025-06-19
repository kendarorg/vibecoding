package org.kendar.sync.lib.twoway;

import org.kendar.sync.lib.utils.FileUtils;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * StatusAnalyzer monitors files in a base directory and tracks changes
 * for two-way synchronization between client and server.
 */
public class StatusAnalyzer {

    private static final String LAST_UPDATE_LOG = ".lastupdate.log";
    private static final String OPERATION_LOG = ".operation.log";
    private static final String LAST_COMPACT_LOG = ".lastcompact.log";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Path baseDirectory;
    private final Path lastUpdateLogPath;
    private final Path operationLogPath;
    private final Path lastCompactLogPath;
    private Map<String, FileInfo> previousFileStates;

    public StatusAnalyzer(String baseDirectory) {
        this.baseDirectory = Paths.get(baseDirectory).toAbsolutePath();
        this.lastUpdateLogPath = this.baseDirectory.resolve(LAST_UPDATE_LOG);
        this.operationLogPath = this.baseDirectory.resolve(OPERATION_LOG);
        this.lastCompactLogPath = this.baseDirectory.resolve(LAST_COMPACT_LOG);
        this.previousFileStates = new ConcurrentHashMap<>();
    }

    /**
     * Analyzes the directory and updates log files with changes since the last run
     *
     * @return List of detected changes as LogEntry objects
     */
    public List<LogEntry> analyze() throws IOException {
        Instant runStartTime = Instant.now();

        // Load previous state if exists
        loadPreviousState();

        // Get current file states
        Map<String, FileInfo> currentFileStates = getCurrentFileStates();

        // Compare and log changes
        List<LogEntry> changes = detectChanges(currentFileStates, runStartTime);

        // Write changes to the operation log
        writeOperationLog(changes);

        // Update last update log
        writeLastUpdateLog(runStartTime);

        // Update internal state
        this.previousFileStates = currentFileStates;
        return changes;
    }

    /**
     * Compacts the operation.log file by keeping only the latest "CR" operations
     * and creates a .lastcompact.log with the timestamp of the operation
     */
    public void compact() throws IOException {
        Instant compactTime = Instant.now();
        if (Files.exists(operationLogPath)) {


            Map<String, LogEntry> latestCreations = new LinkedHashMap<>();

            // Read all entries and keep only the latest CR operation for each file
            try (BufferedReader reader = Files.newBufferedReader(operationLogPath)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    LogEntry entry = parseLogEntry(line);
                    if (entry != null && "CR".equals(entry.operation)) {
                        latestCreations.put(entry.relativePath, entry);
                    }
                }
            }

            // Write the compacted log with only CR operations
            try (BufferedWriter writer = Files.newBufferedWriter(operationLogPath,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                for (LogEntry entry : latestCreations.values()) {
                    writer.write(formatLogEntry(entry));
                    writer.newLine();
                }
            }

        }
        // Create .lastcompact.log with timestamp
        writeLastCompactLog(compactTime);
    }

    /**
     * Compares two operation.log files and determines synchronization actions
     *
     * @param otherLogPath Path to the other operation.log file
     * @return SyncActions containing lists of files to update/delete
     */
    public SyncActions compare(Path otherLogPath) throws IOException {
        Map<String, LogEntry> remoteOperations = loadOperationLog(otherLogPath);
        return compare(remoteOperations);
    }

    public SyncActions compare(Map<String, LogEntry> remoteOperations) throws IOException {
        Map<String, LogEntry> localOperations = loadOperationLog(operationLogPath);
        SyncActions actions = new SyncActions();
        Set<String> allFiles = new HashSet<>();
        allFiles.addAll(localOperations.keySet());
        allFiles.addAll(remoteOperations.keySet());

        for (String filePath : allFiles) {
            LogEntry localEntry = localOperations.get(filePath);
            LogEntry remoteEntry = remoteOperations.get(filePath);

            SyncDecision decision = decideSyncAction(localEntry, remoteEntry, filePath);

            switch (decision.action) {
                case UPDATE_FROM_REMOTE:
                    actions.filesToUpdate.add(new SyncItem(filePath, decision.sourceEntry));
                    break;
                case UPDATE_TO_REMOTE:
                    actions.filesToSend.add(new SyncItem(filePath, decision.sourceEntry));
                    break;
                case DELETE_LOCAL:
                    actions.filesToDelete.add(filePath);
                    break;
                case DELETE_REMOTE:
                    actions.filesToDeleteRemote.add(filePath);
                    break;
                case CONFLICT:
                    actions.conflicts.add(new ConflictItem(filePath, localEntry, remoteEntry));
                    break;
                case NO_ACTION:
                    // Do nothing
                    break;
            }
        }

        return actions;
    }

    private Map<String, LogEntry> loadOperationLog(Path logPath) throws IOException {
        Map<String, LogEntry> operations = new HashMap<>();

        if (!Files.exists(logPath)) {
            return operations;
        }

        try (BufferedReader reader = Files.newBufferedReader(logPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry entry = parseLogEntry(line);
                if (entry != null) {
                    operations.put(entry.relativePath, entry);
                }
            }
        }

        return operations;
    }

    private SyncDecision decideSyncAction(LogEntry localEntry, LogEntry remoteEntry, String filePath) {
        // File exists only locally
        if (localEntry != null && remoteEntry == null) {
            if ("DE".equals(localEntry.operation)) {
                return new SyncDecision(SyncAction.NO_ACTION, null);
            } else {
                return new SyncDecision(SyncAction.UPDATE_TO_REMOTE, localEntry);
            }
        }

        // File exists only remotely
        if (localEntry == null && remoteEntry != null) {
            if ("DE".equals(remoteEntry.operation)) {
                return new SyncDecision(SyncAction.NO_ACTION, null);
            } else {
                return new SyncDecision(SyncAction.UPDATE_FROM_REMOTE, remoteEntry);
            }
        }

        // File exists in both logs
        if (localEntry != null) {
            // Both deleted
            if ("DE".equals(localEntry.operation) && "DE".equals(remoteEntry.operation)) {
                return new SyncDecision(SyncAction.NO_ACTION, null);
            }

            // Local deleted, remote exists
            if ("DE".equals(localEntry.operation)) {
                return new SyncDecision(SyncAction.DELETE_REMOTE, localEntry);
            }

            // The Remote is deleted, the local exists
            if ("DE".equals(remoteEntry.operation)) {
                return new SyncDecision(SyncAction.DELETE_LOCAL, remoteEntry);
            }

            // Both exist - compare modification times
            if (!localEntry.creationTime.equals(remoteEntry.creationTime)) {
                return new SyncDecision(SyncAction.CONFLICT, null);
            }
            if (!localEntry.modificationTime.equals(remoteEntry.modificationTime)) {
                if (localEntry.modificationTime.isAfter(remoteEntry.modificationTime)) {
                    return new SyncDecision(SyncAction.UPDATE_TO_REMOTE, localEntry);
                } else if (remoteEntry.modificationTime.isAfter(localEntry.modificationTime)) {
                    return new SyncDecision(SyncAction.UPDATE_FROM_REMOTE, remoteEntry);
                } else {
                    // Same timestamp but different - potential conflict
                    return new SyncDecision(SyncAction.CONFLICT, null);
                }
            }
        }

        return new SyncDecision(SyncAction.NO_ACTION, null);
    }

    private void writeLastCompactLog(Instant compactTime) throws IOException {
        Files.createDirectories(lastCompactLogPath.getParent());

        String timestamp = LocalDateTime.ofInstant(compactTime, ZoneId.systemDefault())
                .format(TIMESTAMP_FORMAT);

        try (BufferedWriter writer = Files.newBufferedWriter(lastCompactLogPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(timestamp);
        }
    }

    /**
     * Returns the last compact time from the log file
     */
    public Optional<Instant> getLastCompactTime() {
        if (!Files.exists(lastCompactLogPath)) {
            return Optional.empty();
        }

        try {
            String content = FileUtils.readFile(lastCompactLogPath).trim();
            if (content.isEmpty()) {
                return Optional.empty();
            }

            LocalDateTime dateTime = LocalDateTime.parse(content, TIMESTAMP_FORMAT);
            return Optional.of(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private void loadPreviousState() {
        if (!Files.exists(operationLogPath)) {
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(operationLogPath)) {
            String line;
            while ((line = reader.readLine()) != null) {
                LogEntry entry = parseLogEntry(line);
                if (entry != null && !entry.operation.equals("DE")) {
                    // Only keep non-deleted files in the previous state
                    previousFileStates.put(entry.relativePath,
                            new FileInfo(entry.creationTime, entry.modificationTime, entry.size));
                }
            }
        } catch (IOException e) {
            // If we can't read the log, start fresh
            previousFileStates.clear();
        }
    }

    private Map<String, FileInfo> getCurrentFileStates() throws IOException {
        Map<String, FileInfo> currentStates = new ConcurrentHashMap<>();

        if (!Files.exists(baseDirectory)) {
            return currentStates;
        }

        Files.walkFileTree(baseDirectory, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                // Skip log files
                if (file.equals(lastUpdateLogPath) || file.equals(operationLogPath) || file.equals(lastCompactLogPath)) {
                    return FileVisitResult.CONTINUE;
                }

                String relativePath = baseDirectory.relativize(file).toString().replace('\\', '/');
                Instant creationTime = attrs.creationTime().toInstant();
                Instant modificationTime = attrs.lastModifiedTime().toInstant();
                long size = attrs.size();

                currentStates.put(relativePath, new FileInfo(creationTime, modificationTime, size));
                return FileVisitResult.CONTINUE;
            }
        });

        return currentStates;
    }

    private List<LogEntry> detectChanges(Map<String, FileInfo> currentStates, Instant runStartTime) {
        List<LogEntry> changes = new ArrayList<>();
        Instant now = Instant.now();

        // Check for new and modified files
        for (Map.Entry<String, FileInfo> entry : currentStates.entrySet()) {
            String path = entry.getKey();
            FileInfo currentInfo = entry.getValue();
            FileInfo previousInfo = previousFileStates.get(path);

            if (previousInfo == null) {
                // New file
                changes.add(new LogEntry(
                        runStartTime,
                        currentInfo.creationTime,
                        currentInfo.modificationTime,
                        currentInfo.size,
                        "CR",
                        path
                ));
            } else if (!currentInfo.modificationTime.equals(previousInfo.modificationTime) ||
                    currentInfo.size != previousInfo.size) {
                // Modified file (time or size changed)
                changes.add(new LogEntry(
                        runStartTime,
                        currentInfo.creationTime,
                        currentInfo.modificationTime,
                        currentInfo.size,
                        "MO",
                        path
                ));
            }
        }

        // Check for deleted files
        for (Map.Entry<String, FileInfo> entry : previousFileStates.entrySet()) {
            String path = entry.getKey();
            if (!currentStates.containsKey(path)) {
                changes.add(new LogEntry(
                        runStartTime,
                        now,
                        now,
                        0L, // Size 0 for deleted files
                        "DE",
                        path
                ));
            }
        }

        return changes;
    }

    private void writeOperationLog(List<LogEntry> changes) throws IOException {
        if (changes.isEmpty()) {
            return;
        }

        // Ensure parent directory exists
        Files.createDirectories(operationLogPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(operationLogPath,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            for (LogEntry change : changes) {
                writer.write(formatLogEntry(change));
                writer.newLine();
            }
        }
    }

    private void writeLastUpdateLog(Instant runStartTime) throws IOException {
        // Ensure parent directory exists
        Files.createDirectories(lastUpdateLogPath.getParent());

        String timestamp = LocalDateTime.ofInstant(runStartTime, ZoneId.systemDefault())
                .format(TIMESTAMP_FORMAT);

        try (BufferedWriter writer = Files.newBufferedWriter(lastUpdateLogPath,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            writer.write(timestamp);
        }
    }

    private String formatLogEntry(LogEntry entry) {
        String creationTime = LocalDateTime.ofInstant(entry.creationTime, ZoneId.systemDefault())
                .format(TIMESTAMP_FORMAT);
        String modificationTime = LocalDateTime.ofInstant(entry.modificationTime, ZoneId.systemDefault())
                .format(TIMESTAMP_FORMAT);
        String runStartTime = LocalDateTime.ofInstant(entry.runStartTime, ZoneId.systemDefault())
                .format(TIMESTAMP_FORMAT);

        return String.format("%s|%s|%s|%d|%s|%s",
                runStartTime, creationTime, modificationTime, entry.size, entry.operation, entry.relativePath);
    }

    private LogEntry parseLogEntry(String line) {
        String[] parts = line.split("\\|", 6);
        if (parts.length != 6 && parts.length != 5) {
            return null;
        }

        try {
            // Handle both the old format (without runStartTime) and the  new format

            Instant runStartTime = LocalDateTime.parse(parts[0], TIMESTAMP_FORMAT)
                    .atZone(ZoneId.systemDefault()).toInstant();
            Instant creationTime = LocalDateTime.parse(parts[1], TIMESTAMP_FORMAT)
                    .atZone(ZoneId.systemDefault()).toInstant();
            Instant modificationTime = LocalDateTime.parse(parts[2], TIMESTAMP_FORMAT)
                    .atZone(ZoneId.systemDefault()).toInstant();
            long size = Long.parseLong(parts[3]);
            String operation = parts[4];
            String relativePath = parts[5];

            return new LogEntry(runStartTime, creationTime, modificationTime, size, operation, relativePath);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Returns the last update time from the log file
     */
    public Optional<Instant> getLastUpdateTime() {
        if (!Files.exists(lastUpdateLogPath)) {
            return Optional.empty();
        }

        try {
            String content = FileUtils.readFile(lastUpdateLogPath).trim();
            if (content.isEmpty()) {
                return Optional.empty();
            }

            LocalDateTime dateTime = LocalDateTime.parse(content, TIMESTAMP_FORMAT);
            return Optional.of(dateTime.atZone(ZoneId.systemDefault()).toInstant());
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // Inner classes for data structures
    private static class FileInfo {
        final Instant creationTime;
        final Instant modificationTime;
        final long size;

        FileInfo(Instant creationTime, Instant modificationTime, long size) {
            this.creationTime = creationTime;
            this.modificationTime = modificationTime;
            this.size = size;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof FileInfo)) return false;
            FileInfo fileInfo = (FileInfo) o;
            return size == fileInfo.size && Objects.equals(creationTime, fileInfo.creationTime) && Objects.equals(modificationTime, fileInfo.modificationTime);
        }

        @Override
        public int hashCode() {
            return Objects.hash(creationTime, modificationTime, size);
        }
    }


    private static class SyncDecision {
        final SyncAction action;
        final LogEntry sourceEntry;

        SyncDecision(SyncAction action, LogEntry sourceEntry) {
            this.action = action;
            this.sourceEntry = sourceEntry;
        }
    }
}