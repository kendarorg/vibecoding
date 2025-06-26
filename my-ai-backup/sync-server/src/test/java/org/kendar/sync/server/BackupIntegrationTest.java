package org.kendar.sync.server;

import org.junit.jupiter.api.*;
import org.kendar.sync.client.CommandLineArgs;
import org.kendar.sync.client.SyncClient;
import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.protocol.BackupType;
import org.kendar.sync.lib.utils.FileUtils;
import org.kendar.sync.lib.utils.Sleeper;
import org.kendar.sync.server.config.ServerConfig;
import org.kendar.sync.server.server.Server;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.kendar.sync.server.TestUtils.*;

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

    private static String uniqueId;
    private Path testRoot;
    private File sourceDir;
    private File targetDir;
    private File removedFile;
    private ServerConfig serverConfig;
    private Server server;
    private int serverPort;
    private CommandLineArgs commandLineArgs;
    private boolean restore = true;


    @AfterAll
    public static void cleanup() throws Exception {
        FileUtils.deleteDirectoryContents(Path.of("target", "tests", uniqueId));
    }

    @BeforeAll
    public static void beforeClass() {
        uniqueId = UUID.randomUUID().toString();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
    }

    @BeforeEach
    void setUp(TestInfo testInfo) throws Exception {
        testRoot = Path.of("target", "tests", uniqueId, TestUtils.getTestFolder(testInfo));
        Files.createDirectories(testRoot);

        // Create source and target directories
        sourceDir = new File(testRoot.toFile(), "source");
        targetDir = new File(testRoot.toFile(), "target");
        sourceDir.mkdir();
        targetDir.mkdir();

        // Create test files in the source directory with random content


        System.out.println("================= Created test files in " + sourceDir.getAbsolutePath());

        serverPort = findFreePort();

    }

    private void startServer(BackupType backupType) throws InterruptedException {
        serverConfig = new ServerConfig();
        var serverSettings = new ServerSettings();
        serverSettings.setPort(serverPort);
        serverSettings.setMaxConnections(5);
        serverSettings.setMaxPacketSize(1024); // 1 MB
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
        new Thread(() -> server.startTcpServer()).start();
        Sleeper.sleep(200);
        commandLineArgs = new CommandLineArgs();
        commandLineArgs.setServerAddress("127.0.0.1");
        commandLineArgs.setServerPort(serverPort);
        commandLineArgs.setSourceFolder(sourceDir.getAbsolutePath());
        commandLineArgs.setTargetFolder("testBackup");
        commandLineArgs.setUsername("user");
        commandLineArgs.setPassword("password");
        commandLineArgs.setHostName("testHost");
    }

    @Test
    void testBackupAndRestoreDateSeparated() throws Exception {

        startServer(BackupType.DATE_SEPARATED);
        createRandomFiles(sourceDir, 5, 3);

        // Perform backup
        System.out.println("================= Performing backup 1...");
        commandLineArgs.setBackup(true);
        var target = new SyncClient();
        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup 2...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath(), BackupType.DATE_SEPARATED);

        //Add a non deletable file tot destination
        System.out.println("================= Adding new file on target 3...");
        Files.writeString(Path.of(targetDir.toPath() + "/atest.txt"), "testBackup");

        // Perform backup
        System.out.println("================= Performing backup 4...");
        commandLineArgs.setBackup(true);
        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup 5...");
        List<String> diff = new ArrayList<>();
        assertFalse(areDirectoriesEqual(sourceDir.toPath(), targetDir.toPath(), diff, BackupType.DATE_SEPARATED));
        assertEquals(List.of(sourceDir.toPath() + File.separator + "atest.txt"), diff);


        // Remove a file from the source directory
        removedFile = removeRandomFile(sourceDir.toPath(), sourceDir.toPath());

        System.out.println("================= Removing 6..."+removedFile);
        // Perform restore
        System.out.println("================= Performing restore 7...");
        if (restore) {
            commandLineArgs.setBackup(false);
            target.doSync(commandLineArgs);
            assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath(), BackupType.DATE_SEPARATED);
        }
    }

    @Test
    void testBackupAndRestorePreserve() throws Exception {

        startServer(BackupType.PRESERVE);
        createRandomFiles(sourceDir, 5, 3);

        // Perform backup
        System.out.println("================= Performing backup...");
        commandLineArgs.setBackup(true);
        var target = new SyncClient();
        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        //Add a non deletable file tot destination
        System.out.println("================= ADd file on target...");
        Files.writeString(Path.of(targetDir.toPath() + "/atest.txt"), "testBackup");

        // Perform backup
        System.out.println("================= Performing backup...");
        commandLineArgs.setBackup(true);
        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup...");
        List<String> diff = new ArrayList<>();
        assertFalse(areDirectoriesEqual(sourceDir.toPath(), targetDir.toPath(), diff));
        assertEquals(List.of(sourceDir.toPath() + File.separator + "atest.txt"), diff);


        // Remove a file from the source directory
        removedFile = removeRandomFile(sourceDir.toPath(), sourceDir.toPath());

        // Perform restore
        System.out.println("================= Performing restore...");
        if (restore) {
            commandLineArgs.setBackup(false);
            target.doSync(commandLineArgs);
            assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());
        }

    }

    @Test
    void testBackupAndRestoreMirror() throws Exception {

        startServer(BackupType.MIRROR);
        createRandomFiles(sourceDir, 5, 3);

        // Perform backup
        System.out.println("================= Performing backup...");
        commandLineArgs.setBackup(true);
        var target = new SyncClient();
        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        // Remove a file from the source directory
        removedFile = removeRandomFile(sourceDir.toPath(), sourceDir.toPath());

        // Perform backup with deleted file
        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup with deleted...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        // Remove a file from the source directory
        removedFile = removeRandomFile(sourceDir.toPath(), targetDir.toPath());

        // Perform backup with deleted file
        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup with deleted target...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        // Delete a file from the target directory
        removedFile = removeRandomFile(sourceDir.toPath(), targetDir.toPath());

        System.out.println("================= Adding file on target...");
        Files.writeString(Path.of(targetDir.toPath() + "/atest.txt"), "testBackup");

        // Perform restore
        System.out.println("================= Performing restore...");
        if (restore) {
            commandLineArgs.setBackup(false);
            target.doSync(commandLineArgs);

            // Verify restore
            System.out.println("================= Verifying restore...");
            assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());
        }

    }
}