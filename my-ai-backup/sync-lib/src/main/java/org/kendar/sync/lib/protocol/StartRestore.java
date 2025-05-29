package org.kendar.sync.lib.protocol;

/**
 * Message sent before transferring a file to describe its metadata.
 */
public class StartRestore extends Message {


    // Default constructor for Jackson
    public StartRestore() {
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.START_RESTORE;
    }

}