package org.kendar.sync.lib.protocol;

import java.lang.reflect.Constructor;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Defines the types of messages that can be exchanged in the sync protocol.
 * Each message type is represented by a 2-character code.
 */
public enum MessageType {
    // Connection and authentication
    CONNECT("CN",ConnectMessage.class),           // Client connects to server with credentials
    CONNECT_RESPONSE("CR", ConnectResponseMessage.class),  // Server response to connection request

    // File listing and comparison
    FILE_LIST("FL",FileListMessage.class),         // Client sends the list of files
    FILE_LIST_RESPONSE("FR",FileListResponseMessage.class), // Server responds with files to transfer

    // File transfer
    FILE_DESCRIPTOR("FD",FileDescriptorMessage.class),   // File metadata (name, path, size, timestamps)
    FILE_DESCRIPTOR_ACK("FA", FileDescriptorAckMessage.class), // Acknowledgment of file descriptor
    FILE_DATA("FT",FileDataMessage.class),         // File content data
    FILE_DATA_ACK("FK",FileDataAck.class),
    FILE_END("FE",FileEndMessage.class),          // End of file marker
    FILE_END_ACK("EA",FileEndAckMessage.class),      // Acknowledgment of the file end

    // Synchronization control
    SYNC_END("SE",SyncEndMessage.class),          // End of synchronization
    SYNC_END_ACK("SA",SyncEndAckMessage.class),      // Acknowledgment of the sync end

    // Error handling
    ERROR("ER",ErrorMessage.class),
    START_RESTORE("RS", StartRestore.class),
    START_RESTORE_ACK("RK", StartRestoreAck.class),

    KEEP_ALIVE("KA", KeepAlive.class),
    FILE_SYNC("SY", FileSyncMessage.class),
    FILE_SYNC_ACK("SK", FileSyncMessageAck.class);             // Error message

    private final String code;
    private final Class<?> clazz;

    MessageType(String code,Class<?> clazz) {
        this.clazz = clazz;
        if (code == null || code.length() != 2) {
            throw new IllegalArgumentException("Message type code must be exactly 2 characters");
        }
        this.code = code;
    }

    /**
     * Gets the message type from its code.
     *
     * @param code The 2-character code
     * @return The corresponding message type, or null if not found
     */
    public static MessageType fromCode(String code) {
        for (MessageType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * Gets the 2-character code for this message type.
     *
     * @return The message type code
     */
    public String getCode() {
        return code;
    }

    public Class<?> getClazz(){
        return clazz;
    }
    private static final ConcurrentHashMap<MessageType, Constructor<?>> constructorMap = new ConcurrentHashMap<>();
    public Message createInstance() {
        try {
            var constructor = constructorMap.computeIfAbsent(this, type -> {
                try {
                    return type.getClazz().getDeclaredConstructor();
                } catch (NoSuchMethodException e) {
                    throw new RuntimeException("No default constructor for " + type.getClazz().getName(), e);
                }
            });
            return (Message) constructor.newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create instance of " + clazz.getName(), e);
        }
    }
}