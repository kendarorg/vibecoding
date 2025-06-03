package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message sent in response to a file end message to acknowledge that the file has been successfully received.
 */
public class FileSyncMessageAck extends Message {
    static {
        Message.registerMessageType(FileSyncMessageAck.class);
    }

    private boolean success;
    private String errorMessage;

    // Default constructor for Jackson
    public FileSyncMessageAck() {
    }

    /**
     * Creates a new file end acknowledgment message.
     *
     * @param relativePath The relative path of the file
     * @param success      Whether the file was successfully received
     * @param errorMessage Error message if the file was not successfully received
     */
    public FileSyncMessageAck(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a new file end acknowledgment message for a successful acknowledgment.
     *
     * @return A new file end acknowledgment message
     */
    public static FileSyncMessageAck success() {
        return new FileSyncMessageAck( true, null);
    }

    /**
     * Creates a new file end acknowledgment message for a failed acknowledgment.
     *
     * @param errorMessage The reason for the failure
     * @return A new file end acknowledgment message
     */
    public static FileSyncMessageAck failure( String errorMessage) {
        return new FileSyncMessageAck( false, errorMessage);
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.FILE_SYNC_ACK;
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
        if (errorMessage != null) buffer.writeType(errorMessage);
        else buffer.writeType("");
    }

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