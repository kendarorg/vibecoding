package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message sent before transferring a file to describe its metadata.
 */
public class StartRestoreAck extends Message {

    static {
        Message.registerMessageType(StartRestoreAck.class);
    }

    // Default constructor for Jackson
    public StartRestoreAck() {
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.START_RESTORE_ACK;
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