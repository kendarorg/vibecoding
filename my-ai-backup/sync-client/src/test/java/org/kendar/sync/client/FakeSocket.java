package org.kendar.sync.client;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
        
        // Since ByteArrayInputStream doesn't support adding data after creation,
        // we need to create a new one with the combined data
        byte[] currentData = inputStream.readAllBytes();
        byte[] newData = new byte[currentData.length + data.length];
        System.arraycopy(currentData, 0, newData, 0, currentData.length);
        System.arraycopy(data, 0, newData, currentData.length, data.length);
        
        // Replace the field with reflection since it's final
        try {
            java.lang.reflect.Field field = ByteArrayInputStream.class.getDeclaredField("buf");
            field.setAccessible(true);
            field.set(inputStream, newData);
            
            field = ByteArrayInputStream.class.getDeclaredField("count");
            field.setAccessible(true);
            field.set(inputStream, newData.length);
            
            field = ByteArrayInputStream.class.getDeclaredField("pos");
            field.setAccessible(true);
            field.set(inputStream, 0);
        } catch (Exception e) {
            throw new IOException("Failed to add data to input stream", e);
        }
    }
}