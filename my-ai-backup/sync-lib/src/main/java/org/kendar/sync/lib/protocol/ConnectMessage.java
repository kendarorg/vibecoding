package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message sent by the client to connect to the server.
 * Contains authentication information and the target folder.
 */
public class ConnectMessage extends Message {
    static {
        Message.registerMessageType(ConnectMessage.class);
    }

    private String username;
    private String password;
    private String targetFolder;
    private int maxPacketSize;
    private int maxConnections;
    private boolean dryRun;
    private String hostName;

    // Default constructor for Jackson
    public ConnectMessage() {
    }

    /**
     * Creates a new connection message.
     *
     * @param username       The username for authentication
     * @param password       The password for authentication
     * @param targetFolder   The virtual target folder name
     * @param maxPacketSize  The maximum packet size supported by the client
     * @param maxConnections The maximum number of parallel connections supported by the client
     * @param dryRun         Whether this is a dry run (no actual file operations)
     */
    public ConnectMessage(String username, String password, String targetFolder,
                          int maxPacketSize, int maxConnections,
                          boolean dryRun,String hostName) {
        this.username = username;
        this.password = password;
        this.targetFolder = targetFolder;
        this.maxPacketSize = maxPacketSize;
        this.maxConnections = maxConnections;
        this.dryRun = dryRun;
        this.hostName = hostName;
    }

    @Override
    protected Message deserialize(ByteContainer buffer) {
        username = buffer.readType(String.class);
        password = buffer.readType(String.class);
        targetFolder = buffer.readType(String.class);
        maxPacketSize = buffer.readType(Integer.class);
        maxConnections = buffer.readType(Integer.class);
        dryRun = buffer.readType(Boolean.class);
        hostName = buffer.readType(String.class);
        return this;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.CONNECT;
    }

    @Override
    protected void serialize(ByteContainer buffer) {
        buffer.writeType(username);
        buffer.writeType(password);
        buffer.writeType(targetFolder);
        buffer.writeType(maxPacketSize);
        buffer.writeType(maxConnections);
        buffer.writeType(dryRun);
        buffer.writeType(hostName);
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

    public boolean isDryRun() {
        return dryRun;
    }

    public void setDryRun(boolean dryRun) {
        this.dryRun = dryRun;
    }
}
