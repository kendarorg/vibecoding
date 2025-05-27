package org.kendar.sync.lib.protocol;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/**
 * Represents a packet in the sync protocol.
 * Packet structure:
 * - Integer: length of the packet including itself
 * - Integer: The connection id (used to parallelize the communication. Starts from 0 for the connection)
 * - UUID: The session id (defined in the connection phase, is unique for the whole session)
 * - Integer: packet id used when sending data in multiple blocks
 * - char[2]: containing the type of message
 * - byte[]: containing the zipped content of the message
 */
public class Packet {
    private int length;
    private int connectionId;
    private UUID sessionId;
    private int packetId;
    private String messageType;
    private byte[] content;

    // Default constructor for deserialization
    public Packet() {
    }

    /**
     * Creates a new packet with the specified parameters.
     *
     * @param connectionId The connection ID
     * @param sessionId The session ID
     * @param packetId The packet ID
     * @param messageType The message type (2 characters)
     * @param content The content of the packet (will be compressed)
     */
    public Packet(int connectionId, UUID sessionId, int packetId, String messageType, byte[] content) {
        if (messageType == null || messageType.length() != 2) {
            throw new IllegalArgumentException("Message type must be exactly 2 characters");
        }
        
        this.connectionId = connectionId;
        this.sessionId = sessionId;
        this.packetId = packetId;
        this.messageType = messageType;
        
        // Compress the content
        this.content = compress(content);
        
        // Calculate the length of the packet
        // 4 (length) + 4 (connectionId) + 16 (UUID) + 4 (packetId) + 2 (messageType) + content.length
        this.length = 4 + 4 + 16 + 4 + 2 + this.content.length;
    }

    /**
     * Serializes the packet to a byte array.
     *
     * @return The serialized packet
     */
    public byte[] serialize() {
        ByteBuffer buffer = ByteBuffer.allocate(length);
        
        // Write the packet length
        buffer.putInt(length);
        
        // Write the connection ID
        buffer.putInt(connectionId);
        
        // Write the session ID
        buffer.putLong(sessionId.getMostSignificantBits());
        buffer.putLong(sessionId.getLeastSignificantBits());
        
        // Write the packet ID
        buffer.putInt(packetId);
        
        // Write the message type
        buffer.putChar(messageType.charAt(0));
        buffer.putChar(messageType.charAt(1));
        
        // Write the content
        buffer.put(content);
        
        return buffer.array();
    }

    /**
     * Deserializes a packet from a byte array.
     *
     * @param data The serialized packet
     * @return The deserialized packet
     */
    public static Packet deserialize(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);
        Packet packet = new Packet();
        
        // Read the packet length
        packet.length = buffer.getInt();
        
        // Read the connection ID
        packet.connectionId = buffer.getInt();
        
        // Read the session ID
        long mostSigBits = buffer.getLong();
        long leastSigBits = buffer.getLong();
        packet.sessionId = new UUID(mostSigBits, leastSigBits);
        
        // Read the packet ID
        packet.packetId = buffer.getInt();
        
        // Read the message type
        char c1 = buffer.getChar();
        char c2 = buffer.getChar();
        packet.messageType = String.valueOf(c1) + String.valueOf(c2);
        
        // Read the content
        packet.content = new byte[packet.length - (4 + 4 + 16 + 4 + 2)];
        buffer.get(packet.content);
        
        return packet;
    }

    /**
     * Compresses the content using ZLIB.
     *
     * @param data The data to compress
     * @return The compressed data
     */
    private byte[] compress(byte[] data) {
        Deflater deflater = new Deflater();
        deflater.setInput(data);
        deflater.finish();
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(data.length);
        byte[] buffer = new byte[1024];
        while (!deflater.finished()) {
            int count = deflater.deflate(buffer);
            outputStream.write(buffer, 0, count);
        }
        
        try {
            outputStream.close();
        } catch (IOException e) {
            throw new RuntimeException("Error compressing data", e);
        }
        
        return outputStream.toByteArray();
    }

    /**
     * Decompresses the content using ZLIB.
     *
     * @return The decompressed content
     */
    public byte[] getDecompressedContent() {
        Inflater inflater = new Inflater();
        inflater.setInput(content);
        
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream(content.length);
        byte[] buffer = new byte[1024];
        try {
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();
        } catch (Exception e) {
            throw new RuntimeException("Error decompressing data", e);
        }
        
        return outputStream.toByteArray();
    }

    // Getters and setters
    public int getLength() {
        return length;
    }

    public int getConnectionId() {
        return connectionId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    public int getPacketId() {
        return packetId;
    }

    public String getMessageType() {
        return messageType;
    }

    public byte[] getContent() {
        return content;
    }
}