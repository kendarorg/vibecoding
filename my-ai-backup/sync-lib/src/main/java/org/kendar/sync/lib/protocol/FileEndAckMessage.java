package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message sent in response to a file end message to acknowledge that the file has been successfully received.
 */
public class FileEndAckMessage extends Message {
    private String relativePath;
    private boolean success;
    private String errorMessage;

    static {
        Message.registerMessageType(FileEndAckMessage.class);
    }
    // Default constructor for Jackson
    public FileEndAckMessage() {
    }

    /**
     * Creates a new file end acknowledgment message.
     *
     * @param relativePath The relative path of the file
     * @param success      Whether the file was successfully received
     * @param errorMessage Error message if the file was not successfully received
     */
    public FileEndAckMessage(String relativePath, boolean success, String errorMessage) {
        this.relativePath = relativePath;
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a new file end acknowledgment message for a successful acknowledgment.
     *
     * @param relativePath The relative path of the file
     * @return A new file end acknowledgment message
     */
    public static FileEndAckMessage success(String relativePath) {
        return new FileEndAckMessage(relativePath, true, null);
    }

    /**
     * Creates a new file end acknowledgment message for a failed acknowledgment.
     *
     * @param relativePath The relative path of the file
     * @param errorMessage The reason for the failure
     * @return A new file end acknowledgment message
     */
    public static FileEndAckMessage failure(String relativePath, String errorMessage) {
        return new FileEndAckMessage(relativePath, false, errorMessage);
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.FILE_END_ACK;
    }

    @Override
    protected Message deserialize(ByteContainer buffer) {
        relativePath = buffer.readType(String.class);
        success = buffer.readType(Boolean.class);
        errorMessage = buffer.readType(String.class);
        return this;
    }

    @Override
    protected void serialize(ByteContainer buffer) {
        buffer.writeType(relativePath);
        buffer.writeType(success);
        if(errorMessage!=null)buffer.writeType(errorMessage);
        else buffer.writeType("");
    }

    // Getters and setters
    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
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