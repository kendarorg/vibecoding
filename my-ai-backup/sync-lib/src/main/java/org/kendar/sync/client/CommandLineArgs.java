package org.kendar.sync.client;

import org.kendar.sync.lib.protocol.BackupType;

import java.util.List;

/**
 * Class to hold command line arguments.
 */
public class CommandLineArgs {
    private static final int DEFAULT_PORT = 8090;
    private String sourceFolder;
    private String targetFolder;
    private boolean backup = true;
    private String serverAddress;
    private int serverPort = DEFAULT_PORT;
    private String username;
    private String password;
    private boolean dryRun = false;
    private BackupType backupType = BackupType.PRESERVE;
    private boolean help = false;
    private int maxConnections = 0;
    private int maxSize;
    private String hostName;
    private boolean ignoreSystemFiles = true;
    private boolean ignoreHiddenFiles = true;
    private List<String> ignoredPatterns = List.of();

    public boolean isIgnoreHiddenFiles() {
        return ignoreHiddenFiles;
    }

    public void setIgnoreHiddenFiles(boolean ignoreHiddenFiles) {
        this.ignoreHiddenFiles = ignoreHiddenFiles;
    }

    public boolean isIgnoreSystemFiles() {
        return ignoreSystemFiles;
    }

    public void setIgnoreSystemFiles(boolean ignoreSystemFiles) {
        this.ignoreSystemFiles = ignoreSystemFiles;
    }

    public String getSourceFolder() {
        return sourceFolder;
    }

    public void setSourceFolder(String sourceFolder) {
        this.sourceFolder = sourceFolder;
    }

    public String getTargetFolder() {
        return targetFolder;
    }

    public void setTargetFolder(String targetFolder) {
        this.targetFolder = targetFolder;
    }

    public boolean isBackup() {
        return backup;
    }

    public void setBackup(boolean backup) {
        this.backup = backup;
    }

    public String getServerAddress() {
        return serverAddress;
    }

    public void setServerAddress(String serverAddress) {
        this.serverAddress = serverAddress;
    }

    public int getServerPort() {
        return serverPort;
    }

    public void setServerPort(int serverPort) {
        this.serverPort = serverPort;
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

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }

    public BackupType getBackupType() {
        return backupType;
    }

    public void setBackupType(BackupType backupType) {
        this.backupType = backupType;
    }

    public boolean isHelp() {
        return help;
    }

    public void setHelp(boolean help) {
        this.help = help;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public int getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public List<String> getIgnoredPatterns() {
        return ignoredPatterns;
    }

    public void setIgnoredPatterns(List<String> ignoredPatterns) {
        this.ignoredPatterns = ignoredPatterns;
    }
}