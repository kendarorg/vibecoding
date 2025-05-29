package org.kendar.sync.lib.network;

import java.io.*;
import java.net.Socket;

/**
 * A fake Socket implementation for testing purposes.
 * This class allows tests to run without making real network connections.
 */
public class FakeSocket extends Socket {
    private final ByteArrayOutputStream outputStream;
    private final ByteArrayInputStream inputStream;
    private boolean closed = false;

    /**
     * Creates a new FakeSocket with empty input and output streams.
     */
    public FakeSocket() {
        this(new byte[0]);
    }

    /**
     * Creates a new FakeSocket with the given initial input data.
     *
     * @param initialInputData The initial data available for reading from the input stream
     */
    public FakeSocket(byte[] initialInputData) {
        this.outputStream = new ByteArrayOutputStream();
        this.inputStream = new ByteArrayInputStream(initialInputData);
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (closed) {
            throw new IOException("Socket is closed");
        }
        return inputStream;
    }

    @Override
    public OutputStream getOutputStream() throws IOException {
        if (closed) {
            throw new IOException("Socket is closed");
        }
        return outputStream;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        inputStream.close();
        outputStream.close();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    /**
     * Gets the data that has been written to the output stream.
     *
     * @return The data written to the output stream
     */
    public byte[] getOutputStreamData() {
        return outputStream.toByteArray();
    }

    /**
     * Adds data to the input stream for reading.
     *
     * @param data The data to add to the input stream
     * @throws IOException If an I/O error occurs
     */
    public void addInputStreamData(byte[] data) throws IOException {
        if (closed) {
            throw new IOException("Socket is closed");
        }

        // Create a new ByteArrayInputStream with the data
        ByteArrayInputStream newInputStream = new ByteArrayInputStream(data);

        // Replace the inputStream field
        try {
            java.lang.reflect.Field field = this.getClass().getDeclaredField("inputStream");
            field.setAccessible(true);
            field.set(this, newInputStream);
        } catch (Exception e) {
            throw new IOException("Failed to add data to input stream", e);
        }
    }
}
