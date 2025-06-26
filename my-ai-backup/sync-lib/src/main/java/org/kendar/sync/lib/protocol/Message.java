package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Base class for all messages in the sync protocol.
 */
public abstract class Message {
    private static final Logger log = LoggerFactory.getLogger(Message.class);

    private int connectionId;

    private UUID sessionId;

    private int packetId;

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
        MessageType type = buffer.readType(MessageType.class);
        try {
            var instance = type.createInstance();
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
        MessageType type = buffer.readType(MessageType.class);

        try {
            var instance = type.createInstance();
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

    public abstract MessageType getMessageType();

    /**
     * Serializes this message to a JSON byte array.
     *
     * @return The serialized message
     */
    public byte[] serialize() {
        try {
            var buffer = ByteContainer.create();
            buffer.writeType(this.getMessageType());
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
