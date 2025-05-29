package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.model.FileInfo;

/**
 * Message sent after all blocks of a file have been transferred to signal the end of the file transfer.
 */
public class FileEndMessage extends Message {
    private String relativePath;
    private FileInfo fileInfo;

    // Default constructor for Jackson
    public FileEndMessage() {
    }

    /**
     * Creates a new file end message.
     *
     * @param relativePath The relative path of the file
     * @param fileInfo     The file information
     */
    public FileEndMessage(String relativePath, FileInfo fileInfo) {
        this.relativePath = relativePath;
        this.fileInfo = fileInfo;
    }

    /**
     * Creates a new file end message.
     *
     * @param relativePath The relative path of the file
     */
    public FileEndMessage(String relativePath) {
        this.relativePath = relativePath;
        this.fileInfo = null;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.FILE_END;
    }

    // Getters and setters
    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public FileInfo getFileInfo() {
        return fileInfo;
    }

    public void setFileInfo(FileInfo fileInfo) {
        this.fileInfo = fileInfo;
    }
}
