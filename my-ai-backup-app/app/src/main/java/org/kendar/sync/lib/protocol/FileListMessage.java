package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;
import org.kendar.sync.lib.model.FileInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Message sent by the client to the server with a list of files in the source directory.
 * Used during both backup and restore operations.
 */
public class FileListMessage extends Message {
    static {
        Message.registerMessageType(FileListMessage.class);
    }

    private List<FileInfo> files;
    private boolean isBackup;
    private int partNumber;
    private int totalParts;

    // Default constructor for Jackson
    public FileListMessage() {
        this.files = new ArrayList<>();
    }

    /**
     * Creates a new file list message.
     *
     * @param files      The list of files
     * @param isBackup   Whether this is a backup operation (true) or restore operation (false)
     * @param partNumber The part number of this message (for multipart messages)
     * @param totalParts The total number of parts
     */
    public FileListMessage(List<FileInfo> files, boolean isBackup, int partNumber, int totalParts) {
        this.files = files;
        this.isBackup = isBackup;
        this.partNumber = partNumber;
        this.totalParts = totalParts;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.FILE_LIST;
    }

    @Override
    protected Message deserialize(ByteContainer buffer) {
        var filesLines = buffer.readType(String.class).split("\n");
        files = new ArrayList<>();
        for (var fileLine : filesLines) {
            if (!fileLine.isEmpty()) {
                files.add(FileInfo.fromLine(fileLine));
            }
        }
        isBackup = buffer.readType(Boolean.class);
        partNumber = buffer.readType(Integer.class);
        totalParts = buffer.readType(Integer.class);
        return this;
    }

    @Override
    protected void serialize(ByteContainer buffer) {
        var filesLines = files.stream()
                .map(FileInfo::toLine)
                .collect(Collectors.toList());
        buffer.writeType(String.join("\n", filesLines));
        buffer.writeType(isBackup);
        buffer.writeType(partNumber);
        buffer.writeType(totalParts);
    }

    // Getters and setters
    public List<FileInfo> getFiles() {
        return files;
    }

    public void setFiles(List<FileInfo> files) {
        this.files = files;
    }

    public boolean isBackup() {
        return isBackup;
    }

    public void setBackup(boolean backup) {
        isBackup = backup;
    }

    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    public int getTotalParts() {
        return totalParts;
    }

    public void setTotalParts(int totalParts) {
        this.totalParts = totalParts;
    }

    /**
     * Adds a file to the list.
     *
     * @param file The file to add
     */
    public void addFile(FileInfo file) {
        files.add(file);
    }
}