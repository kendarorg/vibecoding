package org.kendar.sync.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kendar.sync.client.CommandLineArgs;
import org.kendar.sync.client.SyncClientApp;
import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.protocol.BackupType;
import org.kendar.sync.server.config.ServerConfig;
import org.kendar.sync.server.server.Server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.stream.Collectors;

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
    private ServerConfig serverConfig;
    private Server server;
    private int serverPort;
    private CommandLineArgs commandLineArgs;

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

        
        System.out.println("Created test files in " + sourceDir.getAbsolutePath());

        serverPort = 8085;

    }

    private void startServer(BackupType backupType) throws InterruptedException {
        serverConfig = new ServerConfig();
        var serverSettings = new ServerSettings();
        serverSettings.setPort(serverPort);
        serverSettings.setMaxConnections(1);
        serverSettings.setMaxPacketSize(1024*1024); // 1 MB
        ServerSettings.User newUser = new ServerSettings.User(UUID.randomUUID().toString(),
                "user", "password", true);
        serverSettings.getUsers().add(newUser);

        ServerSettings.BackupFolder backupFolder = new ServerSettings.BackupFolder();
        backupFolder.setBackupType(backupType);
        backupFolder.setAllowedUsers(List.of(newUser.getId()));
        backupFolder.setRealPath(targetDir.getAbsolutePath());
        backupFolder.setVirtualName("testBackup");
        serverSettings.getBackupFolders().add(backupFolder);
        serverConfig.setServerSettings(serverSettings);
        server = new Server(serverConfig, false);
        new Thread(()->server.startTcpServer()).start();
        Thread.sleep(200);
        commandLineArgs = new CommandLineArgs();
        commandLineArgs.setServerAddress("127.0.0.1");
        commandLineArgs.setServerPort(serverPort);
        commandLineArgs.setSourceFolder(sourceDir.getAbsolutePath());
        commandLineArgs.setTargetFolder("testBackup");
        commandLineArgs.setUsername("user");
        commandLineArgs.setPassword("password");
    }

    @AfterEach
    void tearDown() throws Exception {
        // Clean up test directories if needed
        server.stop();

    }

    public void cleanupDirs() throws IOException
    {
        deleteDirectoryContents(sourceDir.toPath());
        deleteDirectoryContents(targetDir.toPath());
    }

    /**
     * Deletes all files and subdirectories in the specified directory.
     * The directory itself is not deleted.
     *
     * @param directory The directory to clean
     * @return true if all files were deleted successfully, false otherwise
     * @throws IOException If an I/O error occurs
     */
    private boolean deleteDirectoryContents(Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return false;
        }

        boolean success = true;

        // Use Files.walk to traverse the directory tree in depth-first order
        try (var paths = Files.walk(directory)) {
            // Skip the root directory itself
            var filesToDelete = paths
                    .filter(path -> !path.equals(directory))
                    .sorted((a, b) -> -a.compareTo(b)) // Reverse order to delete children before parents
                    .toList();

            for (Path path : filesToDelete) {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    success = false;
                    System.err.println("Failed to delete: " + path + " - " + e.getMessage());
                }
            }
        }

        return success;
    }

    @Test
    void testBackupAndRestorePreserve() throws Exception {

        startServer(BackupType.PRESERVE);
        createdFiles = createRandomFiles(sourceDir, 5, 3);

        // Perform backup
        System.out.println("Performing backup...");
        commandLineArgs.setBackup(true);
        SyncClientApp.doSync(commandLineArgs);

        // Verify backup
        System.out.println("Verifying backup...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        // Remove a file from the source directory
        removedFile = removeRandomFile(sourceDir.toPath());

        // Perform restore
        System.out.println("Performing restore...");
        commandLineArgs.setBackup(false);
        SyncClientApp.doSync(commandLineArgs);

        // Verify restore
        System.out.println("Verifying restore...");
        verifyRestore();
        cleanupDirs();
    }


    @Test
    void testBackupAndRestoreMirror() throws Exception {

        startServer(BackupType.MIRROR);
        createdFiles = createRandomFiles(sourceDir, 5, 3);

        // Perform backup
        System.out.println("Performing backup...");
        commandLineArgs.setBackup(true);
        SyncClientApp.doSync(commandLineArgs);

        // Verify backup
        System.out.println("Verifying backup...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        // Remove a file from the source directory
        removedFile = removeRandomFile(sourceDir.toPath());

        // Perform backup with deleted file
        SyncClientApp.doSync(commandLineArgs);

        // Verify backup
        System.out.println("Verifying backup with deleted...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        // Remove a file from the source directory
        removedFile = removeRandomFile(targetDir.toPath());

        // Perform backup with deleted file
        SyncClientApp.doSync(commandLineArgs);

        // Verify backup
        System.out.println("Verifying backup with deleted target...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        // Delete a file from the target directory
        // Remove a file from the source directory
        removedFile = removeRandomFile(targetDir.toPath());

        // Perform restore
        System.out.println("Performing restore...");
        commandLineArgs.setBackup(false);
        SyncClientApp.doSync(commandLineArgs);

        // Verify restore
        System.out.println("Verifying restore...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());
        cleanupDirs();
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
     * Generates a random integer between the specified minimum and maximum values (inclusive).
     *
     * @param min The minimum value
     * @param max The maximum value
     * @return A random integer between min and max (inclusive)
     */
    private int getRandomNumber(int min, int max) {
        Random random = new Random();
        return random.nextInt(max - min + 1) + min;
    }
    /**
     * Removes a random file from the source directory.
     *
     * @return The removed file
     * @throws IOException If an I/O error occurs
     */
    private File removeRandomFile(Path fromDir) throws IOException {

        var allFiles = Files.walk(fromDir)
                .filter(path -> !Files.isDirectory(path))
                .collect(Collectors.toList());
        var rand = getRandomNumber(0, allFiles.size() - 1);


        
        // Remove a random file
        File fileToRemove = allFiles.get(rand).toFile();
        createdFiles.remove(fileToRemove);
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

    public static void assertDirectoriesEqual(Path dir1, Path dir2) throws IOException {
        if (!areDirectoriesEqual(dir1, dir2)) {
            fail("Directories are not equal: " + dir1 + " and " + dir2);
        }
    }

    public static boolean areDirectoriesEqual(Path dir1, Path dir2) throws IOException {
        if (!Files.isDirectory(dir1) || !Files.isDirectory(dir2)) {
            System.err.println("Not a directory: " + dir1+" "+dir2);
            return false;
        }

        // Get all files from both directories
        Set<Path> dir1Files = Files.walk(dir1)
                .filter(p -> !Files.isDirectory(p))
                .map(dir1::relativize)
                .collect(Collectors.toSet());

        Set<Path> dir2Files = Files.walk(dir2)
                .filter(p -> !Files.isDirectory(p))
                .map(dir2::relativize)
                .collect(Collectors.toSet());

        // Check if both directories have the same files
        if (!dir1Files.equals(dir2Files)) {
            System.err.println("Not same files: " + dir1+" "+dir2);
            return false;
        }

        // Compare the content of each file
        for (Path relativePath : dir1Files) {
            Path file1 = dir1.resolve(relativePath);
            Path file2 = dir2.resolve(relativePath);

            if (Files.size(file1) != Files.size(file2)) {
                System.err.println("Different size: " + file1+" "+file2);
                return false;
            }

            if (!Arrays.equals(Files.readAllBytes(file1), Files.readAllBytes(file2))) {
                System.err.println("Different content: " + file1+" "+file2);
                return false;
            }
        }

        return true;
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