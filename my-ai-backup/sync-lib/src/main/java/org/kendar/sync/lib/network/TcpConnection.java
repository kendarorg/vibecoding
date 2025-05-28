package org.kendar.sync.lib.network;

import org.kendar.sync.lib.protocol.Message;
import org.kendar.sync.lib.protocol.Packet;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Objects;
import java.util.UUID;

/**
 * Handles TCP communication between the client and server.
 */
public class TcpConnection implements AutoCloseable {
    private final Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private UUID sessionId;
    private int connectionId;
    private final int packetId;
    private final int maxPacketSize;

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TcpConnection that)) return false;
        return Objects.equals(socket, that.socket);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(socket);
    }

    /**
     * Creates a new TCP connection.
     *
     * @param socket The socket
     * @param sessionId The session ID
     * @param connectionId The connection ID
     * @param maxPacketSize The maximum packet size
     * @throws IOException If an I/O error occurs
     */
    public TcpConnection(Socket socket, UUID sessionId, int connectionId, int maxPacketSize) throws IOException {
        this.socket = socket;
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
        this.sessionId = sessionId;
        this.connectionId = connectionId;
        this.packetId = 0;
        this.maxPacketSize = maxPacketSize;
    }
    
    /**
     * Sends a message.
     *
     * @param message The message to send
     * @throws IOException If an I/O error occurs
     */
    public void sendMessage(Message message) throws IOException {
        byte[] messageData = message.serialize();
        
        // Create a packet with the message data
        Packet packet = new Packet(
                connectionId,
                sessionId,
                packetId,
                message.getMessageType().getCode(),
                messageData
        );
        
        // Serialize the packet and send it
        byte[] packetData = packet.serialize();
        outputStream.write(packetData);
        outputStream.flush();
    }
    
    /**
     * Receives a message.
     *
     * @return The received message
     * @throws IOException If an I/O error occurs
     */
    public Message receiveMessage() throws IOException {
        // Read the packet length
        byte[] lengthBytes = new byte[4];
        this.inputStream = socket.getInputStream();
        int bytesRead = inputStream.read(lengthBytes);
        if (bytesRead != 4) {
            throw new IOException("Failed to read packet length");
        }
        
        int packetLength = java.nio.ByteBuffer.wrap(lengthBytes).getInt();
        if (packetLength <= 0 || packetLength > maxPacketSize) {
            throw new IOException("Invalid packet length: " + packetLength);
        }
        
        // Read the rest of the packet
        byte[] packetData = new byte[packetLength];
        System.arraycopy(lengthBytes, 0, packetData, 0, 4);
        
        int remaining = packetLength - 4;
        int offset = 4;
        
        while (remaining > 0) {
            bytesRead = inputStream.read(packetData, offset, remaining);
            if (bytesRead == -1) {
                throw new IOException("End of stream reached");
            }
            
            offset += bytesRead;
            remaining -= bytesRead;
        }
        
        // Deserialize the packet
        Packet packet = Packet.deserialize(packetData);
        
        // Deserialize the message
        var result = Message.deserialize(packet.getDecompressedContent());
        result.initialize(packet.getConnectionId(), packet.getSessionId(), packet.getPacketId());
        return result;
    }
    
    /**
     * Closes the connection.
     *
     * @throws IOException If an I/O error occurs
     */
    @Override
    public void close() throws IOException {
        inputStream.close();
        outputStream.close();
        socket.close();
    }
    
    /**
     * Gets the session ID.
     *
     * @return The session ID
     */
    public UUID getSessionId() {
        return sessionId;
    }
    
    /**
     * Gets the connection ID.
     *
     * @return The connection ID
     */
    public int getConnectionId() {
        return connectionId;
    }
    
    /**
     * Gets the socket.
     *
     * @return The socket
     */
    public Socket getSocket() {
        return socket;
    }
    
    /**
     * Gets the maximum packet size.
     *
     * @return The maximum packet size
     */
    public int getMaxPacketSize() {
        return maxPacketSize;
    }

    public void setSessionId(UUID sessionId) {
        this.sessionId= sessionId;
    }

    public void setConnectionId(int connectionId) {
        this.connectionId=connectionId;
    }
}