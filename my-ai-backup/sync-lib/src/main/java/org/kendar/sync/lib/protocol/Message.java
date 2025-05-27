package org.kendar.sync.lib.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;

/**
 * Base class for all messages in the sync protocol.
 * Messages are serialized to JSON and then compressed before being sent in a packet.
 */
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "type"
)
@JsonSubTypes({
    // Connection messages
    @JsonSubTypes.Type(value = ConnectMessage.class, name = "CONNECT"),
    @JsonSubTypes.Type(value = ConnectResponseMessage.class, name = "CONNECT_RESPONSE"),

    // File listing messages
    @JsonSubTypes.Type(value = FileListMessage.class, name = "FILE_LIST"),
    @JsonSubTypes.Type(value = FileListResponseMessage.class, name = "FILE_LIST_RESPONSE"),

    // File transfer messages
    @JsonSubTypes.Type(value = FileDescriptorMessage.class, name = "FILE_DESCRIPTOR"),
    @JsonSubTypes.Type(value = FileDescriptorAckMessage.class, name = "FILE_DESCRIPTOR_ACK"),
    @JsonSubTypes.Type(value = FileDataMessage.class, name = "FILE_DATA"),
    @JsonSubTypes.Type(value = FileEndMessage.class, name = "FILE_END"),
    @JsonSubTypes.Type(value = FileEndAckMessage.class, name = "FILE_END_ACK"),

    // Synchronization control messages
    @JsonSubTypes.Type(value = SyncEndMessage.class, name = "SYNC_END"),
    @JsonSubTypes.Type(value = SyncEndAckMessage.class, name = "SYNC_END_ACK"),

    // Error messages
    @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR")
})
public abstract class Message {
    private static final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule());

    /**
     * Gets the message type for this message.
     *
     * @return The message type
     */
    @JsonIgnore
    public abstract MessageType getMessageType();

    /**
     * Serializes this message to a JSON byte array.
     *
     * @return The serialized message
     * @throws IOException If serialization fails
     */
    public byte[] serialize() throws IOException {
        return objectMapper.writeValueAsBytes(this);
    }

    /**
     * Deserializes a message from a JSON byte array.
     *
     * @param data The serialized message
     * @param clazz The class of the message
     * @param <T> The type of the message
     * @return The deserialized message
     * @throws IOException If deserialization fails
     */
    public static <T extends Message> T deserialize(byte[] data, Class<T> clazz) throws IOException {
        return objectMapper.readValue(data, clazz);
    }

    /**
     * Deserializes a message from a JSON byte array.
     *
     * @param data The serialized message
     * @return The deserialized message
     * @throws IOException If deserialization fails
     */
    public static Message deserialize(byte[] data) throws IOException {
        return objectMapper.readValue(data, Message.class);
    }
}
