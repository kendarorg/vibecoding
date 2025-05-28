package org.kendar.sync.server.server;

import org.kendar.sync.lib.model.ServerSettings;
import org.kendar.sync.lib.protocol.BackupType;

import java.util.UUID;

/**
 * Represents a client session.
 */
public class ClientSession {
    private final UUID sessionId;
    private final ServerSettings.User user;
    private final ServerSettings.BackupFolder folder;
    private final BackupType backupType;
    private final boolean dryRun;

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
}
