package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message sent in response to a file descriptor message to acknowledge that the receiver
 * is ready to receive the file data.
 */
public class FileDescriptorAckMessage extends Message {
    private String relativePath;
    private boolean ready;
    private String errorMessage;

    static {
        Message.registerMessageType(FileDescriptorAckMessage.class);
    }
    // Default constructor for Jackson
    public FileDescriptorAckMessage() {
    }

    /**
     * Creates a new file descriptor acknowledgment message.
     *
     * @param relativePath The relative path of the file
     * @param ready        Whether the receiver is ready to receive the file data
     * @param errorMessage Error message if the receiver is not ready
     */
    public FileDescriptorAckMessage(String relativePath, boolean ready, String errorMessage) {
        this.relativePath = relativePath;
        this.ready = ready;
        this.errorMessage = errorMessage;
    }

    /**
     * Creates a new file descriptor acknowledgment message for a successful acknowledgment.
     *
     * @param relativePath The relative path of the file
     * @return A new file descriptor acknowledgment message
     */
    public static FileDescriptorAckMessage ready(String relativePath) {
        return new FileDescriptorAckMessage(relativePath, true, null);
    }

    /**
     * Creates a new file descriptor acknowledgment message for a failed acknowledgment.
     *
     * @param relativePath The relative path of the file
     * @param errorMessage The reason for the failure
     * @return A new file descriptor acknowledgment message
     */
    public static FileDescriptorAckMessage notReady(String relativePath, String errorMessage) {
        return new FileDescriptorAckMessage(relativePath, false, errorMessage);
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.FILE_DESCRIPTOR_ACK;
    }

    @Override
    protected Message deserialize(ByteContainer buffer) {
        relativePath = buffer.readType(String.class);
        ready = buffer.readType(Boolean.class);
        errorMessage = buffer.readType(String.class);
        return this;
    }

    @Override
    protected void serialize(ByteContainer buffer) {
        buffer.writeType(relativePath);
        buffer.writeType(ready);
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

    public boolean isReady() {
        return ready;
    }

    public void setReady(boolean ready) {
        this.ready = ready;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}