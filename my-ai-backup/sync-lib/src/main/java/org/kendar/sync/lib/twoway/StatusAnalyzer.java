package org.kendar.sync.lib.twoway;

import java.io.*;
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
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private final Path baseDirectory;
    private final Path lastUpdateLogPath;
    private final Path operationLogPath;
    private Map<String, FileInfo> previousFileStates;
    
    public StatusAnalyzer(String baseDirectory) {
        this.baseDirectory = Paths.get(baseDirectory).toAbsolutePath();
        this.lastUpdateLogPath = this.baseDirectory.resolve(LAST_UPDATE_LOG);
        this.operationLogPath = this.baseDirectory.resolve(OPERATION_LOG);
        this.previousFileStates = new ConcurrentHashMap<>();
    }
    
    /**
     * Analyzes the directory and updates log files with changes since last run
     */
    public void analyze() throws IOException {
        Instant runStartTime = Instant.now();
        
        // Load previous state if exists
        loadPreviousState();
        
        // Get current file states
        Map<String, FileInfo> currentFileStates = getCurrentFileStates();
        
        // Compare and log changes
        List<LogEntry> changes = detectChanges(currentFileStates);
        
        // Write changes to operation log
        writeOperationLog(changes);
        
        // Update last update log
        writeLastUpdateLog(runStartTime);
        
        // Update internal state
        this.previousFileStates = currentFileStates;
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
                    // Only keep non-deleted files in previous state
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
        
        Files.walkFileTree(baseDirectory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                // Skip log files
                if (file.equals(lastUpdateLogPath) || file.equals(operationLogPath)) {
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
    
    private List<LogEntry> detectChanges(Map<String, FileInfo> currentStates) {
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
        
        return String.format("%s|%s|%d|%s|%s", 
                creationTime, modificationTime, entry.size, entry.operation, entry.relativePath);
    }
    
    private LogEntry parseLogEntry(String line) {
        String[] parts = line.split("\\|", 5);
        if (parts.length != 5) {
            return null;
        }
        
        try {
            Instant creationTime = LocalDateTime.parse(parts[0], TIMESTAMP_FORMAT)
                    .atZone(ZoneId.systemDefault()).toInstant();
            Instant modificationTime = LocalDateTime.parse(parts[1], TIMESTAMP_FORMAT)
                    .atZone(ZoneId.systemDefault()).toInstant();
            long size = Long.parseLong(parts[2]);
            String operation = parts[3];
            String relativePath = parts[4];
            
            return new LogEntry(creationTime, modificationTime, size, operation, relativePath);
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
            String content = Files.readString(lastUpdateLogPath).trim();
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
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof FileInfo)) return false;
            FileInfo other = (FileInfo) obj;
            return Objects.equals(creationTime, other.creationTime) &&
                   Objects.equals(modificationTime, other.modificationTime) &&
                   size == other.size;
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(creationTime, modificationTime, size);
        }
    }
    
    private static class LogEntry {
        final Instant creationTime;
        final Instant modificationTime;
        final long size;
        final String operation;
        final String relativePath;
        
        LogEntry(Instant creationTime, Instant modificationTime, long size, String operation, String relativePath) {
            this.creationTime = creationTime;
            this.modificationTime = modificationTime;
            this.size = size;
            this.operation = operation;
            this.relativePath = relativePath;
        }
    }
}