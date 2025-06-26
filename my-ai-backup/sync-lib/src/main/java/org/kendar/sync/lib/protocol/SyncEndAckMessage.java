package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message sent by the server to the client in response to a sync end message
 * to acknowledge the end of the synchronization session.
 */
public class SyncEndAckMessage extends Message {

    private boolean success;
    private String errorMessage;

    // Default constructor for Jackson
    public SyncEndAckMessage() {
    }

    /**
     * Creates a new sync end acknowledgment message.
     *
     * @param success      Whether the synchronization was successful
     * @param errorMessage Error message if the synchronization was not successful
     */
    public SyncEndAckMessage(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a new sync end acknowledgment message for a successful acknowledgment.
     *
     * @return A new sync end acknowledgment message
     */
    public static SyncEndAckMessage success() {
        return new SyncEndAckMessage(true, null);
    }

    /**
     * Creates a new sync end acknowledgment message for a failed acknowledgment.
     *
     * @param errorMessage The reason for the failure
     * @return A new sync end acknowledgment message
     */
    public static SyncEndAckMessage failure(String errorMessage) {
        return new SyncEndAckMessage(false, errorMessage);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SYNC_END_ACK;
    }

    @Override
    protected Message deserialize(ByteContainer buffer) {
        success = buffer.readType(Boolean.class);
        errorMessage = buffer.readType(String.class);
        return this;
    }

    @Override
    protected void serialize(ByteContainer buffer) {
        buffer.writeType(success);
        buffer.writeType(errorMessage);
    }

    // Getters and setters
    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}