package org.kendar.sync.lib.network;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.protocol.Message;
import org.kendar.sync.lib.protocol.MessageType;
import org.kendar.sync.lib.protocol.Packet;
import org.kendar.sync.lib.protocol.ErrorMessage;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TcpConnectionTest {

    private static final int TEST_PORT = 12345;
    private static final int MAX_PACKET_SIZE = 1024;
    private ServerSocket serverSocket;
    private ExecutorService executorService;
    private UUID sessionId;
    private int connectionId;

    @BeforeEach
    void setUp() throws IOException {
        // Create a unique test directory inside target/tests
        String uniqueId = UUID.randomUUID().toString();

        // Create a server socket for testing
        serverSocket = new ServerSocket(TEST_PORT);
        executorService = Executors.newFixedThreadPool(2);
        sessionId = UUID.randomUUID();
        connectionId = 1;
    }

    @AfterEach
    void tearDown() throws IOException {
        // Close the server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            serverSocket.close();
        }

        // Shutdown the executor service
        if (executorService != null) {
            executorService.shutdownNow();
            try {
                executorService.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void testSendAndReceiveMessage() throws Exception {
        // Create a latch to synchronize the test
        CountDownLatch serverReady = new CountDownLatch(1);
        CountDownLatch messageReceived = new CountDownLatch(1);

        // Create a test message
        ErrorMessage testMessage = new ErrorMessage("ERR001", "Test error message");

        // Start the server in a separate thread
        executorService.submit(() -> {
            try {
                // Accept a connection
                Socket serverSideSocket = serverSocket.accept();

                // Create a TcpConnection
                TcpConnection serverConnection = new TcpConnection(
                    serverSideSocket, sessionId, connectionId, MAX_PACKET_SIZE);

                // Signal that the server is ready
                serverReady.countDown();

                // Receive a message
                Message receivedMessage = serverConnection.receiveMessage();

                // Verify the received message
                assertEquals(MessageType.ERROR, receivedMessage.getMessageType());
                assertEquals("Test error message", ((ErrorMessage) receivedMessage).getErrorMessage());

                // Send the same message back
                serverConnection.sendMessage(receivedMessage);

                // Signal that the message has been received and sent back
                messageReceived.countDown();

                // Close the connection
                serverConnection.close();
            } catch (IOException e) {
                e.printStackTrace();
                fail("Server thread failed: " + e.getMessage());
            }
            return null;
        });

        // Connect to the server
        Socket clientSocket = new Socket("localhost", TEST_PORT);

        // Create a TcpConnection
        TcpConnection clientConnection = new TcpConnection(
            clientSocket, sessionId, connectionId, MAX_PACKET_SIZE);

        // Wait for the server to be ready
        assertTrue(serverReady.await(5, TimeUnit.SECONDS), "Server not ready in time");

        // Send a message
        clientConnection.sendMessage(testMessage);

        // Wait for the server to receive and send back the message
        assertTrue(messageReceived.await(5, TimeUnit.SECONDS), "Message not received in time");

        // Receive the message sent back by the server
        Message receivedMessage = clientConnection.receiveMessage();

        // Verify the received message
        assertEquals(MessageType.ERROR, receivedMessage.getMessageType());
        assertEquals("Test error message", ((ErrorMessage) receivedMessage).getErrorMessage());

        // Close the connection
        clientConnection.close();
    }

    @Test
    void testGetters() throws IOException {
        // Create a client socket
        Socket clientSocket = new Socket("localhost", TEST_PORT);

        // Create a TcpConnection
        TcpConnection connection = new TcpConnection(
            clientSocket, sessionId, connectionId, MAX_PACKET_SIZE);

        // Test getters
        assertEquals(sessionId, connection.getSessionId());
        assertEquals(connectionId, connection.getConnectionId());
        assertEquals(MAX_PACKET_SIZE, connection.getMaxPacketSize());
        assertSame(clientSocket, connection.getSocket());

        // Close the connection
        connection.close();
    }

    @Test
    void testClose() throws IOException {
        // Create a client socket
        Socket clientSocket = new Socket("localhost", TEST_PORT);

        // Create a TcpConnection
        TcpConnection connection = new TcpConnection(
            clientSocket, sessionId, connectionId, MAX_PACKET_SIZE);

        // Close the connection
        connection.close();

        // Verify that the socket is closed
        assertTrue(clientSocket.isClosed());
    }
}
