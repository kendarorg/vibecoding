package org.kendar.sync.server.backup;

import org.junit.jupiter.api.*;
import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.lib.utils.FileUtils;
import org.kendar.sync.server.TestUtils;
import org.kendar.sync.server.server.ClientSession;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the MirrorBackupHandler class.
 */
public class MirrorBackupHandlerTest {

    private static String uniqueId;
    private MirrorBackupHandler handler;
    private TcpConnection mockConnection;
    private ClientSession mockSession;
    private File tempDir;
    private ServerSettings.BackupFolder mockFolder;

    @BeforeAll
    public static void beforeClass() {
        uniqueId = UUID.randomUUID().toString();
    }

    @AfterAll
    public static void cleanup() throws Exception {
        FileUtils.deleteDirectoryContents(Path.of("target", "tests", uniqueId));
    }

    @BeforeEach
    void setUp(TestInfo testInfo) throws IOException {

        tempDir = Path.of("target", "tests", uniqueId, TestUtils.getTestFolder(testInfo)).toFile();
        Files.createDirectories(tempDir.toPath());

        // Create mocks
        handler = new MirrorBackupHandler();
        mockConnection = Mockito.mock(TcpConnection.class);
        mockSession = Mockito.mock(ClientSession.class);
        mockFolder = Mockito.mock(ServerSettings.BackupFolder.class);

        // Set up mock behavior
        when(mockSession.getFolder()).thenReturn(mockFolder);
        when(mockFolder.getRealPath()).thenReturn(tempDir.getAbsolutePath());
        when(mockSession.isDryRun()).thenReturn(false);
    }

    @AfterEach
    void tearDown() throws IOException {
        FileUtils.deleteDirectoryContents(tempDir.toPath());
    }

    @Test
    void testHandleFileListBackup() throws IOException {
        // Create some files in the target directory
        File file1 = new File(tempDir, "file1.txt");
        File file2 = new File(tempDir, "file2.txt");
        Files.writeString(file1.toPath(), "file1 content");
        Files.writeString(file2.toPath(), "file2 content");

        // Create a file list message with only file1 in the source
        List<FileInfo> sourceFiles = new ArrayList<>();
        sourceFiles.add(new FileInfo(file1.getAbsolutePath(), "file1.txt", 100, Instant.now(), Instant.now(), false));
        FileListMessage message = new FileListMessage(sourceFiles, true, 1, 1);

        // Call the method
        handler.handleFileList(mockConnection, mockSession, message);

        // Verify that the correct response was sent
        ArgumentCaptor<FileListResponseMessage> captor = ArgumentCaptor.forClass(FileListResponseMessage.class);
        verify(mockConnection).sendMessage(captor.capture());

        FileListResponseMessage response = captor.getValue();
        assertTrue(response.isBackup());
        assertEquals(1, response.getFilesToTransfer().size());

        // Verify that file2.txt is in the list of files to delete
        List<String> filesToDelete = response.getFilesToDelete();
        assertEquals(0, filesToDelete.size());
    }

    @Test
    void testHandleFileDescriptor() throws IOException {
        // Create a file descriptor message for a regular file
        FileInfo fileInfo = new FileInfo("test.txt", "test.txt", 100, Instant.now(), Instant.now(), false);
        FileDescriptorMessage message = new FileDescriptorMessage(fileInfo);

        // Call the method
        handler.handleFileDescriptor(mockConnection, mockSession, message);

        // Verify that the correct response was sent
        ArgumentCaptor<FileDescriptorAckMessage> captor = ArgumentCaptor.forClass(FileDescriptorAckMessage.class);
        verify(mockConnection).sendMessage(captor.capture());

        FileDescriptorAckMessage response = captor.getValue();
        assertEquals("test.txt", response.getRelativePath());
        assertTrue(response.isReady());

        // Verify that the parent directory was created
        File file = new File(tempDir, "test.txt");
        assertTrue(file.getParentFile().exists());
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
        assertFalse(dir.exists());
    }

    @Test
    void testHandleFileData() throws IOException {
        // Create a file data message
        byte[] data = "test data".getBytes();
        FileDataMessage message = new FileDataMessage("test.txt", 0, 1, data);

        // Call the method
        handler.handleFileData(mockConnection, mockSession, message);

        // Verify that the file was created with the correct content
        File file = new File(tempDir, "test.txt");
        assertTrue(file.exists());
        assertEquals("test data", new String(Files.readAllBytes(file.toPath())));
    }

    @Test
    void testHandleFileEnd() throws IOException {
        // Create a file end message
        FileEndMessage message = new FileEndMessage("test.txt");
        var path = Path.of(mockSession.getFolder().getRealPath() + File.separator + "test.txt");
        Files.writeString(path, UUID.randomUUID().toString());
        FileInfo fileInfo = FileInfo.fromFile(path.toFile(), mockSession.getFolder().getRealPath());
        message.setFileInfo(fileInfo);


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