package org.kendar.sync.lib.network;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.protocol.ErrorMessage;
import org.kendar.sync.lib.protocol.FileDataAck;
import org.kendar.sync.lib.protocol.Message;
import org.kendar.sync.lib.protocol.MessageType;

import java.io.IOException;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the TcpConnection class using FakeSocket instead of real sockets.
 */
class TcpConnectionTest {

    private static final int MAX_PACKET_SIZE = 1024;
    private UUID sessionId;
    private int connectionId;
    private FakeSocket clientSocket;
    private FakeSocket serverSocket;
    private TcpConnection clientConnection;
    private TcpConnection serverConnection;

    @BeforeEach
    void setUp() throws IOException {
        // Create test parameters
        sessionId = UUID.randomUUID();
        connectionId = 1;

        // Create fake sockets for client and server
        clientSocket = new FakeSocket();
        serverSocket = new FakeSocket();

        // Create TcpConnections
        clientConnection = new TcpConnection(clientSocket, sessionId, connectionId, MAX_PACKET_SIZE,false);
        serverConnection = new TcpConnection(serverSocket, sessionId, connectionId, MAX_PACKET_SIZE,true);
    }

    /**
     * Helper method to transfer data from client to server socket
     */
    private void transferClientToServer() throws IOException {
        byte[] data = clientSocket.getOutputStreamData();
        serverSocket.addInputStreamData(data);
    }

    /**
     * Helper method to transfer data from server to client socket
     */
    private void transferServerToClient() throws IOException {
        byte[] data = serverSocket.getOutputStreamData();
        clientSocket.addInputStreamData(data);
    }

    @Test
    void testSendAndReceiveMessage() throws Exception {
        // Create a test message
        ErrorMessage testMessage = new ErrorMessage("ERR001", "Test error message");

        // Client sends a message
        clientConnection.sendMessage(testMessage);

        // Transfer data from client to server
        transferClientToServer();

        // Server receives the message
        IOException exception = assertThrows(IOException.class, () -> serverConnection.receiveMessage());

        assertEquals("ERR001-Test error message-",
                exception.getMessage());

        // Server sends the same message back
        serverConnection.sendMessage(new FileDataAck());

        // Transfer data from server to client
        transferServerToClient();

        // Client receives the message
        Message clientReceivedMessage = clientConnection.receiveMessage();

        // Verify the received message
        assertEquals(MessageType.FILE_DATA_ACK, clientReceivedMessage.getMessageType());
    }

    @Test
    void testGetters() throws IOException {
        // Test getters
        assertEquals(sessionId, clientConnection.getSessionId());
        assertEquals(connectionId, clientConnection.getConnectionId());
        assertEquals(MAX_PACKET_SIZE, clientConnection.getMaxPacketSize());
        assertSame(clientSocket, clientConnection.getSocket());
    }

    @Test
    void testClose() throws IOException {
        // Close the connection
        clientConnection.close();

        // Verify that the socket is closed
        assertTrue(clientSocket.isClosed());
    }
}