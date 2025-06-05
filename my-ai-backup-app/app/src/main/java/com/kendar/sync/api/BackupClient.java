package com.kendar.sync.api;

import com.kendar.sync.model.BackupJob;

/**
 * Interface for the backup process implementation
 * This will be implemented by someone else as per requirements
 */
public interface BackupClient {
    /**
     * Starts the backup process for the specified job
     * @param job The backup job to run
     * @param listener A listener to receive backup progress updates
     * @return true if backup started successfully, false otherwise
     */
    boolean startBackup(BackupJob job, BackupProgressListener listener);

    /**
     * Stops an ongoing backup process
     * @return true if backup was stopped, false if there was no backup running
     */
    boolean stopBackup();

    /**
     * Fetches available remote targets from the server
     * @param serverAddress The server address
     * @param port The server port
     * @param login The login username
     * @param password The login password
     * @param listener Listener to receive the results
     */
    void fetchRemoteTargets(String serverAddress, int port, String login, String password, RemoteTargetsListener listener);

    /**
     * Interface for backup progress updates
     */
    interface BackupProgressListener {
        void onBackupStarted();
        void onBackupProgress(int progress, String currentFile);
        void onBackupCompleted(boolean success, String message);
        void onBackupError(String errorMessage);
    }

    /**
     * Interface for receiving remote targets
     */
    interface RemoteTargetsListener {
        void onTargetsReceived(String[] targets);
        void onTargetsFetchFailed(String errorMessage);
    }
}
