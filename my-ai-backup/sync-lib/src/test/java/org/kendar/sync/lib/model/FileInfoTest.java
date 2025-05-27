package org.kendar.sync.lib.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileInfoTest {

    private static final String TEST_CONTENT = "Test file content";
    private File testFile;
    private File testDir;
    private String baseDir;

    @BeforeEach
    void setUp(@TempDir Path tempDir) throws IOException {
        // Create a unique test directory inside target/tests
        String uniqueId = UUID.randomUUID().toString();
        Path testRoot = Path.of("target", "tests", uniqueId);
        Files.createDirectories(testRoot);
        
        // Create a test directory
        testDir = new File(testRoot.toFile(), "testDir");
        testDir.mkdir();
        
        // Create a test file
        testFile = new File(testDir, "testFile.txt");
        Files.writeString(testFile.toPath(), TEST_CONTENT);
        
        baseDir = testRoot.toString();
    }

    @Test
    void testFromFile() throws IOException {
        // Test creating FileInfo from a file
        FileInfo fileInfo = FileInfo.fromFile(testFile, baseDir);
        
        // Verify properties
        assertEquals(testFile.getAbsolutePath(), fileInfo.getPath());
        assertTrue(fileInfo.getRelativePath().endsWith("testDir/testFile.txt"));
        assertEquals(TEST_CONTENT.length(), fileInfo.getSize());
        assertFalse(fileInfo.isDirectory());
        
        // Get file attributes to verify timestamps
        BasicFileAttributes attrs = Files.readAttributes(testFile.toPath(), BasicFileAttributes.class);
        assertEquals(attrs.creationTime().toInstant(), fileInfo.getCreationTime());
        assertEquals(attrs.lastModifiedTime().toInstant(), fileInfo.getModificationTime());
    }

    @Test
    void testFromDirectory() throws IOException {
        // Test creating FileInfo from a directory
        FileInfo dirInfo = FileInfo.fromFile(testDir, baseDir);
        
        // Verify properties
        assertEquals(testDir.getAbsolutePath(), dirInfo.getPath());
        assertTrue(dirInfo.getRelativePath().endsWith("testDir"));
        assertTrue(dirInfo.isDirectory());
    }

    @Test
    void testToFile() throws IOException {
        // Create a FileInfo and convert it back to a File
        FileInfo fileInfo = FileInfo.fromFile(testFile, baseDir);
        File convertedFile = fileInfo.toFile(baseDir);
        
        // Verify the converted file
        assertEquals(testFile.getAbsolutePath(), convertedFile.getAbsolutePath());
        assertTrue(convertedFile.exists());
        assertEquals(TEST_CONTENT, Files.readString(convertedFile.toPath()));
    }

    @Test
    void testEqualsAndHashCode() throws IOException {
        // Create two FileInfo objects with the same relative path but different absolute paths
        FileInfo fileInfo1 = FileInfo.fromFile(testFile, baseDir);
        
        // Create a second FileInfo with the same relative path
        FileInfo fileInfo2 = new FileInfo(
            "different/absolute/path.txt",
            fileInfo1.getRelativePath(),
            fileInfo1.getSize(),
            fileInfo1.getCreationTime(),
            fileInfo1.getModificationTime(),
            fileInfo1.isDirectory()
        );
        
        // Create a third FileInfo with a different relative path
        FileInfo fileInfo3 = new FileInfo(
            fileInfo1.getPath(),
            "different/relative/path.txt",
            fileInfo1.getSize(),
            fileInfo1.getCreationTime(),
            fileInfo1.getModificationTime(),
            fileInfo1.isDirectory()
        );
        
        // Test equals
        assertEquals(fileInfo1, fileInfo2, "FileInfo objects with same relative path should be equal");
        assertNotEquals(fileInfo1, fileInfo3, "FileInfo objects with different relative paths should not be equal");
        
        // Test hashCode
        assertEquals(fileInfo1.hashCode(), fileInfo2.hashCode(), "Equal objects should have equal hash codes");
        assertNotEquals(fileInfo1.hashCode(), fileInfo3.hashCode(), "Different objects should have different hash codes");
    }

    @Test
    void testGettersAndSetters() {
        // Create a FileInfo with the constructor
        String path = "/test/path.txt";
        String relativePath = "path.txt";
        long size = 100L;
        Instant creationTime = Instant.now().minusSeconds(3600);
        Instant modificationTime = Instant.now();
        boolean isDirectory = false;
        
        FileInfo fileInfo = new FileInfo(path, relativePath, size, creationTime, modificationTime, isDirectory);
        
        // Test getters
        assertEquals(path, fileInfo.getPath());
        assertEquals(relativePath, fileInfo.getRelativePath());
        assertEquals(size, fileInfo.getSize());
        assertEquals(creationTime, fileInfo.getCreationTime());
        assertEquals(modificationTime, fileInfo.getModificationTime());
        assertEquals(isDirectory, fileInfo.isDirectory());
        
        // Test setters
        String newPath = "/new/path.txt";
        String newRelativePath = "new/path.txt";
        long newSize = 200L;
        Instant newCreationTime = Instant.now().minusSeconds(7200);
        Instant newModificationTime = Instant.now().plusSeconds(3600);
        boolean newIsDirectory = true;
        
        fileInfo.setPath(newPath);
        fileInfo.setRelativePath(newRelativePath);
        fileInfo.setSize(newSize);
        fileInfo.setCreationTime(newCreationTime);
        fileInfo.setModificationTime(newModificationTime);
        fileInfo.setDirectory(newIsDirectory);
        
        assertEquals(newPath, fileInfo.getPath());
        assertEquals(newRelativePath, fileInfo.getRelativePath());
        assertEquals(newSize, fileInfo.getSize());
        assertEquals(newCreationTime, fileInfo.getCreationTime());
        assertEquals(newModificationTime, fileInfo.getModificationTime());
        assertEquals(newIsDirectory, fileInfo.isDirectory());
    }

    @Test
    void testToString() throws IOException {
        FileInfo fileInfo = FileInfo.fromFile(testFile, baseDir);
        String toString = fileInfo.toString();
        
        // Verify toString contains important fields
        assertTrue(toString.contains(fileInfo.getRelativePath()));
        assertTrue(toString.contains(String.valueOf(fileInfo.getSize())));
        assertTrue(toString.contains(fileInfo.getModificationTime().toString()));
    }
}