package org.kendar.sync.lib.utils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.protocol.BackupType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileUtilsTest {

    private Path testRoot;
    private File sourceDir;
    private File targetDir;
    private File testFile1;
    private File testFile2;
    private File testSubDir;
    private File testSubFile;
    private static final String TEST_CONTENT_1 = "Test file content 1";
    private static final String TEST_CONTENT_2 = "Test file content 2";
    private static final String TEST_CONTENT_3 = "Test file content 3";

    @BeforeEach
    void setUp() throws IOException {
        // Create a unique test directory inside target/tests
        String uniqueId = UUID.randomUUID().toString();
        testRoot = Path.of("target", "tests", uniqueId);
        Files.createDirectories(testRoot);

        // Create source and target directories
        sourceDir = new File(testRoot.toFile(), "source");
        targetDir = new File(testRoot.toFile(), "target");
        sourceDir.mkdir();
        targetDir.mkdir();

        // Create test files in source directory
        testFile1 = new File(sourceDir, "testFile1.txt");
        Files.writeString(testFile1.toPath(), TEST_CONTENT_1);

        testFile2 = new File(sourceDir, "testFile2.txt");
        Files.writeString(testFile2.toPath(), TEST_CONTENT_2);

        // Create a subdirectory with a file
        testSubDir = new File(sourceDir, "subdir");
        testSubDir.mkdir();

        testSubFile = new File(testSubDir, "testSubFile.txt");
        Files.writeString(testSubFile.toPath(), TEST_CONTENT_3);
    }

    @Test
    void testListFiles() throws IOException {
        // List files in the source directory
        List<FileInfo> files = FileUtils.listFiles(sourceDir, sourceDir.getAbsolutePath());

        // Verify the list contains the expected files
        assertEquals(3, files.size());

        // Verify file details
        boolean foundFile1 = false;
        boolean foundFile2 = false;
        boolean foundSubDir = false;

        for (FileInfo file : files) {
            if (file.getRelativePath().equals("testFile1.txt")) {
                foundFile1 = true;
                assertEquals(TEST_CONTENT_1.length(), file.getSize());
                assertFalse(file.isDirectory());
            } else if (file.getRelativePath().equals("testFile2.txt")) {
                foundFile2 = true;
                assertEquals(TEST_CONTENT_2.length(), file.getSize());
                assertFalse(file.isDirectory());
            } else if (file.getRelativePath().equals("subdir")) {
                foundSubDir = true;
                assertTrue(file.isDirectory());
            }
        }

        assertTrue(foundFile1, "testFile1.txt not found");
        assertTrue(foundFile2, "testFile2.txt not found");
        assertTrue(foundSubDir, "subdir not found");
    }

    @Test
    void testListFilesRecursive() throws IOException {
        // List files recursively in the source directory (listFiles does recursive listing)
        List<FileInfo> files = FileUtils.listFiles(sourceDir, sourceDir.getAbsolutePath());

        // Verify the list contains the expected files
        assertEquals(4, files.size());

        // Verify file details
        boolean foundFile1 = false;
        boolean foundFile2 = false;
        boolean foundSubDir = false;
        boolean foundSubFile = false;

        for (FileInfo file : files) {
            if (file.getRelativePath().equals("testFile1.txt")) {
                foundFile1 = true;
                assertEquals(TEST_CONTENT_1.length(), file.getSize());
                assertFalse(file.isDirectory());
            } else if (file.getRelativePath().equals("testFile2.txt")) {
                foundFile2 = true;
                assertEquals(TEST_CONTENT_2.length(), file.getSize());
                assertFalse(file.isDirectory());
            } else if (file.getRelativePath().equals("subdir")) {
                foundSubDir = true;
                assertTrue(file.isDirectory());
            } else if (file.getRelativePath().equals("subdir/testSubFile.txt")) {
                foundSubFile = true;
                assertEquals(TEST_CONTENT_3.length(), file.getSize());
                assertFalse(file.isDirectory());
            }
        }

        assertTrue(foundFile1, "testFile1.txt not found");
        assertTrue(foundFile2, "testFile2.txt not found");
        assertTrue(foundSubDir, "subdir not found");
        assertTrue(foundSubFile, "subdir/testSubFile.txt not found");
    }

    @Test
    void testCalculateFileDifferences() throws IOException {
        // Create a file in the target directory that doesn't exist in the source
        File targetOnlyFile = new File(targetDir, "targetOnly.txt");
        Files.writeString(targetOnlyFile.toPath(), "Target only content");

        // Create a file in the target directory that exists in the source but with different content
        File targetFile1 = new File(targetDir, "testFile1.txt");
        Files.writeString(targetFile1.toPath(), "Different content");

        // List files in source and target
        List<FileInfo> sourceFiles = FileUtils.listFiles(sourceDir, sourceDir.getAbsolutePath());

        List<FileInfo> targetFiles = FileUtils.listFiles(targetDir, targetDir.getAbsolutePath());

        // Calculate differences with PRESERVE backup type
        Map<String, List<FileInfo>> diffPreserve = FileUtils.calculateFileDifferences(
            sourceFiles, targetFiles, BackupType.PRESERVE);

        // Verify differences
        List<FileInfo> toAdd = diffPreserve.get("toAdd");
        List<FileInfo> toUpdate = diffPreserve.get("toUpdate");
        List<FileInfo> toDelete = diffPreserve.get("toDelete");

        // In PRESERVE mode, files should be added and updated, but not deleted
        assertEquals(3, toAdd.size()); // testFile2.txt, subdir, subdir/testSubFile.txt
        assertEquals(1, toUpdate.size()); // testFile1.txt
        assertEquals(0, toDelete.size()); // Nothing should be deleted in PRESERVE mode

        // Calculate differences with MIRROR backup type
        Map<String, List<FileInfo>> diffMirror = FileUtils.calculateFileDifferences(
            sourceFiles, targetFiles, BackupType.MIRROR);

        // Verify differences
        toAdd = diffMirror.get("toAdd");
        toUpdate = diffMirror.get("toUpdate");
        toDelete = diffMirror.get("toDelete");

        // In MIRROR mode, files should be added, updated, and deleted
        assertEquals(3, toAdd.size()); // testFile2.txt, subdir, subdir/testSubFile.txt
        assertEquals(1, toUpdate.size()); // testFile1.txt
        assertEquals(1, toDelete.size()); // targetOnly.txt should be deleted
    }

    @Test
    void testCreateDirectoryIfNotExists() throws IOException {
        // Test creating a directory that doesn't exist
        File newDir = new File(testRoot.toFile(), "newDir");
        assertFalse(newDir.exists());

        FileUtils.createDirectoryIfNotExists(newDir);
        assertTrue(newDir.exists());
        assertTrue(newDir.isDirectory());

        // Test with a directory that already exists
        FileUtils.createDirectoryIfNotExists(newDir);
        assertTrue(newDir.exists());
        assertTrue(newDir.isDirectory());
    }

    @Test
    void testSetFileTimes() throws IOException {
        // Create a test file
        File testFile = new File(testRoot.toFile(), "timeTest.txt");
        Files.writeString(testFile.toPath(), "Test content");

        // Set file times
        Instant creationTime = Instant.now().minusSeconds(3600);
        Instant modificationTime = Instant.now().minusSeconds(1800);

        FileUtils.setFileTimes(testFile, creationTime, modificationTime);

        // Verify file times
        BasicFileAttributes attrs = Files.readAttributes(testFile.toPath(), BasicFileAttributes.class);

        // Note: Some file systems might not support setting creation time
        // So we only check modification time
        assertEquals(modificationTime.toEpochMilli() / 1000, 
                    attrs.lastModifiedTime().toInstant().toEpochMilli() / 1000);
    }

    @Test
    void testCopyFile() throws IOException {
        // Create a source file
        File sourceFile = new File(testRoot.toFile(), "source.txt");
        Files.writeString(sourceFile.toPath(), "Source content");

        // Create a target file
        File targetFile = new File(testRoot.toFile(), "target.txt");

        // Copy the file
        FileUtils.copyFile(sourceFile, targetFile);

        // Verify the target file
        assertTrue(targetFile.exists());
        assertEquals("Source content", Files.readString(targetFile.toPath()));
    }

    @Test
    void testReadAndWriteFile() throws IOException {
        // Create a test file
        File testFile = new File(testRoot.toFile(), "readWrite.txt");

        // Write data to the file
        byte[] data = "Test data".getBytes();
        FileUtils.writeFile(testFile, data);

        // Verify the file exists
        assertTrue(testFile.exists());

        // Read data from the file
        byte[] readData = FileUtils.readFile(testFile);

        // Verify the data
        assertArrayEquals(data, readData);
    }

    @Test
    void testDelete() throws IOException {
        // Create a test directory with files
        File deleteDir = new File(testRoot.toFile(), "deleteDir");
        deleteDir.mkdir();

        File deleteFile1 = new File(deleteDir, "file1.txt");
        Files.writeString(deleteFile1.toPath(), "File 1");

        File deleteSubDir = new File(deleteDir, "subdir");
        deleteSubDir.mkdir();

        File deleteFile2 = new File(deleteSubDir, "file2.txt");
        Files.writeString(deleteFile2.toPath(), "File 2");

        // Verify files exist
        assertTrue(deleteDir.exists());
        assertTrue(deleteFile1.exists());
        assertTrue(deleteSubDir.exists());
        assertTrue(deleteFile2.exists());

        // Delete the directory
        FileUtils.delete(deleteDir);

        // Verify files are deleted
        assertFalse(deleteDir.exists());
        assertFalse(deleteFile1.exists());
        assertFalse(deleteSubDir.exists());
        assertFalse(deleteFile2.exists());
    }
}
