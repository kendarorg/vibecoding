package org.kendar.sync.lib.protocol;

import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.model.FileInfo;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for all protocol message classes.
 */
class ProtocolMessagesTest {

    @Test
    void testConnectMessage() throws IOException {
        // Create a test message
        ConnectMessage originalMessage = new ConnectMessage(
                "testuser",
                "password123",
                "documents",
                1024 * 1024,
                5,
                false
        );

        // Serialize the message
        byte[] serialized = originalMessage.serialize();

        // Deserialize the message
        ConnectMessage deserializedMessage = Message.deserialize(serialized, ConnectMessage.class);

        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.CONNECT, deserializedMessage.getMessageType());
        assertEquals("testuser", deserializedMessage.getUsername());
        assertEquals("password123", deserializedMessage.getPassword());
        assertEquals("documents", deserializedMessage.getTargetFolder());
        assertEquals(1024 * 1024, deserializedMessage.getMaxPacketSize());
        assertEquals(5, deserializedMessage.getMaxConnections());
        assertFalse(deserializedMessage.isDryRun());
    }

    @Test
    void testConnectResponseMessage() throws IOException {
        // Create a test message
        ConnectResponseMessage originalMessage = new ConnectResponseMessage(
                true,
                null,
                1024 * 1024,
                5,
                BackupType.NONE
        );

        // Serialize the message
        byte[] serialized = originalMessage.serialize();

        // Deserialize the message
        ConnectResponseMessage deserializedMessage = Message.deserialize(serialized, ConnectResponseMessage.class);

        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.CONNECT_RESPONSE, deserializedMessage.getMessageType());
        assertTrue(deserializedMessage.isAccepted());
        assertTrue(deserializedMessage.getErrorMessage().isEmpty());
        assertEquals(1024 * 1024, deserializedMessage.getMaxPacketSize());
        assertEquals(5, deserializedMessage.getMaxConnections());

        // Test with error message
        ConnectResponseMessage errorMessage = new ConnectResponseMessage(
                false,
                "Authentication failed",
                1024 * 1024,
                5,
                BackupType.NONE
        );

        byte[] errorSerialized = errorMessage.serialize();
        ConnectResponseMessage deserializedErrorMessage = Message.deserialize(errorSerialized, ConnectResponseMessage.class);

        assertFalse(deserializedErrorMessage.isAccepted());
        assertEquals("Authentication failed", deserializedErrorMessage.getErrorMessage());
    }

    @Test
    void testFileDataMessage() throws IOException {
        // Create a test message
        String relativePath = "documents/test.txt";
        int chunkIndex = 0;
        int totalChunks = 1;
        byte[] data = "Test file content".getBytes();

        FileDataMessage originalMessage = new FileDataMessage(
                relativePath,
                chunkIndex,
                totalChunks,
                data
        );

        // Serialize the message
        byte[] serialized = originalMessage.serialize();

        // Deserialize the message
        FileDataMessage deserializedMessage = Message.deserialize(serialized, FileDataMessage.class);

        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.FILE_DATA, deserializedMessage.getMessageType());
        assertEquals(relativePath, deserializedMessage.getRelativePath());
        assertEquals(chunkIndex, deserializedMessage.getBlockNumber());
        assertEquals(totalChunks, deserializedMessage.getTotalBlocks());
        assertArrayEquals(data, deserializedMessage.getData());
    }

    @Test
    void testFileDescriptorMessage() throws IOException {
        // Create a test FileInfo
        FileInfo fileInfo = new FileInfo(
                "/test/path.txt",
                "path.txt",
                100L,
                Instant.now().minusSeconds(3600),
                Instant.now(),
                false
        );

        // Create a test message
        FileDescriptorMessage originalMessage = new FileDescriptorMessage(fileInfo);

        // Serialize the message
        byte[] serialized = originalMessage.serialize();

        // Deserialize the message
        FileDescriptorMessage deserializedMessage = Message.deserialize(serialized, FileDescriptorMessage.class);

        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.FILE_DESCRIPTOR, deserializedMessage.getMessageType());

        var dtf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        FileInfo deserializedFileInfo = deserializedMessage.getFileInfo();
        //assertEquals(fileInfo.getPath(), deserializedFileInfo.getPath());
        assertEquals(fileInfo.getRelativePath(), deserializedFileInfo.getRelativePath());
        assertEquals(fileInfo.getSize(), deserializedFileInfo.getSize());
        assertEquals(dtf.format(new Date(fileInfo.getCreationTime().toEpochMilli())),
                dtf.format(new Date(deserializedFileInfo.getCreationTime().toEpochMilli())));

        assertEquals(dtf.format(new Date(fileInfo.getModificationTime().toEpochMilli())),
                dtf.format(new Date(deserializedFileInfo.getModificationTime().toEpochMilli())));
        assertEquals(fileInfo.isDirectory(), deserializedFileInfo.isDirectory());
    }

    @Test
    void testFileDescriptorAckMessage() throws IOException {
        // Create a test message
        FileDescriptorAckMessage originalMessage = FileDescriptorAckMessage.ready("documents/test.txt");

        // Serialize the message
        byte[] serialized = originalMessage.serialize();

        // Deserialize the message
        FileDescriptorAckMessage deserializedMessage = Message.deserialize(serialized, FileDescriptorAckMessage.class);

        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.FILE_DESCRIPTOR_ACK, deserializedMessage.getMessageType());
        assertEquals("documents/test.txt", deserializedMessage.getRelativePath());
        assertTrue(deserializedMessage.isReady());
        assertTrue(deserializedMessage.getErrorMessage().isEmpty());

        // Test with error message
        FileDescriptorAckMessage errorMessage = FileDescriptorAckMessage.notReady("documents/test.txt", "File already exists");

        byte[] errorSerialized = errorMessage.serialize();
        FileDescriptorAckMessage deserializedErrorMessage = Message.deserialize(errorSerialized, FileDescriptorAckMessage.class);

        assertFalse(deserializedErrorMessage.isReady());
        assertEquals("File already exists", deserializedErrorMessage.getErrorMessage());
    }

    @Test
    void testFileEndMessage() throws IOException {
        // Create a test FileInfo
        FileInfo fileInfo = new FileInfo(
                "/test/path.txt",
                "path.txt",
                100L,
                Instant.now().minusSeconds(3600),
                Instant.now(),
                false
        );

        // Create a test message
        FileEndMessage originalMessage = new FileEndMessage("path.txt", fileInfo);

        // Serialize the message
        byte[] serialized = originalMessage.serialize();

        // Deserialize the message
        FileEndMessage deserializedMessage = Message.deserialize(serialized, FileEndMessage.class);

        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.FILE_END, deserializedMessage.getMessageType());
        assertEquals("path.txt", deserializedMessage.getRelativePath());


        var dtf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        FileInfo deserializedFileInfo = deserializedMessage.getFileInfo();
        //assertEquals(fileInfo.getPath(), deserializedFileInfo.getPath());
        assertEquals(fileInfo.getRelativePath(), deserializedFileInfo.getRelativePath());
        assertEquals(fileInfo.getSize(), deserializedFileInfo.getSize());
        assertEquals(dtf.format(new Date(fileInfo.getCreationTime().toEpochMilli())),
                dtf.format(new Date(deserializedFileInfo.getCreationTime().toEpochMilli())));

        assertEquals(dtf.format(new Date(fileInfo.getModificationTime().toEpochMilli())),
                dtf.format(new Date(deserializedFileInfo.getModificationTime().toEpochMilli())));
        assertEquals(fileInfo.isDirectory(), deserializedFileInfo.isDirectory());
    }

    @Test
    void testFileEndAckMessage() throws IOException {
        // Create a test message
        FileEndAckMessage originalMessage = FileEndAckMessage.success("documents/test.txt");

        // Serialize the message
        byte[] serialized = originalMessage.serialize();

        // Deserialize the message
        FileEndAckMessage deserializedMessage = Message.deserialize(serialized, FileEndAckMessage.class);

        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.FILE_END_ACK, deserializedMessage.getMessageType());
        assertEquals("documents/test.txt", deserializedMessage.getRelativePath());
        assertTrue(deserializedMessage.isSuccess());
        assertTrue(deserializedMessage.getErrorMessage().isEmpty());

        // Test with error message
        FileEndAckMessage errorMessage = FileEndAckMessage.failure("documents/test.txt", "Failed to write file");

        byte[] errorSerialized = errorMessage.serialize();
        FileEndAckMessage deserializedErrorMessage = Message.deserialize(errorSerialized, FileEndAckMessage.class);

        assertFalse(deserializedErrorMessage.isSuccess());
        assertEquals("Failed to write file", deserializedErrorMessage.getErrorMessage());
    }

    @Test
    void testFileListMessage() throws IOException {
        // Create test file list
        List<FileInfo> files = new ArrayList<>();
        files.add(new FileInfo("/test/file1.txt", "file1.txt", 100L, Instant.now(), Instant.now(), false));
        files.add(new FileInfo("/test/dir", "dir", 0L, Instant.now(), Instant.now(), true));

        // Create a test message
        FileListMessage originalMessage = new FileListMessage(files, true, 1, 1);

        // Serialize the message
        byte[] serialized = originalMessage.serialize();

        // Deserialize the message
        FileListMessage deserializedMessage = Message.deserialize(serialized, FileListMessage.class);

        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.FILE_LIST, deserializedMessage.getMessageType());
        assertEquals(2, deserializedMessage.getFiles().size());
        assertTrue(deserializedMessage.isBackup());
        assertEquals(1, deserializedMessage.getPartNumber());
        assertEquals(1, deserializedMessage.getTotalParts());

        // Verify the file info objects
        FileInfo deserializedFile1 = deserializedMessage.getFiles().get(0);
        assertEquals("file1.txt", deserializedFile1.getRelativePath());
        assertEquals(100L, deserializedFile1.getSize());
        assertFalse(deserializedFile1.isDirectory());

        FileInfo deserializedDir = deserializedMessage.getFiles().get(1);
        assertEquals("dir", deserializedDir.getRelativePath());
        assertTrue(deserializedDir.isDirectory());
    }

    @Test
    void testFileListResponseMessage() throws IOException {
        // Create test file lists
        List<FileInfo> filesToTransfer = new ArrayList<>();
        filesToTransfer.add(new FileInfo("/test/file1.txt", "file1.txt", 100L, Instant.now(), Instant.now(), false));

        List<String> filesToDelete = Arrays.asList("file2.txt", "file3.txt");

        // Create a test message
        FileListResponseMessage originalMessage = new FileListResponseMessage(
                filesToTransfer,
                filesToDelete,
                true,
                1,
                1
        );

        // Serialize the message
        byte[] serialized = originalMessage.serialize();

        // Deserialize the message
        FileListResponseMessage deserializedMessage = Message.deserialize(serialized, FileListResponseMessage.class);

        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.FILE_LIST_RESPONSE, deserializedMessage.getMessageType());
        assertEquals(1, deserializedMessage.getFilesToTransfer().size());
        assertEquals(2, deserializedMessage.getFilesToDelete().size());
        assertTrue(deserializedMessage.isBackup());
        assertEquals(1, deserializedMessage.getPartNumber());
        assertEquals(1, deserializedMessage.getTotalParts());

        // Verify the file info objects
        FileInfo deserializedFile = deserializedMessage.getFilesToTransfer().get(0);
        assertEquals("file1.txt", deserializedFile.getRelativePath());
        assertEquals(100L, deserializedFile.getSize());
        assertFalse(deserializedFile.isDirectory());

        // Verify the files to delete
        assertEquals("file2.txt", deserializedMessage.getFilesToDelete().get(0));
        assertEquals("file3.txt", deserializedMessage.getFilesToDelete().get(1));
    }

    @Test
    void testSyncEndMessage() throws IOException {
        // Create a test message
        SyncEndMessage originalMessage = new SyncEndMessage();

        // Serialize the message
        byte[] serialized = originalMessage.serialize();

        // Deserialize the message
        SyncEndMessage deserializedMessage = Message.deserialize(serialized, SyncEndMessage.class);

        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.SYNC_END, deserializedMessage.getMessageType());
    }

    @Test
    void testSyncEndAckMessage() throws IOException {
        // Create a test message
        SyncEndAckMessage originalMessage = new SyncEndAckMessage(true, "Sync completed successfully");

        // Serialize the message
        byte[] serialized = originalMessage.serialize();

        // Deserialize the message
        SyncEndAckMessage deserializedMessage = Message.deserialize(serialized, SyncEndAckMessage.class);

        // Verify the deserialized message
        assertNotNull(deserializedMessage);
        assertEquals(MessageType.SYNC_END_ACK, deserializedMessage.getMessageType());
        assertTrue(deserializedMessage.isSuccess());
        assertEquals("Sync completed successfully", deserializedMessage.getErrorMessage());

        // Test with error
        SyncEndAckMessage errorMessage = new SyncEndAckMessage(false, "Sync failed");

        byte[] errorSerialized = errorMessage.serialize();
        SyncEndAckMessage deserializedErrorMessage = Message.deserialize(errorSerialized, SyncEndAckMessage.class);

        assertFalse(deserializedErrorMessage.isSuccess());
        assertEquals("Sync failed", deserializedErrorMessage.getErrorMessage());
    }
}
