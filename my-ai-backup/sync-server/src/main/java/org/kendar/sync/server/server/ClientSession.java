package org.kendar.sync.server.server;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.BackupType;
import org.kendar.sync.lib.utils.Sleeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents a client session.
 */
public class ClientSession {
    private static final Logger log = LoggerFactory.getLogger(ClientSession.class);
    private final UUID sessionId;
    private final ServerSettings.User user;
    private final ServerSettings.BackupFolder folder;
    private final BackupType backupType;
    private final boolean dryRun;
    private final int secondsTimeout;
    private final Map<Integer, FileInfo> currentFileTransfers = new HashMap<>();
    private final Set<TcpConnection> connections = new HashSet<>();
    private final AtomicLong lastOperationTimestamp = new AtomicLong(0);
    private boolean isBackup = false;
    private TcpConnection mainConnection;
    private boolean ignoreHiddenFiles;
    private boolean ignoreSystemFiles;
    private HashSet<String> ignoredPatterns;

    public ClientSession(UUID sessionId, ServerSettings.User user, ServerSettings.BackupFolder folder,
                         BackupType backupType, boolean dryRun,
                         int secondsTimeout) {
        this.sessionId = sessionId;
        this.user = user;
        this.folder = folder;
        this.backupType = backupType;
        this.dryRun = dryRun;
        this.secondsTimeout = secondsTimeout;
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
     * @param fileInfo     The file info
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
     * Checks if this session is in backup mode.
     *
     * @return True if in backup mode, false if in restore mode
     */
    public boolean isBackup() {
        return isBackup;
    }

    /**
     * Sets whether this session is in backup mode.
     *
     * @param isBackup True if in backup mode, false if in restore mode
     */
    public void setBackup(boolean isBackup) {
        this.isBackup = isBackup;
    }

    public TcpConnection getMainConnection() {
        return mainConnection;
    }

    public void setMainConnection(TcpConnection mainConnection) {
        this.mainConnection = mainConnection;
    }

    public void setConnection(TcpConnection connection) {
        this.connections.add(connection);
    }

    public void closeConnections() {
        for (TcpConnection connection : connections) {
            try {
                connection.close();
            } catch (Exception e) {
                log.error("Error closing connection 1: {}", e.getMessage());
            }
        }
        connections.clear();
    }

    public void closeChildConnections() {
        var lst = new ArrayList<>(connections);
        for (TcpConnection connection : lst) {
            try {
                if (connection.getConnectionId() != 0) {
                    connection.close();
                    connections.remove(connection);
                }
            } catch (Exception e) {
                log.error("Error closing connection 2: {}", e.getMessage());
            }
        }
        Sleeper.sleep(200);
    }

    public Set<TcpConnection> getConnections() {
        return connections;
    }

    /**
     * Updates the last operation timestamp to the current time plus the specified timeout.
     */
    public void touch() {
        lastOperationTimestamp.set(System.currentTimeMillis() +
                (long) secondsTimeout * 1000L);
    }

    /**
     * Checks if the session has expired based on the last operation timestamp.
     *
     * @return true if the current time is greater than or equal to the last operation timestamp,
     * false otherwise
     */
    public boolean isExpired() {
        return System.currentTimeMillis() >= lastOperationTimestamp.get(); //TODO
    }


    public void setIgnoreHiddenFiles(boolean ignoreHiddenFiles) {
        this.ignoreHiddenFiles = ignoreHiddenFiles;
    }

    public boolean isIgnoreHiddenFiles() {
        return ignoreHiddenFiles;
    }

    public void setIgnoreSystemFiles(boolean ignoreSystemFiles) {
        this.ignoreSystemFiles = ignoreSystemFiles;
    }

    public boolean isIgnoreSystemFiles() {
        return ignoreSystemFiles;
    }

    public void setIgnoredPatterns(HashSet<String> ignoredPatterns) {
        this.ignoredPatterns = ignoredPatterns;
    }

    public HashSet<String> getIgnoredPatterns() {
        return ignoredPatterns;
    }
}
