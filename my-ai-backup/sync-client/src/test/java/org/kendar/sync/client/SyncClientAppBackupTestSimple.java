package org.kendar.sync.client;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the backup functionality in SyncClientApp using a simple mock implementation.
 */
class SyncClientAppBackupTestSimple {

    private Path testRoot;
    private File sourceDir;
    private File targetDir;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private MockTcpConnection mockConnection;
    private Method performBackupMethod;
    private Object commandLineArgs;

    /**
     * Simple mock implementation of TcpConnection for testing.
     */
    private static class MockTcpConnection extends TcpConnection {
        private final List<Message> sentMessages = new ArrayList<>();
        private final List<Message> messagesToReturn = new ArrayList<>();
        private int currentMessageIndex = 0;

        public MockTcpConnection() throws IOException {
            super(new Socket(), UUID.randomUUID(), 0, 1024);
        }

        @Override
        public void sendMessage(Message message) throws IOException {
            sentMessages.add(message);
        }

        @Override
        public Message receiveMessage() throws IOException {
            if (currentMessageIndex < messagesToReturn.size()) {
                return messagesToReturn.get(currentMessageIndex++);
            }
            return null;
        }

        public void addMessageToReturn(Message message) {
            messagesToReturn.add(message);
        }

        public List<Message> getSentMessages() {
            return sentMessages;
        }

        @Override
        public void close() throws IOException {
            // Do nothing
        }
    }

    @BeforeEach
    void setUp() throws Exception {
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
        
        // Create a mock TcpConnection
        mockConnection = new MockTcpConnection();
        
        // Get the private performBackup method using reflection
        performBackupMethod = SyncClientApp.class.getDeclaredMethod("performBackup", TcpConnection.class, 
            Class.forName("org.kendar.sync.client.SyncClientApp$CommandLineArgs"));
        performBackupMethod.setAccessible(true);
        
        // Create CommandLineArgs object using reflection
        Class<?> commandLineArgsClass = Class.forName("org.kendar.sync.client.SyncClientApp$CommandLineArgs");
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
    }
    
    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }
    
    @Test
    void testPerformBackup() throws Exception {
        // Set up mock responses
        FileListResponseMessage fileListResponse = new FileListResponseMessage(
            new ArrayList<>(), new ArrayList<>(), true, 1, 1);
        mockConnection.addMessageToReturn(fileListResponse);
        
        // Add FileDescriptorAck responses for each file
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.ready(sourceDir.getName()));
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.ready(sourceDir.getName() + "/subdir"));
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.ready(sourceDir.getName() + "/testFile1.txt"));
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.ready(sourceDir.getName() + "/subdir/testFile2.txt"));
        
        // Add FileEndAck responses for each file
        mockConnection.addMessageToReturn(FileEndAckMessage.success(sourceDir.getName() + "/testFile1.txt"));
        mockConnection.addMessageToReturn(FileEndAckMessage.success(sourceDir.getName() + "/subdir/testFile2.txt"));
        
        // Call the performBackup method
        performBackupMethod.invoke(null, mockConnection, commandLineArgs);
        
        // Get the sent messages
        List<Message> sentMessages = mockConnection.getSentMessages();
        
        // Verify that the correct messages were sent
        // 1. FileListMessage
        assertTrue(sentMessages.get(0) instanceof FileListMessage);
        
        // Count the number of each message type
        int fileDescriptorCount = 0;
        int fileDataCount = 0;
        int fileEndCount = 0;
        
        for (Message message : sentMessages) {
            if (message instanceof FileDescriptorMessage) {
                fileDescriptorCount++;
            } else if (message instanceof FileDataMessage) {
                fileDataCount++;
            } else if (message instanceof FileEndMessage) {
                fileEndCount++;
            }
        }
        
        // Verify counts
        assertEquals(4, fileDescriptorCount); // source dir, subdir, 2 files
        assertEquals(2, fileDataCount); // 2 files
        assertEquals(2, fileEndCount); // 2 files
        
        // Verify output
        String output = outContent.toString();
        assertTrue(output.contains("Starting backup"));
        assertTrue(output.contains("Found"));
        assertTrue(output.contains("files to backup"));
    }
    
    @Test
    void testPerformBackupDryRun() throws Exception {
        // Set dry run mode
        Class<?> commandLineArgsClass = Class.forName("org.kendar.sync.client.SyncClientApp$CommandLineArgs");
        commandLineArgsClass.getDeclaredMethod("setDryRun", boolean.class)
            .invoke(commandLineArgs, true);
        
        // Set up mock responses
        FileListResponseMessage fileListResponse = new FileListResponseMessage(
            new ArrayList<>(), new ArrayList<>(), true, 1, 1);
        mockConnection.addMessageToReturn(fileListResponse);
        
        // Add FileDescriptorAck responses for each file
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.ready(sourceDir.getName()));
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.ready(sourceDir.getName() + "/subdir"));
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.ready(sourceDir.getName() + "/testFile1.txt"));
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.ready(sourceDir.getName() + "/subdir/testFile2.txt"));
        
        // Add FileEndAck responses for each file
        mockConnection.addMessageToReturn(FileEndAckMessage.success(sourceDir.getName() + "/testFile1.txt"));
        mockConnection.addMessageToReturn(FileEndAckMessage.success(sourceDir.getName() + "/subdir/testFile2.txt"));
        
        // Call the performBackup method
        performBackupMethod.invoke(null, mockConnection, commandLineArgs);
        
        // Get the sent messages
        List<Message> sentMessages = mockConnection.getSentMessages();
        
        // Verify that the correct messages were sent
        // 1. FileListMessage
        assertTrue(sentMessages.get(0) instanceof FileListMessage);
        
        // Verify that no FileDataMessage was sent (dry run)
        boolean fileDataMessageFound = false;
        for (Message message : sentMessages) {
            if (message instanceof FileDataMessage) {
                fileDataMessageFound = true;
                break;
            }
        }
        assertFalse(fileDataMessageFound);
        
        // Verify output
        String output = outContent.toString();
        assertTrue(output.contains("Starting backup"));
        assertTrue(output.contains("Dry run: Would send file data"));
    }
    
    @Test
    void testPerformBackupWithErrors() throws Exception {
        // Set up mock responses
        FileListResponseMessage fileListResponse = new FileListResponseMessage(
            new ArrayList<>(), new ArrayList<>(), true, 1, 1);
        mockConnection.addMessageToReturn(fileListResponse);
        
        // Add FileDescriptorAck responses with errors
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.ready(sourceDir.getName()));
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.ready(sourceDir.getName() + "/subdir"));
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.notReady(sourceDir.getName() + "/testFile1.txt", "File already exists"));
        mockConnection.addMessageToReturn(FileDescriptorAckMessage.ready(sourceDir.getName() + "/subdir/testFile2.txt"));
        
        // Add FileEndAck responses with errors
        mockConnection.addMessageToReturn(FileEndAckMessage.failure(sourceDir.getName() + "/subdir/testFile2.txt", "Failed to write file"));
        
        // Call the performBackup method
        performBackupMethod.invoke(null, mockConnection, commandLineArgs);
        
        // Verify output
        String output = outContent.toString();
        assertTrue(output.contains("Server not ready to receive file: File already exists"));
        assertTrue(output.contains("File transfer failed: Failed to write file"));
    }
}