package org.kendar.sync.lib.protocol;

/**
 * Message sent by the client to connect to the server.
 * Contains authentication information and the target folder.
 */
public class ConnectMessage extends Message {
    private String username;
    private String password;
    private String targetFolder;
    private int maxPacketSize;
    private int maxConnections;
    private BackupType backupType;
    
    // Default constructor for Jackson
    public ConnectMessage() {
    }
    
    /**
     * Creates a new connect message.
     *
     * @param username The username for authentication
     * @param password The password for authentication
     * @param targetFolder The virtual target folder name
     * @param maxPacketSize The maximum packet size supported by the client
     * @param maxConnections The maximum number of parallel connections supported by the client
     * @param backupType The type of backup operation
     */
    public ConnectMessage(String username, String password, String targetFolder, 
                          int maxPacketSize, int maxConnections, BackupType backupType) {
        this.username = username;
        this.password = password;
        this.targetFolder = targetFolder;
        this.maxPacketSize = maxPacketSize;
        this.maxConnections = maxConnections;
        this.backupType = backupType;
    }
    
    @Override
    public MessageType getMessageType() {
        return MessageType.CONNECT;
    }
    
    // Getters and setters
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
    
    public String getTargetFolder() {
        return targetFolder;
    }
    
    public void setTargetFolder(String targetFolder) {
        this.targetFolder = targetFolder;
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
    
    public BackupType getBackupType() {
        return backupType;
    }
    
    public void setBackupType(BackupType backupType) {
        this.backupType = backupType;
    }
}