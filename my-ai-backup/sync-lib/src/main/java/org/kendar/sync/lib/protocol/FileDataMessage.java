package org.kendar.sync.lib.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;

/**
 * Message containing a chunk of file data.
 */
public class FileDataMessage extends Message {
    private String relativePath;
    private int blockNumber;
    private int totalBlocks;
    private byte[] data;
    
    // Default constructor for Jackson
    public FileDataMessage() {
    }
    
    /**
     * Creates a new file data message.
     *
     * @param relativePath The relative path of the file
     * @param blockNumber The block number (0-based)
     * @param totalBlocks The total number of blocks
     * @param data The file data
     */
    public FileDataMessage(String relativePath, int blockNumber, int totalBlocks, byte[] data) {
        this.relativePath = relativePath;
        this.blockNumber = blockNumber;
        this.totalBlocks = totalBlocks;
        this.data = data;
    }
    
    @Override
    public MessageType getMessageType() {
        return MessageType.FILE_DATA;
    }
    
    // Getters and setters
    public String getRelativePath() {
        return relativePath;
    }
    
    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }
    
    public int getBlockNumber() {
        return blockNumber;
    }
    
    public void setBlockNumber(int blockNumber) {
        this.blockNumber = blockNumber;
    }
    
    public int getTotalBlocks() {
        return totalBlocks;
    }
    
    public void setTotalBlocks(int totalBlocks) {
        this.totalBlocks = totalBlocks;
    }
    
    public byte[] getData() {
        return data;
    }
    
    public void setData(byte[] data) {
        this.data = data;
    }
    
    /**
     * Checks if this is the first block of the file.
     *
     * @return True if this is the first block, false otherwise
     */
    @JsonIgnore
    public boolean isFirstBlock() {
        return blockNumber == 0;
    }
    
    /**
     * Checks if this is the last block of the file.
     *
     * @return True if this is the last block, false otherwise
     */
    @JsonIgnore
    public boolean isLastBlock() {
        return blockNumber == totalBlocks - 1;
    }
}