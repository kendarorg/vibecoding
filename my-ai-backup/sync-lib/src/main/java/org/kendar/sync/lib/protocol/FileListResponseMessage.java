package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.model.FileInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Message sent by the server to the client in response to a file list message.
 * Contains the list of files that need to be transferred and the list of files that need to be deleted.
 */
public class FileListResponseMessage extends Message {
    private List<FileInfo> filesToTransfer;
    private List<String> filesToDelete;
    private boolean isBackup;
    private int partNumber;
    private int totalParts;

    // Default constructor for Jackson
    public FileListResponseMessage() {
        this.filesToTransfer = new ArrayList<>();
        this.filesToDelete = new ArrayList<>();
    }

    /**
     * Creates a new file list response message.
     *
     * @param filesToTransfer The list of files that need to be transferred
     * @param filesToDelete   The list of files that need to be deleted
     * @param isBackup        Whether this is a backup operation (true) or restore operation (false)
     * @param partNumber      The part number of this message (for multi-part messages)
     * @param totalParts      The total number of parts
     */
    public FileListResponseMessage(List<FileInfo> filesToTransfer, List<String> filesToDelete,
                                   boolean isBackup, int partNumber, int totalParts) {
        this.filesToTransfer = filesToTransfer;
        this.filesToDelete = filesToDelete;
        this.isBackup = isBackup;
        this.partNumber = partNumber;
        this.totalParts = totalParts;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.FILE_LIST_RESPONSE;
    }

    // Getters and setters
    public List<FileInfo> getFilesToTransfer() {
        return filesToTransfer;
    }

    public void setFilesToTransfer(List<FileInfo> filesToTransfer) {
        this.filesToTransfer = filesToTransfer;
    }

    public List<String> getFilesToDelete() {
        return filesToDelete;
    }

    public void setFilesToDelete(List<String> filesToDelete) {
        this.filesToDelete = filesToDelete;
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
     * Adds a file to the list of files to transfer.
     *
     * @param file The file to add
     */
    public void addFileToTransfer(FileInfo file) {
        filesToTransfer.add(file);
    }

    /**
     * Adds a file to the list of files to delete.
     *
     * @param relativePath The relative path of the file to delete
     */
    public void addFileToDelete(String relativePath) {
        filesToDelete.add(relativePath);
    }
}