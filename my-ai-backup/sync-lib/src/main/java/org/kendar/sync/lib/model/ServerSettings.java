package org.kendar.sync.lib.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.kendar.sync.lib.protocol.BackupType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Represents the server settings.
 */
public class ServerSettings {
    private int port;
    private int webPort;
    private int maxPacketSize;
    private int maxConnections;
    private List<User> users;
    private List<BackupFolder> backupFolders;

    // Default constructor for Jackson
    public ServerSettings() {
        this.users = new ArrayList<>();
        this.backupFolders = new ArrayList<>();
    }

    /**
     * Creates a new server settings object.
     *
     * @param port           The listening port of the server
     * @param maxPacketSize  The maximum packet size
     * @param maxConnections The maximum parallel TCP connections for a single session
     * @param users          The list of users
     * @param backupFolders  The list of backup folders
     */
    public ServerSettings(int port, int webPort, int maxPacketSize, int maxConnections, List<User> users, List<BackupFolder> backupFolders) {
        this.port = port;
        this.webPort = webPort;
        this.maxPacketSize = maxPacketSize;
        this.maxConnections = maxConnections;
        this.users = users;
        this.backupFolders = backupFolders;
    }

    /**
     * Loads the server settings from a file.
     *
     * @param filePath The path to the settings file
     * @return The server settings
     * @throws IOException If an I/O error occurs
     */
    public static ServerSettings load(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Path path = Paths.get(filePath);

        if (!Files.exists(path)) {
            // Create default settings
            ServerSettings settings = new ServerSettings();
            settings.setPort(8090);
            settings.setWebPort(8089);
            settings.setMaxPacketSize(1024 * 1024); // 1 MB
            settings.setMaxConnections(5);

            // Create admin user
            User adminUser = new User(UUID.randomUUID().toString(), "admin", "admin", true);
            settings.getUsers().add(adminUser);

            // Save default settings
            settings.save(filePath);

            return settings;
        }

        return mapper.readValue(path.toFile(), ServerSettings.class);
    }

    /**
     * Saves the server settings to a file.
     *
     * @param filePath The path to the settings file
     * @throws IOException If an I/O error occurs
     */
    public void save(String filePath) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        File file = new File(filePath);

        // Create parent directories if they don't exist
        if (file.getParentFile() != null) {
            if (!file.getParentFile().mkdirs()) {
                throw new IOException("Failed to create directory 5: " + file.getParentFile().getAbsolutePath());
            }
        }

        mapper.writerWithDefaultPrettyPrinter().writeValue(file, this);
    }

    /**
     * Authenticates a user.
     *
     * @param username The username
     * @param password The password
     * @return The user if authentication is successful, or empty if not
     */
    @JsonIgnore
    public Optional<User> authenticate(String username, String password) {
        return users.stream()
                .filter(user -> user.getUsername().equals(username) && user.getPassword().equals(password))
                .findFirst();
    }

    /**
     * Checks if a user has access to a backup folder.
     *
     * @param userId     The user ID
     * @param folderName The virtual folder name
     * @return The backup folder if the user has access, or empty if not
     */
    @JsonIgnore
    public Optional<BackupFolder> getUserFolder(String userId, String folderName) {
        return backupFolders.stream()
                .filter(folder -> folder.getVirtualName().equals(folderName) && folder.getAllowedUsers().contains(userId))
                .findFirst();
    }

    // Getters and setters
    public int getWebPort() {
        return webPort;
    }

    public void setWebPort(int webPort) {
        this.webPort = webPort;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public void setMaxPacketSize(int maxPacketSize) {
        this.maxPacketSize = maxPacketSize;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public List<BackupFolder> getBackupFolders() {
        return backupFolders;
    }

    public void setBackupFolders(List<BackupFolder> backupFolders) {
        this.backupFolders = backupFolders;
    }

    /**
     * Represents a user in the system.
     */
    public static class User {
        private String id;
        private String username;
        private String password;
        private boolean admin;

        // Default constructor for Jackson
        public User() {
        }

        /**
         * Creates a new user.
         *
         * @param id       The user ID
         * @param username The username
         * @param password The password
         * @param admin    Whether the user is an admin
         */
        public User(String id, String username, String password, boolean admin) {
            this.id = id;
            this.username = username;
            this.password = password;
            this.admin = admin;
        }

        // Getters and setters
        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public boolean isAdmin() {
            return admin;
        }

        public void setAdmin(boolean admin) {
            this.admin = admin;
        }
    }

    /**
     * Represents a backup folder.
     */
    public static class BackupFolder {
        private String virtualName;
        private String realPath;
        private BackupType backupType;
        private List<String> allowedUsers;

        // Default constructor for Jackson
        public BackupFolder() {
            this.allowedUsers = new ArrayList<>();
        }

        /**
         * Creates a new backup folder.
         *
         * @param virtualName  The virtual name of the folder
         * @param realPath     The real path of the folder
         * @param backupType   The type of backup
         * @param allowedUsers The list of user IDs allowed to access the folder
         */
        public BackupFolder(String virtualName, String realPath, BackupType backupType, List<String> allowedUsers) {
            this.virtualName = virtualName;
            this.realPath = realPath;
            this.backupType = backupType;
            this.allowedUsers = allowedUsers;
        }

        // Getters and setters
        public String getVirtualName() {
            return virtualName;
        }

        public void setVirtualName(String virtualName) {
            this.virtualName = virtualName;
        }

        public String getRealPath() {
            return realPath;
        }

        public void setRealPath(String realPath) {
            this.realPath = realPath;
        }

        public BackupType getBackupType() {
            return backupType;
        }

        public void setBackupType(BackupType backupType) {
            this.backupType = backupType;
        }

        public List<String> getAllowedUsers() {
            return allowedUsers;
        }

        public void setAllowedUsers(List<String> allowedUsers) {
            this.allowedUsers = allowedUsers;
        }
    }
}