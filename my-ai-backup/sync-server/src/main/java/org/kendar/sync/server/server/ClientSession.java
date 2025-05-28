package org.kendar.sync.server.server;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.BackupType;

import java.util.*;

/**
 * Represents a client session.
 */
public class ClientSession {
    private final UUID sessionId;
    private final ServerSettings.User user;
    private final ServerSettings.BackupFolder folder;
    private final BackupType backupType;
    private final boolean dryRun;
    private final Map<Integer, FileInfo> currentFileTransfers = new HashMap<>();
    private boolean isBackup = false;
    private TcpConnection mainConnection;
    private Set<TcpConnection> connections = new HashSet<>();

    public ClientSession(UUID sessionId, ServerSettings.User user, ServerSettings.BackupFolder folder,
                         BackupType backupType, boolean dryRun) {
        this.sessionId = sessionId;
        this.user = user;
        this.folder = folder;
        this.backupType = backupType;
        this.dryRun = dryRun;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public ServerSettings.User getUser() {
        return user;
    }

    public ServerSettings.BackupFolder getFolder() {
        return folder;
    }

    public BackupType getBackupType() {
        return backupType;
    }

    public boolean isDryRun() {
        return dryRun;
    }

    /**
     * Stores the current file being transferred for a specific connection.
     *
     * @param connectionId The connection ID
     * @param fileInfo The file info
     */
    public void setCurrentFile(int connectionId, FileInfo fileInfo) {
        currentFileTransfers.put(connectionId, fileInfo);
    }

    /**
     * Gets the current file being transferred for a specific connection.
     *
     * @param connectionId The connection ID
     * @return The file info, or null if no file is being transferred
     */
    public FileInfo getCurrentFile(int connectionId) {
        return currentFileTransfers.get(connectionId);
    }

    /**
     * Removes the current file being transferred for a specific connection.
     *
     * @param connectionId The connection ID
     */
    public void clearCurrentFile(int connectionId) {
        currentFileTransfers.remove(connectionId);
    }

    /**
     * Sets whether this session is in backup mode.
     *
     * @param isBackup True if in backup mode, false if in restore mode
     */
    public void setBackup(boolean isBackup) {
        this.isBackup = isBackup;
    }

    /**
     * Checks if this session is in backup mode.
     *
     * @return True if in backup mode, false if in restore mode
     */
    public boolean isBackup() {
        return isBackup;
    }

    public void setMainConnection(TcpConnection mainConnection) {
        this.mainConnection = mainConnection;
    }

    public TcpConnection getMainConnection() {
        return mainConnection;
    }

    public void setConnection(TcpConnection connection) {
        this.connections.add( connection);
    }

    public void closeConnections() {
        for (TcpConnection connection : connections) {
            try {
                connection.close();
            } catch (Exception e) {
                System.err.println("Error closing connection: " + e.getMessage());
            }
        }
        connections.clear();
    }
}
