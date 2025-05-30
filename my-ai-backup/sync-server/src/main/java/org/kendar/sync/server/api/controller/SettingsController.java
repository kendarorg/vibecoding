package org.kendar.sync.server.api.controller;

import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.server.config.ServerConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller for server settings.
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {
    private final ServerConfig serverConfig;
    private ServerSettings serverSettings;
    public SettingsController(ServerConfig serverConfig, ServerSettings serverSettings) {
        this.serverConfig = serverConfig;
        this.serverSettings = serverSettings;
    }

    /**
     * Gets the server settings.
     *
     * @return The server settings
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServerSettings> getSettings() {
        return ResponseEntity.ok(serverSettings);
    }

    /**
     * Updates the server settings.
     *
     * @param settings The updated server settings
     * @return The updated server settings
     * @throws IOException If an I/O error occurs
     */
    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServerSettings> updateSettings(@RequestBody ServerSettings settings) throws IOException {
        settings.save(serverConfig.getSettingsFile());
        serverSettings = serverConfig.reloadSettings();
        return ResponseEntity.ok(serverSettings);
    }

    /**
     * Gets all users.
     *
     * @return The list of users
     */
    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ServerSettings.User>> getUsers() {
        return ResponseEntity.ok(serverSettings.getUsers());
    }

    /**
     * Gets a user by ID.
     *
     * @param id The user ID
     * @return The user
     */
    @GetMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == #id")
    public ResponseEntity<?> getUser(@PathVariable String id) {
        Optional<ServerSettings.User> user = serverSettings.getUsers().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst();

        if (user.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(user.get());
    }

    /**
     * Creates a new user.
     *
     * @param user The user to create
     * @return The created user
     * @throws IOException If an I/O error occurs
     */
    @PostMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServerSettings.User> createUser(@RequestBody ServerSettings.User user) throws IOException {
        // Generate a new ID if not provided
        if (user.getId() == null || user.getId().isEmpty()) {
            user.setId(UUID.randomUUID().toString());
        }

        // Check if the username already exists
        boolean usernameExists = serverSettings.getUsers().stream()
                .anyMatch(u -> u.getUsername().equals(user.getUsername()));

        if (usernameExists) {
            return ResponseEntity.badRequest().build();
        }

        serverSettings.getUsers().add(user);
        serverSettings.save(serverConfig.getSettingsFile());

        return ResponseEntity.ok(user);
    }

    /**
     * Updates a user.
     *
     * @param id   The user ID
     * @param user The updated user
     * @return The updated user
     * @throws IOException If an I/O error occurs
     */
    @PutMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN') or authentication.name == #id")
    public ResponseEntity<?> updateUser(@PathVariable String id, @RequestBody ServerSettings.User user) throws IOException {
        // Find the user
        Optional<ServerSettings.User> existingUser = serverSettings.getUsers().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst();

        if (existingUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Update the user
        ServerSettings.User userToUpdate = existingUser.get();

        // Only admins can change the admin status
        if (!userToUpdate.isAdmin() && user.isAdmin()) {
            return ResponseEntity.status(403).build();
        }

        userToUpdate.setUsername(user.getUsername());
        userToUpdate.setPassword(user.getPassword());

        if (userToUpdate.isAdmin()) {
            userToUpdate.setAdmin(user.isAdmin());
        }

        serverSettings.save(serverConfig.getSettingsFile());

        return ResponseEntity.ok(userToUpdate);
    }

    /**
     * Deletes a user.
     *
     * @param id The user ID
     * @return No content
     * @throws IOException If an I/O error occurs
     */
    @DeleteMapping("/users/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable String id) throws IOException {
        // Find the user
        Optional<ServerSettings.User> existingUser = serverSettings.getUsers().stream()
                .filter(u -> u.getId().equals(id))
                .findFirst();

        if (existingUser.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Remove the user
        serverSettings.getUsers().remove(existingUser.get());

        // Remove the user from all backup folders
        for (ServerSettings.BackupFolder folder : serverSettings.getBackupFolders()) {
            folder.getAllowedUsers().remove(id);
        }

        serverSettings.save(serverConfig.getSettingsFile());

        return ResponseEntity.noContent().build();
    }

    /**
     * Gets all backup folders.
     *
     * @return The list of backup folders
     */
    @GetMapping("/folders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ServerSettings.BackupFolder>> getFolders() {
        return ResponseEntity.ok(serverSettings.getBackupFolders());
    }

    /**
     * Gets a backup folder by name.
     *
     * @param name The folder name
     * @return The backup folder
     */
    @GetMapping("/folders/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getFolder(@PathVariable String name) {
        Optional<ServerSettings.BackupFolder> folder = serverSettings.getBackupFolders().stream()
                .filter(f -> f.getVirtualName().equals(name))
                .findFirst();

        if (folder.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(folder.get());
    }

    /**
     * Creates a new backup folder.
     *
     * @param folder The folder to create
     * @return The created folder
     * @throws IOException If an I/O error occurs
     */
    @PostMapping("/folders")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServerSettings.BackupFolder> createFolder(@RequestBody ServerSettings.BackupFolder folder) throws IOException {
        // Check if the folder name already exists
        boolean folderExists = serverSettings.getBackupFolders().stream()
                .anyMatch(f -> f.getVirtualName().equals(folder.getVirtualName()));

        if (folderExists) {
            return ResponseEntity.badRequest().build();
        }

        serverSettings.getBackupFolders().add(folder);
        serverSettings.save(serverConfig.getSettingsFile());

        return ResponseEntity.ok(folder);
    }

    /**
     * Updates a backup folder.
     *
     * @param name   The folder name
     * @param folder The updated folder
     * @return The updated folder
     * @throws IOException If an I/O error occurs
     */
    @PutMapping("/folders/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateFolder(@PathVariable String name, @RequestBody ServerSettings.BackupFolder folder) throws IOException {
        // Find the folder
        Optional<ServerSettings.BackupFolder> existingFolder = serverSettings.getBackupFolders().stream()
                .filter(f -> f.getVirtualName().equals(name))
                .findFirst();

        if (existingFolder.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Update the folder
        ServerSettings.BackupFolder folderToUpdate = existingFolder.get();
        folderToUpdate.setVirtualName(folder.getVirtualName());
        folderToUpdate.setRealPath(folder.getRealPath());
        folderToUpdate.setBackupType(folder.getBackupType());
        folderToUpdate.setAllowedUsers(folder.getAllowedUsers());

        serverSettings.save(serverConfig.getSettingsFile());

        return ResponseEntity.ok(folderToUpdate);
    }

    /**
     * Deletes a backup folder.
     *
     * @param name The folder name
     * @return No content
     * @throws IOException If an I/O error occurs
     */
    @DeleteMapping("/folders/{name}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteFolder(@PathVariable String name) throws IOException {
        // Find the folder
        Optional<ServerSettings.BackupFolder> existingFolder = serverSettings.getBackupFolders().stream()
                .filter(f -> f.getVirtualName().equals(name))
                .findFirst();

        if (existingFolder.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        // Remove the folder
        serverSettings.getBackupFolders().remove(existingFolder.get());
        serverSettings.save(serverConfig.getSettingsFile());

        return ResponseEntity.noContent().build();
    }

    /**
     * Updates the server port.
     *
     * @param port The new port
     * @return The updated server settings
     * @throws IOException If an I/O error occurs
     */
    @PutMapping("/port")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServerSettings> updatePort(@RequestBody int port) throws IOException {
        serverSettings.setPort(port);
        serverSettings.save(serverConfig.getSettingsFile());

        return ResponseEntity.ok(serverSettings);
    }

    /**
     * Updates the maximum packet size.
     *
     * @param maxPacketSize The new maximum packet size
     * @return The updated server settings
     * @throws IOException If an I/O error occurs
     */
    @PutMapping("/maxPacketSize")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServerSettings> updateMaxPacketSize(@RequestBody int maxPacketSize) throws IOException {
        serverSettings.setMaxPacketSize(maxPacketSize);
        serverSettings.save(serverConfig.getSettingsFile());

        return ResponseEntity.ok(serverSettings);
    }

    /**
     * Updates the maximum number of connections.
     *
     * @param maxConnections The new maximum number of connections
     * @return The updated server settings
     * @throws IOException If an I/O error occurs
     */
    @PutMapping("/maxConnections")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ServerSettings> updateMaxConnections(@RequestBody int maxConnections) throws IOException {
        serverSettings.setMaxConnections(maxConnections);
        serverSettings.save(serverConfig.getSettingsFile());

        return ResponseEntity.ok(serverSettings);
    }
}