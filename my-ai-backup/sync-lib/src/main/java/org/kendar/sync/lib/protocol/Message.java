package org.kendar.sync.lib.protocol;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.kendar.sync.lib.buffer.ByteContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
        @JsonSubTypes.Type(value = ErrorMessage.class, name = "ERROR"),

        @JsonSubTypes.Type(value = StartRestore.class, name = "START_RESTORE")

})
public abstract class Message {
    private static final ConcurrentHashMap<String, Class<? extends Message>> messageTypeMap = new ConcurrentHashMap<>();
    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());
    private static final Logger log = LoggerFactory.getLogger(Message.class);
    @JsonIgnore
    private int connectionId;
    @JsonIgnore
    private UUID sessionId;
    @JsonIgnore
    private int packetId;

    public static void registerMessageType(Class<? extends Message> clazz) {
        if (clazz == null) {
            throw new IllegalArgumentException("Type and class must not be null or empty");
        }
        messageTypeMap.put(clazz.getSimpleName(), clazz);
    }

    /**
     * Deserializes a message from a JSON byte array.
     *
     * @param data  The serialized message
     * @param clazz The class of the message
     * @param <T>   The type of the message
     * @return The deserialized message
     */
    @SuppressWarnings("unchecked")
    public static <T extends Message> T deserialize(byte[] data, Class<T> clazz) {
        var buffer = ByteContainer.create();
        buffer.write(data);
        buffer.resetReadCursor();
        buffer.resetWriteCursor();
        String type = buffer.readType(String.class);
        try {
            var instance = (Message) clazz.getDeclaredConstructor().newInstance();
            return (T) instance.deserialize(buffer);
        } catch (Exception e) {
            log.error("Error 1 deserializing message of type: {}", type);
            throw new RuntimeException(e);
        }
    }

    /**
     * Deserializes a message from a JSON byte array.
     *
     * @param data The serialized message
     * @return The deserialized message
     */
    public static Message deserialize(byte[] data) {
        var buffer = ByteContainer.create();
        buffer.write(data);
        buffer.resetReadCursor();
        buffer.resetWriteCursor();
        String type = buffer.readType(String.class);
        var clazz = messageTypeMap.get(type);
        try {
            var instance = (Message) clazz.getDeclaredConstructor().newInstance();
            return instance.deserialize(buffer);
        } catch (Exception e) {
            log.error("Error 2 deserializing message of type: {}", type);
            throw new RuntimeException(e);
        }
        // return objectMapper.readValue(data, Message.class);
    }

    protected abstract Message deserialize(ByteContainer buffer);

    public int getConnectionId() {
        return connectionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getPacketId() {
        return packetId;
    }

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
     */
    public byte[] serialize() {
        try {
            var buffer = ByteContainer.create();
            buffer.writeType(this.getClass().getSimpleName());
            this.serialize(buffer);
            return buffer.getBytes();
        } catch (Exception e) {
            log.error("Error 3 serializing {}", this.getClass().getSimpleName());
            throw new RuntimeException(e);
        }
        //return objectMapper.writeValueAsBytes(this);
    }

    protected abstract void serialize(ByteContainer buffer);

    public void initialize(int connectionId, UUID sessionId, int packetId) {
        this.connectionId = connectionId;
        this.sessionId = sessionId;
        this.packetId = packetId;
    }
}
