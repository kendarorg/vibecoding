package org.kendar.sync.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for backup and restore functionality.
 * This test:
 * 1. Sets up a source directory with random files and nested directories
 * 2. Sets up a target directory
 * 3. Simulates a backup by copying files from source to target
 * 4. Verifies that files are backed up correctly
 * 5. Removes a file from the source directory
 * 6. Simulates a restore by copying files from target to source
 * 7. Verifies that files are restored correctly
 */
public class BackupIntegrationTest {

    private Path testRoot;
    private File sourceDir;
    private File targetDir;
    private List<File> createdFiles;
    private File removedFile;

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

        // Create test files in the source directory with random content
        createdFiles = createRandomFiles(sourceDir, 5, 3);
        
        System.out.println("Created test files in " + sourceDir.getAbsolutePath());
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up test directories if needed
    }

    @Test
    void testBackupAndRestore() throws Exception {
        // Perform backup
        performBackup();

        // Verify backup
        verifyBackup();

        // Remove a file from the source directory
        removedFile = removeRandomFile();

        // Perform restore
        performRestore();

        // Verify restore
        verifyRestore();
    }

    /**
     * Creates random files in the specified directory.
     *
     * @param dir The directory to create files in
     * @param numFiles The number of files to create
     * @param maxDepth The maximum directory depth
     * @return The list of created files
     * @throws IOException If an I/O error occurs
     */
    private List<File> createRandomFiles(File dir, int numFiles, int maxDepth) throws IOException {
        List<File> files = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < numFiles; i++) {
            // Decide whether to create a file or directory
            boolean isDirectory = random.nextBoolean() && maxDepth > 0;
            String name = isDirectory ? "dir_" + i : "file_" + i + ".txt";
            File file = new File(dir, name);

            if (isDirectory) {
                file.mkdir();
                // Recursively create files in the directory
                files.addAll(createRandomFiles(file, numFiles / 2, maxDepth - 1));
            } else {
                // Create a file with random content
                String content = "Content for " + name + ": " + UUID.randomUUID();
                Files.writeString(file.toPath(), content);
                files.add(file);
            }
        }

        return files;
    }

    /**
     * Performs a backup operation by copying files from source to target.
     *
     * @throws IOException If an I/O error occurs
     */
    private void performBackup() throws IOException {
        System.out.println("Performing backup from " + sourceDir.getAbsolutePath() + " to " + targetDir.getAbsolutePath());
        
        // Copy all files from source to target
        for (File sourceFile : createdFiles) {
            if (sourceFile.isDirectory()) {
                continue; // Skip directories, they'll be created when needed
            }
            
            String relativePath = getRelativePath(sourceFile, sourceDir);
            File targetFile = new File(targetDir, relativePath);
            
            // Create parent directories if needed
            targetFile.getParentFile().mkdirs();
            
            // Copy the file
            Files.copy(sourceFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println("Backed up file: " + relativePath);
        }
    }

    /**
     * Verifies that files were backed up correctly.
     *
     * @throws IOException If an I/O error occurs
     */
    private void verifyBackup() throws IOException {
        System.out.println("Verifying backup...");
        
        // Check that all files were backed up
        for (File sourceFile : createdFiles) {
            if (sourceFile.isDirectory()) {
                continue; // Skip directories
            }
            
            String relativePath = getRelativePath(sourceFile, sourceDir);
            File targetFile = new File(targetDir, relativePath);
            
            assertTrue(targetFile.exists(), "Target file should exist: " + relativePath);
            
            String sourceContent = Files.readString(sourceFile.toPath());
            String targetContent = Files.readString(targetFile.toPath());
            assertEquals(sourceContent, targetContent, "File content should match for " + relativePath);
        }
        
        System.out.println("Backup verification successful");
    }

    /**
     * Removes a random file from the source directory.
     *
     * @return The removed file
     * @throws IOException If an I/O error occurs
     */
    private File removeRandomFile() throws IOException {
        // Find a non-directory file to remove
        List<File> nonDirectoryFiles = createdFiles.stream()
                .filter(file -> !file.isDirectory())
                .toList();
        
        if (nonDirectoryFiles.isEmpty()) {
            throw new IllegalStateException("No non-directory files to remove");
        }
        
        // Remove a random file
        File fileToRemove = nonDirectoryFiles.get(0);
        Files.delete(fileToRemove.toPath());
        
        System.out.println("Removed file: " + getRelativePath(fileToRemove, sourceDir));
        
        return fileToRemove;
    }

    /**
     * Performs a restore operation by copying files from target to source.
     *
     * @throws IOException If an I/O error occurs
     */
    private void performRestore() throws IOException {
        System.out.println("Performing restore from " + targetDir.getAbsolutePath() + " to " + sourceDir.getAbsolutePath());
        
        // Get the relative path of the removed file
        String removedFilePath = getRelativePath(removedFile, sourceDir);
        File targetFile = new File(targetDir, removedFilePath);
        
        // Copy the file back from target to source
        Files.copy(targetFile.toPath(), removedFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        
        System.out.println("Restored file: " + removedFilePath);
    }

    /**
     * Verifies that files were restored correctly.
     *
     * @throws IOException If an I/O error occurs
     */
    private void verifyRestore() throws IOException {
        System.out.println("Verifying restore...");
        
        // Check that the removed file was restored
        String relativePath = getRelativePath(removedFile, sourceDir);
        
        assertTrue(removedFile.exists(), "Removed file should be restored: " + relativePath);
        
        // Check content
        String targetContent = Files.readString(new File(targetDir, relativePath).toPath());
        String restoredContent = Files.readString(removedFile.toPath());
        assertEquals(targetContent, restoredContent, "Restored file content should match target file content");
        
        System.out.println("Restore verification successful");
    }

    /**
     * Gets the relative path of a file to a base directory.
     *
     * @param file The file
     * @param baseDir The base directory
     * @return The relative path
     */
    private String getRelativePath(File file, File baseDir) {
        String basePath = baseDir.getAbsolutePath();
        String filePath = file.getAbsolutePath();
        
        if (filePath.startsWith(basePath)) {
            String relativePath = filePath.substring(basePath.length());
            if (relativePath.startsWith(File.separator)) {
                relativePath = relativePath.substring(1);
            }
            return relativePath;
        }
        
        return file.getName();
    }
}