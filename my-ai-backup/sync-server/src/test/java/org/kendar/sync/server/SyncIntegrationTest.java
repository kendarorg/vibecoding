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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
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
public class SyncIntegrationTest {

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

    private static int findFreePort() {
        try (var serverSocket = new java.net.ServerSocket(0)) {
            return serverSocket.getLocalPort();
        } catch (IOException e) {
            throw new RuntimeException("Failed to find a free port", e);
        }
    }

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
        Sleeper.sleep(500);
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
        serverSettings.setMaxPacketSize(10*1024*1024); // 1 MB
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
        commandLineArgs.setMaxSize(10*1024*1024);
    }


    @Test
    void testSynchronize() throws Exception {

        startServer(BackupType.TWO_WAY_SYNC);
        createRandomFiles(sourceDir, 100, 3);
        createRandomFiles(targetDir, 100, 3);

        // Perform backup
        System.out.println("================= Performing backup...");
        var target = new SyncClient();
        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup 2...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        //Add a new file on target
        System.out.println("================= ADd file on target 2...");
        Files.writeString(Path.of(targetDir.toPath() + "/targetnew.txt"), "testBackup");

        // Perform backup
        System.out.println("================= Performing backup 2...");
        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup 3...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        //Add a new file on source
        System.out.println("================= ADd file on target...");
        Files.writeString(Path.of(sourceDir.toPath() + "/sourcenew.txt"), "testBackup");

        // Perform backup
        System.out.println("================= Performing backup...");
        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup 4...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

//
        // Remove a file from the source directory
        removedFile = removeRandomFile(sourceDir.toPath(), sourceDir.toPath());
        System.out.println("================= Performing backup...");

        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup 5...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());


        // Touch a file from the source directory
        var touchedFile = touchRandomFile(sourceDir.toPath(), sourceDir.toPath());
        System.out.println("================= Performing backup...");

        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup 6...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        Files.writeString(Path.of(sourceDir.toPath() + "/conflict.txt"), "testBackup");
        Sleeper.sleep(1100);
        Files.writeString(Path.of(targetDir.toPath() + "/conflict.txt"), "testBackup");

        target.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup 7...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());
        var conflicts = Files.readAllLines(Path.of(targetDir.toPath().toString(), ".conflicts.log"));
        assertTrue(conflicts.size() > 0, "There should be conflicts in the log file");
        assertTrue(conflicts.stream().anyMatch(conflict -> conflict.contains("conflict.txt")),
                "There should be a conflict for the file conflict.txt in the log file");

    }


    @Test
    void testSynchronizeStop() throws Exception {

        startServer(BackupType.TWO_WAY_SYNC);
        createRandomFiles(sourceDir, 1500, 3);
        createRandomFiles(targetDir, 1, 4);
        Sleeper.sleep(200);
        // Perform backup
        System.out.println("================= Performing backup...");
        var target = new SyncClient();
        target.setKeepAlive(50);
        new Thread(()->target.doSync(commandLineArgs)).start();
        var count =0;
        Sleeper.sleep(100);
        while(count<1000){
            //System.out.println(count);
            Sleeper.yield();
            count= countFilesInDirectory(targetDir);
        }
        System.out.println("================= Counted files in target "+count);
        target.disconnect();

        // Verify backup
        System.out.println("================= Verifying backup 1...");
        assertDirectoriesNotEqual(sourceDir.toPath(), targetDir.toPath());

    }

    public static int countFilesInDirectory(File directory) {
        int count = 0;
        for (File file : directory.listFiles()) {
            if (file.isFile()) {
                count++;
            }
            if (file.isDirectory()) {
                count += countFilesInDirectory(file);
            }
        }
        return count;
    }


}