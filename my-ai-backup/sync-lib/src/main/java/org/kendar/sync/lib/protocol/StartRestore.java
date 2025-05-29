package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message sent before transferring a file to describe its metadata.
 */
public class StartRestore extends Message {

    static {
        Message.registerMessageType(StartRestore.class);
    }

    // Default constructor for Jackson
    public StartRestore() {
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.START_RESTORE;
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