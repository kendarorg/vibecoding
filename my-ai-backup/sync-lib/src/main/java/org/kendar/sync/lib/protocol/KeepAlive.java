package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;

/**
 * Message sent before transferring a file to describe its metadata.
 */
public class KeepAlive extends Message {


    // Default constructor for Jackson
    public KeepAlive() {
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.KEEP_ALIVE;
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