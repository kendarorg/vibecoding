package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message used to report errors during the synchronization process.
 * Can be sent by either the client or the server.
 */
public class ErrorMessage extends Message {


    private String errorCode;
    private String errorMessage;
    private String details;

    // Default constructor for Jackson
    public ErrorMessage() {
    }

    /**
     * Creates a new error message.
     *
     * @param errorCode    The error code
     * @param errorMessage The error message
     * @param details      Additional details about the error
     */
    public ErrorMessage(String errorCode, String errorMessage, String details) {
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
        this.details = details;
    }

    /**
     * Creates a new error message.
     *
     * @param errorCode    The error code
     * @param errorMessage The error message
     */
    public ErrorMessage(String errorCode, String errorMessage) {
        this(errorCode, errorMessage, null);
    }

    /**
     * Creates a new error message from an exception.
     *
     * @param errorCode The error code
     * @param exception The exception
     * @return A new error message
     */
    public static ErrorMessage fromException(String errorCode, Exception exception) {
        return new ErrorMessage(errorCode, exception.getMessage(), exception.toString());
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.ERROR;
    }

    @Override
    protected Message deserialize(ByteContainer buffer) {
        errorCode = buffer.readType(String.class);
        errorMessage = buffer.readType(String.class);
        details = buffer.readType(String.class);
        return this;
    }

    @Override
    protected void serialize(ByteContainer buffer) {
        buffer.writeType(errorCode);
        if (errorMessage != null) buffer.writeType(errorMessage);
        else buffer.writeType("");
        if (details != null) buffer.writeType(details);
        else buffer.writeType("");
    }

    // Getters and setters
    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }
}