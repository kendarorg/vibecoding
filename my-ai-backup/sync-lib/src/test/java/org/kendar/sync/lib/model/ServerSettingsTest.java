package org.kendar.sync.lib.model;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.protocol.BackupType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ServerSettingsTest {

    private ServerSettings serverSettings;
    private Path testRoot;
    private String settingsFilePath;
    private ServerSettings.User testUser;
    private ServerSettings.BackupFolder testFolder;

    @BeforeEach
    void setUp() throws IOException {
        // Create a unique test directory inside target/tests
        String uniqueId = UUID.randomUUID().toString();
        testRoot = Path.of("target", "tests", uniqueId);
        Files.createDirectories(testRoot);

        // Create settings file path
        settingsFilePath = testRoot.resolve("server-settings.json").toString();

        // Create test user
        testUser = new ServerSettings.User(
                "user1",
                "testuser",
                "password123",
                false
        );

        // Create test backup folder
        testFolder = new ServerSettings.BackupFolder(
                "documents",
                testRoot.resolve("documents").toString(),
                BackupType.MIRROR,
                List.of("user1"),
                true,
                true,
                List.of()
        );

        // Create server settings
        List<ServerSettings.User> users = new ArrayList<>();
        users.add(testUser);

        List<ServerSettings.BackupFolder> folders = new ArrayList<>();
        folders.add(testFolder);

        serverSettings = new ServerSettings(8090, 8089, 1024, 10, users, folders);
    }

    @Test
    void testSaveAndLoad() throws IOException {
        // Save settings to file
        serverSettings.save(settingsFilePath);

        // Verify file exists
        File settingsFile = new File(settingsFilePath);
        assertTrue(settingsFile.exists());

        // Load settings from file
        ServerSettings loadedSettings = ServerSettings.load(settingsFilePath);

        // Verify loaded settings
        assertEquals(serverSettings.getPort(), loadedSettings.getPort());
        assertEquals(serverSettings.getMaxPacketSize(), loadedSettings.getMaxPacketSize());
        assertEquals(serverSettings.getMaxConnections(), loadedSettings.getMaxConnections());
        assertEquals(serverSettings.getUsers().size(), loadedSettings.getUsers().size());
        assertEquals(serverSettings.getBackupFolders().size(), loadedSettings.getBackupFolders().size());

        // Verify user details
        ServerSettings.User loadedUser = loadedSettings.getUsers().get(0);
        assertEquals(testUser.getId(), loadedUser.getId());
        assertEquals(testUser.getUsername(), loadedUser.getUsername());
        assertEquals(testUser.getPassword(), loadedUser.getPassword());
        assertEquals(testUser.isAdmin(), loadedUser.isAdmin());

        // Verify folder details
        ServerSettings.BackupFolder loadedFolder = loadedSettings.getBackupFolders().get(0);
        assertEquals(testFolder.getVirtualName(), loadedFolder.getVirtualName());
        assertEquals(testFolder.getRealPath(), loadedFolder.getRealPath());
        assertEquals(testFolder.getBackupType(), loadedFolder.getBackupType());
        assertEquals(testFolder.getAllowedUsers().size(), loadedFolder.getAllowedUsers().size());
        assertEquals(testFolder.getAllowedUsers().get(0), loadedFolder.getAllowedUsers().get(0));
    }

    @Test
    void testAuthenticate() {
        // Test successful authentication
        Optional<ServerSettings.User> authenticatedUser = serverSettings.authenticate("testuser", "password123");
        assertTrue(authenticatedUser.isPresent());
        assertEquals(testUser.getId(), authenticatedUser.get().getId());

        // Test failed authentication - wrong password
        Optional<ServerSettings.User> wrongPassword = serverSettings.authenticate("testuser", "wrongpassword");
        assertFalse(wrongPassword.isPresent());

        // Test failed authentication - user not found
        Optional<ServerSettings.User> userNotFound = serverSettings.authenticate("nonexistentuser", "password123");
        assertFalse(userNotFound.isPresent());
    }

    @Test
    void testGetUserFolder() {
        // Test getting a folder that the user has access to
        Optional<ServerSettings.BackupFolder> userFolder = serverSettings.getUserFolder("user1", "documents");
        assertTrue(userFolder.isPresent());
        assertEquals(testFolder.getVirtualName(), userFolder.get().getVirtualName());

        // Test getting a folder that doesn't exist
        Optional<ServerSettings.BackupFolder> nonexistentFolder = serverSettings.getUserFolder("user1", "nonexistent");
        assertFalse(nonexistentFolder.isPresent());

        // Test getting a folder that the user doesn't have access to
        // First, create a folder that user1 doesn't have access to
        ServerSettings.BackupFolder restrictedFolder = new ServerSettings.BackupFolder(
                "restricted",
                testRoot.resolve("restricted").toString(),
                BackupType.MIRROR,
                List.of("user2"),
                true,
                true,
                List.of()
        );
        serverSettings.getBackupFolders().add(restrictedFolder);

        Optional<ServerSettings.BackupFolder> noAccess = serverSettings.getUserFolder("user1", "restricted");
        assertFalse(noAccess.isPresent());
    }

    @Test
    void testGettersAndSetters() {
        // Test getters
        assertEquals(8090, serverSettings.getPort());
        assertEquals(1024, serverSettings.getMaxPacketSize());
        assertEquals(10, serverSettings.getMaxConnections());
        assertEquals(1, serverSettings.getUsers().size());
        assertEquals(1, serverSettings.getBackupFolders().size());

        // Test setters
        serverSettings.setPort(9090);
        serverSettings.setMaxPacketSize(2048);
        serverSettings.setMaxConnections(20);

        List<ServerSettings.User> newUsers = new ArrayList<>();
        newUsers.add(new ServerSettings.User("user2", "newuser", "newpass", true));
        serverSettings.setUsers(newUsers);

        List<ServerSettings.BackupFolder> newFolders = new ArrayList<>();
        newFolders.add(new ServerSettings.BackupFolder("newfolder", "/new/path", BackupType.DATE_SEPARATED, List.of("user2"),
                true,
                true,
                List.of()));
        serverSettings.setBackupFolders(newFolders);

        assertEquals(9090, serverSettings.getPort());
        assertEquals(2048, serverSettings.getMaxPacketSize());
        assertEquals(20, serverSettings.getMaxConnections());
        assertEquals(1, serverSettings.getUsers().size());
        assertEquals("user2", serverSettings.getUsers().get(0).getId());
        assertEquals(1, serverSettings.getBackupFolders().size());
        assertEquals("newfolder", serverSettings.getBackupFolders().get(0).getVirtualName());
    }

    @Test
    void testUserGettersAndSetters() {
        ServerSettings.User user = new ServerSettings.User();

        user.setId("testId");
        user.setUsername("testUsername");
        user.setPassword("testPassword");
        user.setAdmin(true);

        assertEquals("testId", user.getId());
        assertEquals("testUsername", user.getUsername());
        assertEquals("testPassword", user.getPassword());
        assertTrue(user.isAdmin());
    }

    @Test
    void testBackupFolderGettersAndSetters() {
        ServerSettings.BackupFolder folder = new ServerSettings.BackupFolder();

        folder.setVirtualName("testVirtualName");
        folder.setRealPath("testRealPath");
        folder.setBackupType(BackupType.PRESERVE);
        List<String> allowedUsers = Arrays.asList("user1", "user2");
        folder.setAllowedUsers(allowedUsers);

        assertEquals("testVirtualName", folder.getVirtualName());
        assertEquals("testRealPath", folder.getRealPath());
        assertEquals(BackupType.PRESERVE, folder.getBackupType());
        assertEquals(2, folder.getAllowedUsers().size());
        assertEquals("user1", folder.getAllowedUsers().get(0));
        assertEquals("user2", folder.getAllowedUsers().get(1));
    }

    @Test
    void testLoadNonExistentFile() throws IOException {
        // Test loading from a non-existent file
        String nonExistentPath = testRoot.resolve("nonexistent.json").toString();
        ServerSettings defaultSettings = ServerSettings.load(nonExistentPath);

        // Verify default settings are returned
        assertNotNull(defaultSettings);
        assertEquals(8090, defaultSettings.getPort()); // Default port
        assertEquals(1, defaultSettings.getUsers().size());
        assertEquals(0, defaultSettings.getBackupFolders().size());
    }
}
