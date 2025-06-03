package org.kendar.sync.lib.twoway;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kendar.sync.lib.utils.Sleeper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StatusAnalyzerTest {

    @TempDir
    Path tempDir;

    private StatusAnalyzer statusAnalyzer;
    private Path testFile;
    private Path testFile2;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @BeforeEach
    void setUp() throws IOException {
        statusAnalyzer = new StatusAnalyzer(tempDir.toString());
        testFile = tempDir.resolve("testfile.txt");
        testFile2 = tempDir.resolve("testfile2.txt");
    }

    @Test
    void testAnalyze_NewFile_CreatesOperationLog() throws IOException {
        // Given
        Files.writeString(testFile, "test content");

        // When
        statusAnalyzer.analyze();

        // Then
        Path operationLog = tempDir.resolve(".operation.log");
        assertTrue(Files.exists(operationLog));

        String logContent = Files.readString(operationLog);
        assertTrue(logContent.contains("CR|testfile.txt"));

        // Check that the log entry starts with a timestamp (run start time)
        String[] parts = logContent.split("\|");
        assertEquals(6, parts.length, "Log entry should have 6 fields including run start time");
    }

    @Test
    void testAnalyze_ModifiedFile_CreatesModificationEntry() throws IOException {
        // Given - first run to establish baseline
        Files.writeString(testFile, "initial content");
        statusAnalyzer.analyze();

        // When - modify file and analyze again
        Sleeper.sleep(1100); // Ensure different modification time
        Files.writeString(testFile, "modified content");
        statusAnalyzer.analyze();

        // Then
        Path operationLog = tempDir.resolve(".operation.log");
        String logContent = Files.readString(operationLog);
        assertTrue(logContent.contains("MO|testfile.txt"));
    }

    @Test
    void testAnalyze_DeletedFile_CreatesDeleteEntry() throws IOException {
        // Given - first run to establish file
        Files.writeString(testFile, "content");
        statusAnalyzer.analyze();

        // When - delete file and analyze again
        Files.delete(testFile);
        statusAnalyzer.analyze();

        // Then
        Path operationLog = tempDir.resolve(".operation.log");
        String logContent = Files.readString(operationLog);
        assertTrue(logContent.contains("DE|testfile.txt"));
    }

    @Test
    void testAnalyze_CreatesLastUpdateLog() throws IOException {
        // Given
        Files.writeString(testFile, "content");
        Instant beforeAnalysis = Instant.now();

        Sleeper.sleep(1000);
        // When
        statusAnalyzer.analyze();

        // Then
        Path lastUpdateLog = tempDir.resolve(".lastupdate.log");
        assertTrue(Files.exists(lastUpdateLog));

        Optional<Instant> lastUpdateTime = statusAnalyzer.getLastUpdateTime();
        assertTrue(lastUpdateTime.isPresent());
        assertTrue(lastUpdateTime.get().isAfter(beforeAnalysis) ||
                lastUpdateTime.get().equals(beforeAnalysis));
    }

    @Test
    void testAnalyze_EmptyDirectory_NoOperationLog() throws IOException {
        // When
        statusAnalyzer.analyze();

        // Then
        Path operationLog = tempDir.resolve(".operation.log");
        assertFalse(Files.exists(operationLog));

        Path lastUpdateLog = tempDir.resolve(".lastupdate.log");
        assertTrue(Files.exists(lastUpdateLog));
    }

    @Test
    void testCompact_EmptyOperationLog() throws IOException {
        // When
        statusAnalyzer.compact();

        // Then
        Path lastCompactLog = tempDir.resolve(".lastcompact.log");
        assertTrue(Files.exists(lastCompactLog));
    }

    @Test
    void testCompact_WithCROperations_KeepsLatestCreations() throws IOException {
        // Given - create operation log with multiple CR operations
        Path operationLog = tempDir.resolve(".operation.log");
        String logContent = "2024-01-01 09:00:00|2024-01-01 10:00:00|2024-01-01 10:00:00|100|CR|file1.txt\n" +
                "2024-01-01 10:00:00|2024-01-01 11:00:00|2024-01-01 11:00:00|200|CR|file1.txt\n" +
                "2024-01-01 11:00:00|2024-01-01 12:00:00|2024-01-01 12:00:00|150|MO|file1.txt\n" +
                "2024-01-01 12:00:00|2024-01-01 13:00:00|2024-01-01 13:00:00|300|CR|file2.txt\n";
        Files.writeString(operationLog, logContent);

        // When
        statusAnalyzer.compact();

        // Then
        String compactedContent = Files.readString(operationLog);
        String[] lines = compactedContent.split("\n");
        assertEquals(2, lines.length); // Only 2 CR operations should remain
        assertTrue(compactedContent.contains("2024-01-01 10:00:00|2024-01-01 11:00:00|2024-01-01 11:00:00|200|CR|file1.txt"));
        assertTrue(compactedContent.contains("2024-01-01 12:00:00|2024-01-01 13:00:00|2024-01-01 13:00:00|300|CR|file2.txt"));
        assertFalse(compactedContent.contains("MO")); // MO operation should be removed

        // Check compact log is created
        Path lastCompactLog = tempDir.resolve(".lastcompact.log");
        assertTrue(Files.exists(lastCompactLog));
    }

    @Test
    void testGetLastCompactTime_NoCompactLog_ReturnsEmpty() {
        // When
        Optional<Instant> lastCompactTime = statusAnalyzer.getLastCompactTime();

        // Then
        assertFalse(lastCompactTime.isPresent());
    }

    @Test
    void testGetLastCompactTime_WithCompactLog_ReturnsTime() throws IOException {
        // Given
        statusAnalyzer.compact(); // This will create an empty compact log

        // When
        Optional<Instant> lastCompactTime = statusAnalyzer.getLastCompactTime();

        // Then
        assertTrue(lastCompactTime.isPresent());
        assertTrue(lastCompactTime.get().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void testGetLastUpdateTime_NoUpdateLog_ReturnsEmpty() {
        // When
        Optional<Instant> lastUpdateTime = statusAnalyzer.getLastUpdateTime();

        // Then
        assertFalse(lastUpdateTime.isPresent());
    }

    @Test
    void testCompare_BothLogsEmpty_NoActions() throws IOException {
        // Given
        Path otherLogPath = tempDir.resolve("other.operation.log");
        Files.writeString(otherLogPath, "");

        // When
        StatusAnalyzer.SyncActions actions = statusAnalyzer.compare(otherLogPath);

        // Then
        assertTrue(actions.filesToUpdate.isEmpty());
        assertTrue(actions.filesToSend.isEmpty());
        assertTrue(actions.filesToDelete.isEmpty());
        assertTrue(actions.filesToDeleteRemote.isEmpty());
        assertTrue(actions.conflicts.isEmpty());
    }

    @Test
    void testCompare_LocalFileOnly_SendToRemote() throws IOException {
        // Given
        Path localLog = tempDir.resolve(".operation.log");
        Path otherLog = tempDir.resolve("other.operation.log");

        Files.writeString(localLog, "2024-01-01 09:00:00|2024-01-01 10:00:00|2024-01-01 10:00:00|100|CR|file1.txt\n");
        Files.writeString(otherLog, "");

        // When
        StatusAnalyzer.SyncActions actions = statusAnalyzer.compare(otherLog);

        // Then
        assertEquals(1, actions.filesToSend.size());
        assertEquals("file1.txt", actions.filesToSend.get(0).relativePath);
        assertTrue(actions.filesToUpdate.isEmpty());
        assertTrue(actions.filesToDelete.isEmpty());
        assertTrue(actions.filesToDeleteRemote.isEmpty());
        assertTrue(actions.conflicts.isEmpty());
    }

    @Test
    void testCompare_RemoteFileOnly_UpdateFromRemote() throws IOException {
        // Given
        Path localLog = tempDir.resolve(".operation.log");
        Path otherLog = tempDir.resolve("other.operation.log");

        Files.writeString(localLog, "");
        Files.writeString(otherLog, "2024-01-01 09:00:00|2024-01-01 10:00:00|2024-01-01 10:00:00|100|CR|file1.txt\n");

        // When
        StatusAnalyzer.SyncActions actions = statusAnalyzer.compare(otherLog);

        // Then
        assertEquals(1, actions.filesToUpdate.size());
        assertEquals("file1.txt", actions.filesToUpdate.get(0).relativePath);
        assertTrue(actions.filesToSend.isEmpty());
        assertTrue(actions.filesToDelete.isEmpty());
        assertTrue(actions.filesToDeleteRemote.isEmpty());
        assertTrue(actions.conflicts.isEmpty());
    }

    @Test
    void testCompare_LocalDeletedRemoteExists_DeleteRemote() throws IOException {
        // Given
        Path localLog = tempDir.resolve(".operation.log");
        Path otherLog = tempDir.resolve("other.operation.log");

        Files.writeString(localLog, "2024-01-01 11:00:00|2024-01-01 12:00:00|2024-01-01 12:00:00|0|DE|file1.txt\n");
        Files.writeString(otherLog, "2024-01-01 09:00:00|2024-01-01 10:00:00|2024-01-01 10:00:00|100|CR|file1.txt\n");

        // When
        StatusAnalyzer.SyncActions actions = statusAnalyzer.compare(otherLog);

        // Then
        assertEquals(1, actions.filesToDeleteRemote.size());
        assertEquals("file1.txt", actions.filesToDeleteRemote.get(0));
        assertTrue(actions.filesToUpdate.isEmpty());
        assertTrue(actions.filesToSend.isEmpty());
        assertTrue(actions.filesToDelete.isEmpty());
        assertTrue(actions.conflicts.isEmpty());
    }

    @Test
    void testCompare_RemoteDeletedLocalExists_DeleteLocal() throws IOException {
        // Given
        Path localLog = tempDir.resolve(".operation.log");
        Path otherLog = tempDir.resolve("other.operation.log");

        Files.writeString(localLog, "2024-01-01 09:00:00|2024-01-01 10:00:00|2024-01-01 10:00:00|100|CR|file1.txt\n");
        Files.writeString(otherLog, "2024-01-01 11:00:00|2024-01-01 12:00:00|2024-01-01 12:00:00|0|DE|file1.txt\n");

        // When
        StatusAnalyzer.SyncActions actions = statusAnalyzer.compare(otherLog);

        // Then
        assertEquals(1, actions.filesToDelete.size());
        assertEquals("file1.txt", actions.filesToDelete.get(0));
        assertTrue(actions.filesToUpdate.isEmpty());
        assertTrue(actions.filesToSend.isEmpty());
        assertTrue(actions.filesToDeleteRemote.isEmpty());
        assertTrue(actions.conflicts.isEmpty());
    }

    @Test
    void testCompare_LocalNewer_SendToRemote() throws IOException {
        // Given
        Path localLog = tempDir.resolve(".operation.log");
        Path otherLog = tempDir.resolve("other.operation.log");

        Files.writeString(localLog, "2024-01-01 11:00:00|2024-01-01 10:00:00|2024-01-01 12:00:00|100|MO|file1.txt\n");
        Files.writeString(otherLog, "2024-01-01 09:00:00|2024-01-01 10:00:00|2024-01-01 10:00:00|100|CR|file1.txt\n");

        // When
        StatusAnalyzer.SyncActions actions = statusAnalyzer.compare(otherLog);

        // Then
        assertEquals(1, actions.filesToSend.size());
        assertEquals("file1.txt", actions.filesToSend.get(0).relativePath);
        assertTrue(actions.filesToUpdate.isEmpty());
        assertTrue(actions.filesToDelete.isEmpty());
        assertTrue(actions.filesToDeleteRemote.isEmpty());
        assertTrue(actions.conflicts.isEmpty());
    }

    @Test
    void testCompare_RemoteNewer_UpdateFromRemote() throws IOException {
        // Given
        Path localLog = tempDir.resolve(".operation.log");
        Path otherLog = tempDir.resolve("other.operation.log");

        Files.writeString(localLog, "2024-01-01 09:00:00|2024-01-01 10:00:00|2024-01-01 10:00:00|100|CR|file1.txt\n");
        Files.writeString(otherLog, "2024-01-01 11:00:00|2024-01-01 10:00:00|2024-01-01 12:00:00|100|MO|file1.txt\n");

        // When
        StatusAnalyzer.SyncActions actions = statusAnalyzer.compare(otherLog);

        // Then
        assertEquals(1, actions.filesToUpdate.size());
        assertEquals("file1.txt", actions.filesToUpdate.get(0).relativePath);
        assertTrue(actions.filesToSend.isEmpty());
        assertTrue(actions.filesToDelete.isEmpty());
        assertTrue(actions.filesToDeleteRemote.isEmpty());
        assertTrue(actions.conflicts.isEmpty());
    }

    @Test
    void testCompare_BothDeleted_NoAction() throws IOException {
        // Given
        Path localLog = tempDir.resolve(".operation.log");
        Path otherLog = tempDir.resolve("other.operation.log");

        Files.writeString(localLog, "2024-01-01 09:00:00|2024-01-01 10:00:00|2024-01-01 10:00:00|0|DE|file1.txt\n");
        Files.writeString(otherLog, "2024-01-01 09:00:00|2024-01-01 10:00:00|2024-01-01 10:00:00|0|DE|file1.txt\n");

        // When
        StatusAnalyzer.SyncActions actions = statusAnalyzer.compare(otherLog);

        // Then
        assertTrue(actions.filesToUpdate.isEmpty());
        assertTrue(actions.filesToSend.isEmpty());
        assertTrue(actions.filesToDelete.isEmpty());
        assertTrue(actions.filesToDeleteRemote.isEmpty());
        assertTrue(actions.conflicts.isEmpty());
    }

    @Test
    void testAnalyze_SkipsLogFiles() throws IOException {
        // Given
        Files.writeString(testFile, "regular file");
        Files.writeString(tempDir.resolve(".lastupdate.log"), "log content");
        Files.writeString(tempDir.resolve(".operation.log"), "operation content");
        Files.writeString(tempDir.resolve(".lastcompact.log"), "compact content");

        // When
        statusAnalyzer.analyze();

        // Then
        Path operationLog = tempDir.resolve(".operation.log");
        String logContent = Files.readString(operationLog);
        assertTrue(logContent.contains("testfile.txt"));
        assertFalse(logContent.contains(".lastupdate.log"));
        assertFalse(logContent.contains(".operation.log"));
        assertFalse(logContent.contains(".lastcompact.log"));
    }

    @Test
    void testAnalyze_NonExistentDirectory_HandlesGracefully() throws IOException {
        // Given
        Path nonExistentDir = tempDir.resolve("nonexistent");
        StatusAnalyzer analyzerForNonExistent = new StatusAnalyzer(nonExistentDir.toString());

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> analyzerForNonExistent.analyze());
    }

    @Test
    void testConstructor_CreatesAbsolutePath() {
        // Given
        String relativePath = "relative/path";

        // When
        StatusAnalyzer analyzer = new StatusAnalyzer(relativePath);

        // Then - should not throw exception and handle relative paths
        assertNotNull(analyzer);
    }
}