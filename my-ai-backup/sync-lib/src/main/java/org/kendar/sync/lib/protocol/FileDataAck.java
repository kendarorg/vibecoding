package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message sent before transferring a file to describe its metadata.
 */
public class FileDataAck extends Message {

    // Default constructor for Jackson
    public FileDataAck() {
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.FILE_DATA_ACK;
    }

    @Override
    protected Message deserialize(ByteContainer buffer) {
        // No fields to deserialize
        return this;
    }

    @Override
    protected void serialize(ByteContainer buffer) {
        // No fields to serialize
    }
}