package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message sent by the client to the server to signal the end of a synchronization session.
 */
public class SyncEndMessage extends Message {
    static {
        Message.registerMessageType(SyncEndMessage.class);
    }

    private boolean isBackup;
    private int filesTransferred;
    private int filesDeleted;

    // Default constructor for Jackson
    public SyncEndMessage() {
    }

    /**
     * Creates a new sync end message.
     *
     * @param isBackup         Whether this was a backup operation (true) or restore operation (false)
     * @param filesTransferred The number of files transferred
     * @param filesDeleted     The number of files deleted
     */
    public SyncEndMessage(boolean isBackup, int filesTransferred, int filesDeleted) {
        this.isBackup = isBackup;
        this.filesTransferred = filesTransferred;
        this.filesDeleted = filesDeleted;
    }

    @Override
    public MessageType getMessageType() {
        return MessageType.SYNC_END;
    }

    @Override
    protected Message deserialize(ByteContainer buffer) {
        isBackup = buffer.readType(Boolean.class);
        filesTransferred = buffer.readType(Integer.class);
        filesDeleted = buffer.readType(Integer.class);
        return this;
    }

    @Override
    protected void serialize(ByteContainer buffer) {
        buffer.writeType(isBackup);
        buffer.writeType(filesTransferred);
        buffer.writeType(filesDeleted);
    }

    // Getters and setters
    public boolean isBackup() {
        return isBackup;
    }

    public void setBackup(boolean backup) {
        isBackup = backup;
    }

    public int getFilesTransferred() {
        return filesTransferred;
    }

    public void setFilesTransferred(int filesTransferred) {
        this.filesTransferred = filesTransferred;
    }

    public int getFilesDeleted() {
        return filesDeleted;
    }

    public void setFilesDeleted(int filesDeleted) {
        this.filesDeleted = filesDeleted;
    }
}