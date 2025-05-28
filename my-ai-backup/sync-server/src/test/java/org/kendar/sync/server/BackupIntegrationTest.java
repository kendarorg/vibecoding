package org.kendar.sync.server;

import org.junit.jupiter.api.*;
import org.kendar.sync.client.CommandLineArgs;
import org.kendar.sync.client.SyncClientApp;
import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.protocol.BackupType;
import org.kendar.sync.lib.utils.FileUtils;
import org.kendar.sync.server.config.ServerConfig;
import org.kendar.sync.server.server.Server;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.regex.Pattern;
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
    private File removedFile;
    private ServerConfig serverConfig;
    private Server server;
    private int serverPort;
    private CommandLineArgs commandLineArgs;
    private static String uniqueId;

    @AfterEach
    void tearDown() throws Exception {
        server.stop();
    }

    @AfterAll
    public static void cleanup() throws Exception {
        FileUtils.deleteDirectoryContents(Path.of("target", "tests",  uniqueId));
    }

    @BeforeAll
    public static void beforeClass() {
        uniqueId = UUID.randomUUID().toString();
    }

    @BeforeEach
    void setUp(TestInfo testInfo) throws Exception {
        testRoot = Path.of("target", "tests",  uniqueId,TestUtils.getTestFolder(testInfo));
        Files.createDirectories(testRoot);

        // Create source and target directories
        sourceDir = new File(testRoot.toFile(), "source");
        targetDir = new File(testRoot.toFile(), "target");
        sourceDir.mkdir();
        targetDir.mkdir();

        // Create test files in the source directory with random content

        
        System.out.println("================= Created test files in " + sourceDir.getAbsolutePath());

        serverPort = 8085;

    }

    private void startServer(BackupType backupType) throws InterruptedException {
        serverConfig = new ServerConfig();
        var serverSettings = new ServerSettings();
        serverSettings.setPort(serverPort);
        serverSettings.setMaxConnections(5);
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






    @Test
    void testBackupAndRestoreDateSeparated() throws Exception {

        startServer(BackupType.DATE_SEPARATED);
        createRandomFiles(sourceDir, 5, 3);

        // Perform backup
        System.out.println("================= Performing backup...");
        commandLineArgs.setBackup(true);
        SyncClientApp.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath(),BackupType.DATE_SEPARATED);

        //Add a non deletable file tot destination
        System.out.println("================= Adding new file on target...");
        Files.writeString(Path.of(targetDir.toPath()+"/atest.txt"), "testBackup");

        // Perform backup
        System.out.println("================= Performing backup...");
        commandLineArgs.setBackup(true);
        SyncClientApp.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup...");
        List<String> diff = new ArrayList<>();
        assertFalse(areDirectoriesEqual(sourceDir.toPath(), targetDir.toPath(),diff,BackupType.DATE_SEPARATED));
        assertEquals(List.of(sourceDir.toPath()+File.separator+"atest.txt"), diff);


        // Remove a file from the source directory
        removedFile = removeRandomFile(sourceDir.toPath());

        // Perform restore
        System.out.println("================= Performing restore...");
        commandLineArgs.setBackup(false);
        SyncClientApp.doSync(commandLineArgs);
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath(),BackupType.DATE_SEPARATED);

    }

    @Test
    void testBackupAndRestorePreserve() throws Exception {

        startServer(BackupType.PRESERVE);
        createRandomFiles(sourceDir, 5, 3);

        // Perform backup
        System.out.println("================= Performing backup...");
        commandLineArgs.setBackup(true);
        SyncClientApp.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        //Add a non deletable file tot destination
        System.out.println("================= ADd file on target...");
        Files.writeString(Path.of(targetDir.toPath()+"/atest.txt"), "testBackup");

        // Perform backup
        System.out.println("================= Performing backup...");
        commandLineArgs.setBackup(true);
        SyncClientApp.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup...");
        List<String> diff = new ArrayList<>();
        assertFalse(areDirectoriesEqual(sourceDir.toPath(), targetDir.toPath(),diff));
        assertEquals(List.of(sourceDir.toPath()+File.separator+"atest.txt"), diff);


        // Remove a file from the source directory
        removedFile = removeRandomFile(sourceDir.toPath());

        // Perform restore
        System.out.println("================= Performing restore...");
        commandLineArgs.setBackup(false);
        SyncClientApp.doSync(commandLineArgs);
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

    }


    @Test
    void testBackupAndRestoreMirror() throws Exception {

        startServer(BackupType.MIRROR);
        createRandomFiles(sourceDir, 5, 3);

        // Perform backup
        System.out.println("================= Performing backup...");
        commandLineArgs.setBackup(true);
        SyncClientApp.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        // Remove a file from the source directory
        removedFile = removeRandomFile(sourceDir.toPath());

        // Perform backup with deleted file
        SyncClientApp.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup with deleted...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        // Remove a file from the source directory
        removedFile = removeRandomFile(targetDir.toPath());

        // Perform backup with deleted file
        SyncClientApp.doSync(commandLineArgs);

        // Verify backup
        System.out.println("================= Verifying backup with deleted target...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

        // Delete a file from the target directory
        removedFile = removeRandomFile(targetDir.toPath());

        System.out.println("================= Adding file on target...");
        Files.writeString(Path.of(targetDir.toPath()+"/atest.txt"), "testBackup");

        // Perform restore
        System.out.println("================= Performing restore...");
        commandLineArgs.setBackup(false);
        SyncClientApp.doSync(commandLineArgs);

        // Verify restore
        System.out.println("================= Verifying restore...");
        assertDirectoriesEqual(sourceDir.toPath(), targetDir.toPath());

    }

    private List<File> createRandomFiles(File dir, int numFiles, int maxDepth) throws IOException {
        return createRandomFiles(dir,dir,numFiles,maxDepth);
    }

    private List<File> createRandomFiles(File rootDir,File dir, int numFiles, int maxDepth) throws IOException {
        List<File> files = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < numFiles; i++) {
            // Decide whether to create a file or directory
            boolean isDirectory = random.nextBoolean() && maxDepth > 0;
            String name = isDirectory ? "dir_" + i : "file_" + i + ".txt";
            File file = new File(dir, name);

            if (!isDirectory) {

                files.add(file);
                var randomLong =getRandomNumber(228967200000L,1585965600000L);
                String date = new java.text.SimpleDateFormat("yyyy-MM-dd hh:mm:ss").format(new java.util.Date (randomLong));
                // Create a file with random content
                String content = "Content for " + name + ": " + UUID.randomUUID();
                var path = file.toPath();
                var parent = path.getParent().toString();
                if(parent.equalsIgnoreCase(rootDir.toPath().toString())){
                    parent =null;
                }else {
                    parent = parent.replace(rootDir.toPath() + File.separator, "");
                }

//                if(backupType==BackupType.DATE_SEPARATED){
//                    if(parent==null){
//                        Files.createDirectories(Path.of(rootDir.toString(), date));
//                        path = Path.of(rootDir.toString(), date, file.getName());
//                    }else {
//                        Files.createDirectories(Path.of(rootDir.toString(), date, parent));
//                        path = Path.of(rootDir.toString(), date, parent, file.getName());
//                    }
//                }else{
                    if(parent==null){
                        Files.createDirectories(Path.of(rootDir.toString()));
                        path = Path.of(rootDir.toString(), file.getName());
                    }else {
                        Files.createDirectories(Path.of(rootDir.toString(), parent));
                        path = Path.of(rootDir.toString(), parent, file.getName());
                    }
                //}
                Files.writeString(path, content);
                Files.setAttribute(path, "creationTime", FileTime.fromMillis(randomLong));
                Files.setLastModifiedTime(path, FileTime.fromMillis(randomLong+100000L));

            } else {
                // Recursively create files in the directory
                files.addAll(createRandomFiles(rootDir,file, numFiles / 2, maxDepth - 1));
            }
        }

        return files;
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

    private long getRandomNumber(long min, long max) {
        Random random = new Random();
        return random.nextLong(max - min + 1) + min;
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
        Files.delete(fileToRemove.toPath());
        System.out.println("================= Removed file: " + getRelativePath(fileToRemove, sourceDir));
        
        return fileToRemove;
    }

    public static void assertDirectoriesEqual(Path dir1, Path dir2,BackupType backupType) throws IOException {
        if (!areDirectoriesEqual(dir1, dir2,new ArrayList<>(),backupType)) {
            fail("Directories are not equal: " + dir1 + " and " + dir2);
        }
    }

    public static void assertDirectoriesEqual(Path dir1, Path dir2) throws IOException {
        if (!areDirectoriesEqual(dir1, dir2,new ArrayList<>())) {
            fail("Directories are not equal: " + dir1 + " and " + dir2);
        }
    }

    public static boolean areDirectoriesEqual(Path dir1, Path dir2,List<String> different) throws IOException {
        return areDirectoriesEqual(dir1, dir2,different,BackupType.MIRROR);
    }

    public static boolean areDirectoriesEqual(Path dir1, Path dir2,List<String> different,BackupType backupType) throws IOException {
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
        }

        // Compare the content of each file
        for (Path relativePath : dir1Files) {
            Path file1 = dir1.resolve(relativePath);
            Path file2 = dir2.resolve(relativePath);
            if(backupType==BackupType.DATE_SEPARATED){
                if(!Files.exists(file2)){
                    BasicFileAttributes attrs = Files.readAttributes(file1, BasicFileAttributes.class);
                    String date = new java.text.SimpleDateFormat("yyyy-MM-dd").
                            format(new java.util.Date (attrs.creationTime().toMillis()));
                    file2 = dir2.resolve(Path.of(date,relativePath.toString()));
                }
            }

            if(!Files.exists(file2)) {
                different.add(file2.toString());
                continue;
            }

            if (Files.size(file1) != Files.size(file2)) {
                System.err.println("Different size: " + file1+" "+file2);
                different.add(file1.toString());
                continue;
            }

            if (!Arrays.equals(Files.readAllBytes(file1), Files.readAllBytes(file2))) {
                System.err.println("Different content: " + file1+" "+file2);
                different.add(file1.toString());
                continue;
            }
            var attr1 = Files.readAttributes(file1, BasicFileAttributes.class);
            var attr2 = Files.readAttributes(file2, BasicFileAttributes.class);
            if(attr1.lastModifiedTime().toInstant().getEpochSecond()!=
            attr2.lastModifiedTime().toInstant().getEpochSecond()) {
                System.err.println("Different modification: " + file1+" "+file2);
                different.add(file1.toString());
                continue;
            }
            if(attr1.creationTime().toInstant().getEpochSecond()!=
                    attr2.creationTime().toInstant().getEpochSecond()) {
                System.err.println("Different creation: " + file1+" "+file2);
                different.add(file1.toString());
            }
        }

        var isDatePattern = Pattern.compile("^[0-9]{4}-[0-9]{2}-[0-9]{2}.*");

        for (Path relativePath : dir2Files) {
            Path file1 = dir1.resolve(relativePath);

            if(!Files.exists(file1) && backupType==BackupType.DATE_SEPARATED && isDatePattern.matcher(relativePath.toString()).matches()) {
                var rp = relativePath.toString().substring("yyyy-mm-dd".length()+1);
                var finalPath = dir1.toString()+File.separator+rp;
                if (!Files.exists(Path.of(finalPath))) {
                    different.add(finalPath);
                }
            }else {
                if (!Files.exists(file1)) {
                    different.add(file1.toString());
                }
            }
        }

        return different.size()==0;
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