package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;
import org.kendar.sync.lib.model.FileInfo;

/**
 * Message sent before transferring a file to describe its metadata.
 */
public class FileDescriptorMessage extends Message {

    private FileInfo fileInfo;

    // Default constructor for Jackson
    public FileDescriptorMessage() {
    }

    /**
     * Creates a new file descriptor message.
     *
     * @param fileInfo The file information
     */
    public FileDescriptorMessage(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.FILE_DESCRIPTOR;
    }

    @Override
    protected Message deserialize(ByteContainer buffer) {
        fileInfo = FileInfo.fromLine(buffer.readType(String.class));
        return this;
    }

    @Override
    protected void serialize(ByteContainer buffer) {
        buffer.writeType(fileInfo.toLine());
    }

    // Getters and setters
    public FileInfo getFileInfo() {
        return fileInfo;
    }

    public void setFileInfo(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
    }
}