package org.kendar.sync.lib.protocol;

/**
 * Defines the types of messages that can be exchanged in the sync protocol.
 * Each message type is represented by a 2-character code.
 */
public enum MessageType {
    // Connection and authentication
    CONNECT("CN"),           // Client connects to server with credentials
    CONNECT_RESPONSE("CR"),  // Server response to connection request

    // File listing and comparison
    FILE_LIST("FL"),         // Client sends the list of files
    FILE_LIST_RESPONSE("FR"), // Server responds with files to transfer

    // File transfer
    FILE_DESCRIPTOR("FD"),   // File metadata (name, path, size, timestamps)
    FILE_DESCRIPTOR_ACK("FA"), // Acknowledgment of file descriptor
    FILE_DATA("FT"),         // File content data
    FILE_END("FE"),          // End of file marker
    FILE_END_ACK("EA"),      // Acknowledgment of the file end

    // Synchronization control
    SYNC_END("SE"),          // End of synchronization
    SYNC_END_ACK("SA"),      // Acknowledgment of the sync end

    // Error handling
    ERROR("ER"),
    START_RESTORE("RS"),
    START_RESTORE_ACK("RK"),
    FILE_DATA_ACK("FK"),
    FILE_SYNC("SY"),
    FILE_SYNC_ACK("SK");             // Error message

    private final String code;

    MessageType(String code) {
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
}