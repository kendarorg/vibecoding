package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message sent by the server in response to a connection request.
 * Indicates whether the connection was accepted or rejected.
 */
public class ConnectResponseMessage extends Message {
    static {
        Message.registerMessageType(ConnectResponseMessage.class);
    }

    private boolean accepted;
    private String errorMessage = "";
    private int maxPacketSize;
    private int maxConnections;
    private BackupType backupType;

    // Default constructor for Jackson
    public ConnectResponseMessage() {
    }

    /**
     * Creates a new connection response message.
     *
     * @param accepted       Whether the connection was accepted
     * @param errorMessage   Error message if the connection was rejected
     * @param maxPacketSize  The maximum packet size negotiated for the session
     * @param maxConnections The maximum number of parallel connections negotiated for the session
     * @param backupType     The type of backup requested (e.g., FULL, INCREMENTAL, NONE)
     */
    public ConnectResponseMessage(boolean accepted, String errorMessage,
                                  int maxPacketSize, int maxConnections,
                                  BackupType backupType) {
        this.accepted = accepted;
        this.errorMessage = errorMessage;
        this.maxPacketSize = maxPacketSize;
        this.maxConnections = maxConnections;
        this.backupType = backupType;
    }

    /**
     * Creates a new connection response message for a successful connection.
     *
     * @param maxPacketSize  The maximum packet size negotiated for the session
     * @param maxConnections The maximum number of parallel connections negotiated for the session
     * @return A new connection response message
     */
    public static ConnectResponseMessage accepted(int maxPacketSize, int maxConnections) {
        return new ConnectResponseMessage(true, null, maxPacketSize, maxConnections, BackupType.NONE);
    }

    /**
     * Creates a new connection response message for a rejected connection.
     *
     * @param errorMessage The reason for the rejection
     * @return A new connection response message
     */
    public static ConnectResponseMessage rejected(String errorMessage) {
        return new ConnectResponseMessage(false, errorMessage, 0, 0, BackupType.NONE);
    }

    @Override
    protected Message deserialize(ByteContainer buffer) {
        accepted = buffer.readType(Boolean.class);
        errorMessage = buffer.readType(String.class);
        maxPacketSize = buffer.readType(Integer.class);
        maxConnections = buffer.readType(Integer.class);
        backupType = buffer.readType(BackupType.class);
        return this;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.CONNECT_RESPONSE;
    }

    @Override
    protected void serialize(ByteContainer buffer) {
        buffer.writeType(accepted);
        if (errorMessage == null) buffer.writeType("");
        else buffer.writeType(errorMessage);
        buffer.writeType(maxPacketSize);
        buffer.writeType(maxConnections);
        buffer.writeType(backupType);
    }

    // Getters and setters

    public BackupType getBackupType() {
        return backupType;
    }

    public void setBackupType(BackupType backupType) {
        this.backupType = backupType;
    }

    public boolean isAccepted() {
        return accepted;
    }

    public void setAccepted(boolean accepted) {
        this.accepted = accepted;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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
}