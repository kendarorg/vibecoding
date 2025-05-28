package org.kendar.sync.server.backup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.server.server.ClientSession;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the DateSeparatedBackupHandler class.
 */
public class DateSeparatedBackupHandlerTest {

    private DateSeparatedBackupHandler handler;
    private TcpConnection mockConnection;
    private ClientSession mockSession;
    private File tempDir;
    private ServerSettings.BackupFolder mockFolder;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @BeforeEach
    void setUp() throws IOException {
        // Create a temporary directory for testing
        tempDir = Files.createTempDirectory("date-separated-test").toFile();
        tempDir.deleteOnExit();

        // Create mocks
        handler = new DateSeparatedBackupHandler();
        mockConnection = Mockito.mock(TcpConnection.class);
        mockSession = Mockito.mock(ClientSession.class);
        mockFolder = Mockito.mock(ServerSettings.BackupFolder.class);

        // Set up mock behavior
        when(mockSession.getFolder()).thenReturn(mockFolder);
        when(mockFolder.getRealPath()).thenReturn(tempDir.getAbsolutePath());
        when(mockSession.isDryRun()).thenReturn(false);
    }

    @Test
    void testHandleFileList() throws IOException {
        // Create a file list message
        FileListMessage message = new FileListMessage(new ArrayList<>(), true, 1, 1);

        // Call the method
        handler.handleFileList(mockConnection, mockSession, message);

        // Verify that the correct response was sent
        ArgumentCaptor<FileListResponseMessage> captor = ArgumentCaptor.forClass(FileListResponseMessage.class);
        verify(mockConnection).sendMessage(captor.capture());
        
        FileListResponseMessage response = captor.getValue();
        assertTrue(response.isBackup());
        assertEquals(0, response.getFilesToTransfer().size());
        assertEquals(0, response.getFilesToDelete().size());
    }

    @Test
    void testHandleFileDescriptor() throws IOException {
        // Create a file descriptor message for a regular file
        Instant modificationTime = Instant.now();
        FileInfo fileInfo = new FileInfo("test.txt", "test.txt", 100, modificationTime, modificationTime, false);
        FileDescriptorMessage message = new FileDescriptorMessage(fileInfo);

        // Call the method
        handler.handleFileDescriptor(mockConnection, mockSession, message);

        // Verify that the correct response was sent
        ArgumentCaptor<FileDescriptorAckMessage> captor = ArgumentCaptor.forClass(FileDescriptorAckMessage.class);
        verify(mockConnection).sendMessage(captor.capture());
        
        FileDescriptorAckMessage response = captor.getValue();
        assertEquals("test.txt", response.getRelativePath());
        assertTrue(response.isReady());
        
        // Verify that the date directory was created
        LocalDate modificationDate = modificationTime.atZone(ZoneId.systemDefault()).toLocalDate();
        String dateDir = modificationDate.format(DATE_FORMATTER);
        File dateDirFile = new File(tempDir, dateDir);
        assertTrue(dateDirFile.exists());
        assertTrue(dateDirFile.isDirectory());
    }

    @Test
    void testHandleFileDescriptorForDirectory() throws IOException {
        // Create a file descriptor message for a directory
        FileInfo fileInfo = new FileInfo("testdir", "testdir", 0, Instant.now(), Instant.now(), true);
        FileDescriptorMessage message = new FileDescriptorMessage(fileInfo);

        // Call the method
        handler.handleFileDescriptor(mockConnection, mockSession, message);

        // Verify that the correct response was sent
        ArgumentCaptor<FileDescriptorAckMessage> captor = ArgumentCaptor.forClass(FileDescriptorAckMessage.class);
        verify(mockConnection).sendMessage(captor.capture());
        
        FileDescriptorAckMessage response = captor.getValue();
        assertEquals("testdir", response.getRelativePath());
        assertTrue(response.isReady());
        
        // Verify that the directory was created
        File dir = new File(tempDir, "testdir");
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
    }

    @Test
    void testHandleFileData() throws IOException {
        // Create a file data message
        byte[] data = "test data".getBytes();
        FileDataMessage message = new FileDataMessage("test.txt", 0, 1, data);

        // Call the method
        handler.handleFileData(mockConnection, mockSession, message);

        // Verify that the file was created with the correct content
        // For DATE_SEPARATED backup type, the file should be in a date directory
        LocalDate currentDate = LocalDate.now();
        String dateDir = currentDate.format(DATE_FORMATTER);
        File file = new File(new File(tempDir, dateDir), "test.txt");
        assertTrue(file.exists());
        assertEquals("test data", new String(Files.readAllBytes(file.toPath())));
    }

    @Test
    void testHandleFileEnd() throws IOException {
        // Create a file end message
        FileEndMessage message = new FileEndMessage("test.txt");

        // Call the method
        handler.handleFileEnd(mockConnection, mockSession, message);

        // Verify that the correct response was sent
        ArgumentCaptor<FileEndAckMessage> captor = ArgumentCaptor.forClass(FileEndAckMessage.class);
        verify(mockConnection).sendMessage(captor.capture());
        
        FileEndAckMessage response = captor.getValue();
        assertEquals("test.txt", response.getRelativePath());
        assertTrue(response.isSuccess());
    }

    @Test
    void testHandleSyncEnd() throws IOException {
        // Create a sync end message
        SyncEndMessage message = new SyncEndMessage();

        // Call the method
        handler.handleSyncEnd(mockConnection, mockSession, message);

        // Verify that the correct response was sent
        ArgumentCaptor<SyncEndAckMessage> captor = ArgumentCaptor.forClass(SyncEndAckMessage.class);
        verify(mockConnection).sendMessage(captor.capture());
        
        SyncEndAckMessage response = captor.getValue();
        assertTrue(response.isSuccess());
    }
}