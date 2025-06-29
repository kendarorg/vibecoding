package org.kendar.sync.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

/**
 * Tests for the backup functionality in SyncClientApp.
 */
class SyncClientAppBackupTest {

    private final PrintStream originalOut = System.out;
    private final PrintStream originalErr = System.err;
    private Path testRoot;
    private File sourceDir;
    private File targetDir;
    private ByteArrayOutputStream outContent;
    private ByteArrayOutputStream errContent;
    private TcpConnection mockConnection;
    private Object commandLineArgs;
    private SyncClient target;
    private SyncClientBackup syncClientBackup;

    @BeforeEach
    void setUp() throws Exception {
        outContent = new ByteArrayOutputStream();
        errContent = new ByteArrayOutputStream();
        // Create a unique test directory inside target/tests
        String uniqueId = UUID.randomUUID().toString();
        testRoot = Path.of("target", "tests", uniqueId);
        Files.createDirectories(testRoot);

        // Create source and target directories
        sourceDir = new File(testRoot.toFile(), "source");
        targetDir = new File(testRoot.toFile(), "target");
        sourceDir.mkdir();
        targetDir.mkdir();

        // Create test files in the source directory
        File testFile1 = new File(sourceDir, "testFile1.txt");
        Files.writeString(testFile1.toPath(), "Test content 1");

        File testSubDir = new File(sourceDir, "subdir");
        testSubDir.mkdir();

        File testFile2 = new File(testSubDir, "testFile2.txt");
        Files.writeString(testFile2.toPath(), "Test content 2");

        // Redirect System.out for testing output
        System.setOut(new PrintStream(outContent));
        System.setErr(new PrintStream(errContent));

        // Create a mock TcpConnection
        mockConnection = mock(TcpConnection.class);
        when(mockConnection.getMaxPacketSize()).thenReturn(1024);


        // Create CommandLineArgs object using reflection
        Class<?> commandLineArgsClass = Class.forName("org.kendar.sync.client.CommandLineArgs");
        commandLineArgs = commandLineArgsClass.getDeclaredConstructor().newInstance();

        // Set field values using reflection
        commandLineArgsClass.getDeclaredMethod("setSourceFolder", String.class)
                .invoke(commandLineArgs, sourceDir.getAbsolutePath());
        commandLineArgsClass.getDeclaredMethod("setTargetFolder", String.class)
                .invoke(commandLineArgs, "documents");
        commandLineArgsClass.getDeclaredMethod("setBackup", boolean.class)
                .invoke(commandLineArgs, true);
        commandLineArgsClass.getDeclaredMethod("setBackupType", BackupType.class)
                .invoke(commandLineArgs, BackupType.MIRROR);
        commandLineArgsClass.getDeclaredMethod("setDryRun", boolean.class)
                .invoke(commandLineArgs, false);
        commandLineArgsClass.getDeclaredMethod("setMaxSize", int.class)
                .invoke(commandLineArgs, 1024);
        commandLineArgsClass.getDeclaredMethod("setMaxConnections", int.class)
                .invoke(commandLineArgs, 1);

        syncClientBackup = new SyncClientBackup() {
            @Override
            protected TcpConnection getTcpConnection(TcpConnection connection,
                                                     CommandLineArgs args, int i, int maxPacketSize) throws IOException {
                return connection;
            }
        };
    }

    @AfterEach
    public void tearDown() throws Exception {
        System.setOut(originalOut);
        System.setOut(originalErr);
    }

    @Test
    void testPerformBackup() throws Exception {
        // Set up mock responses
        List<FileInfo> filesToTransfer = List.of(
                new FileInfo("/test/subdir/testFile2.txt", "subdir/testFile2.txt", 0, Instant.now(), Instant.now(),
                        7));
        FileListResponseMessage fileListResponse = new FileListResponseMessage(
                filesToTransfer, new ArrayList<>(), true, 1, 1);
        when(mockConnection.receiveMessage())
                .thenReturn(fileListResponse)
                .thenReturn(FileDescriptorAckMessage.ready(sourceDir.getName() + "/subdir/testFile2.txt"))
                .thenReturn(new FileDataAck())
                .thenReturn(FileEndAckMessage.success("/subdir/testFile2.txt"));


        // Call the performBackup method
        syncClientBackup.performBackup(mockConnection, (CommandLineArgs) commandLineArgs, 1, 1024,false, false, new ArrayList<>());

        // Verify that the correct messages were sent
        // 1. FileListMessage
        verify(mockConnection, times(1)).sendMessage(ArgumentMatchers.argThat(new ArgumentMatcher<Message>() {
            @Override
            public boolean matches(Message message) {
                return message instanceof FileListMessage;
            }
        }));

        // 2. FileDescriptorMessage for source directory
        verify(mockConnection, times(1)).sendMessage(ArgumentMatchers.argThat(new ArgumentMatcher<Message>() {
            @Override
            public boolean matches(Message message) {
                if (!(message instanceof FileDescriptorMessage)) return false;
                FileDescriptorMessage fdm = (FileDescriptorMessage) message;
                return fdm.getFileInfo().getRelativePath().equals("subdir/testFile2.txt");
            }
        }));

        // 3. FileDescriptorMessage for subdir
        verify(mockConnection, times(1)).sendMessage(ArgumentMatchers.argThat(new ArgumentMatcher<Message>() {
            @Override
            public boolean matches(Message message) {
                if (!(message instanceof FileDataMessage)) return false;
                FileDataMessage fdm = (FileDataMessage) message;
                return fdm.getRelativePath().equals("subdir/testFile2.txt");
            }
        }));

        // 8. FileEndMessage for testFile1.txt
        verify(mockConnection, times(1)).sendMessage(ArgumentMatchers.argThat(new ArgumentMatcher<Message>() {
            @Override
            public boolean matches(Message message) {
                if (!(message instanceof FileEndMessage)) return false;
                FileEndMessage fem = (FileEndMessage) message;
                return fem.getRelativePath().equals("subdir/testFile2.txt");
            }
        }));


        // Verify output
        String output = outContent.toString();
        assertTrue(output.contains("Starting backup"));
        assertTrue(output.contains("Found"));
        assertTrue(output.contains("files to backup"));
    }

    @Test
    void testPerformBackupDryRun() throws Exception {
        // Set dry run mode
        Class<?> commandLineArgsClass = Class.forName("org.kendar.sync.client.CommandLineArgs");
        commandLineArgsClass.getDeclaredMethod("setDryRun", boolean.class)
                .invoke(commandLineArgs, true);

        // Set up mock responses
        List<FileInfo> filesToTransfer = List.of(
                new FileInfo("/test/subdir/testFile2.txt", "subdir/testFile2.txt", 0, Instant.now(), Instant.now(), 0),
                new FileInfo("/test/testFile1.txt", "testFile1.txt", 0, Instant.now(), Instant.now(), 0));
        FileListResponseMessage fileListResponse = new FileListResponseMessage(
                filesToTransfer, new ArrayList<>(), true, 1, 1);
        when(mockConnection.receiveMessage())
                .thenReturn(fileListResponse)
                .thenReturn(FileDescriptorAckMessage.ready(sourceDir.getName() + "/subdir/testFile2.txt"))
                .thenReturn(FileEndAckMessage.success("/subdir/testFile2.txt"));

        // Call the performBackup method
        syncClientBackup.performBackup(mockConnection, (CommandLineArgs) commandLineArgs, 1, 1024
                ,false, false, new ArrayList<>());

        // Verify that the correct messages were sent
        // 1. FileListMessage
        verify(mockConnection, times(1)).sendMessage(ArgumentMatchers.argThat(new ArgumentMatcher<Message>() {
            @Override
            public boolean matches(Message message) {
                return message instanceof FileListMessage;
            }
        }));

        // Verify that no FileDataMessage was sent (dry run)
        verify(mockConnection, never()).sendMessage(ArgumentMatchers.argThat(new ArgumentMatcher<Message>() {
            @Override
            public boolean matches(Message message) {
                return message instanceof FileDataMessage;
            }
        }));

        // Verify output
        String output = outContent.toString();
        assertTrue(output.contains("Starting backup"));
        assertTrue(output.contains("Dry run: Would send file data"));
    }

    @Test
    void testPerformBackupWithErrors() throws Exception {
        // Set up mock responses with errors
        List<FileInfo> filesToTransfer = List.of(
                new FileInfo("/test/subdir/testFile2.txt", "subdir/testFile2.txt", 0, Instant.now(), Instant.now(), 0),
                new FileInfo("/test/testFile1.txt", "testFile1.txt", 0, Instant.now(), Instant.now(), 0));
        FileListResponseMessage fileListResponse = new FileListResponseMessage(
                filesToTransfer, new ArrayList<>(), true, 1, 1);

        // First response is FileListResponse
        // Second response is FileDescriptorAck with error
        // Third response is FileDescriptorAck with success
        // Fourth response is FileEndAck with error
        when(mockConnection.receiveMessage())
                .thenReturn(fileListResponse)
                .thenReturn(FileDescriptorAckMessage.notReady(sourceDir.getName() + "/testFile1.txt", "File already exists"))
                .thenReturn(new FileDataAck())
                .thenReturn(FileDescriptorAckMessage.ready(sourceDir.getName() + "/subdir/testFile2.txt"))
                .thenReturn(new FileDataAck())
                .thenReturn(FileEndAckMessage.failure(sourceDir.getName() + "/subdir/testFile2.txt", "Failed to write file"));

        // Call the performBackup method
        syncClientBackup.performBackup(mockConnection, (CommandLineArgs) commandLineArgs, 1, 1024
                ,false, false, new ArrayList<>());

        // Verify output
        String output = outContent.toString() + errContent.toString();
        assertTrue(output.contains("Server not ready to receive file: File already exists"));
    }
}
