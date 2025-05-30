package org.kendar.sync.lib.buffer;


import org.kendar.sync.lib.buffer.converters.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * A container for managing byte arrays with support for dynamic resizing,
 * reading, writing, and type conversion using custom converters.
 */
@SuppressWarnings("rawtypes")
public class ByteContainer {

    private final List<byte[]> data = new ArrayList<>();
    private int size = 0;
    private boolean changed = false;
    private HashMap<Class<?>, ByteContainerConverter> converters = new HashMap<>();
    private int writeCursor = 0;
    private int readCursor = 0;

    /**
     * Default constructor for ByteContainer.
     */
    public ByteContainer() {

    }

    /**
     * Constructs a ByteContainer with a specified initial size.
     *
     * @param size the initial size of the container.
     */
    public ByteContainer(int size) {
        this.size = size;
        this.data.add(new byte[size]);
    }

    public static ByteContainer create() {
        return new ByteContainer().withConverters(
                new IntConverter(),
                new StringConverter(),
                new ByteArrayConverter(),
                new LongConverter(),
                new UUIDConverter(),
                new MessageTypeConverter(),
                new BackupTypeConverter(),
                new BooleanConverter());
    }

    /**
     * Creates a shallow copy of the current ByteContainer.
     *
     * @return a cloned ByteContainer instance.
     */
    @SuppressWarnings("MethodDoesntCallSuperMethod")
    @Override
    public ByteContainer clone() {
        ByteContainer clone = new ByteContainer();
        clone.converters = this.converters;
        return clone;
    }

    /**
     * Adds converters to the ByteContainer for handling specific types.
     *
     * @param offeredConverters the converters to add.
     * @return the current ByteContainer instance.
     */
    public ByteContainer withConverters(ByteContainerConverter... offeredConverters) {
        for (var converter : offeredConverters) {
            converters.put(converter.getType(), converter);
        }
        return this;
    }

    /**
     * Calculates the internal offsets for a given range of bytes.
     *
     * @param offset the starting offset.
     * @param length the length of the range.
     * @return a list of ByteContainerStructure objects representing the offsets.
     */
    private List<ByteContainerStructure> getInternalOffsets(int offset, int length) {
        var result = new ArrayList<ByteContainerStructure>();
        var currentOffset = 0;
        var currentLength = length;
        for (var dataIndex = 0; dataIndex < data.size(); dataIndex++) {
            var bytes = data.get(dataIndex);
            if (offset >= currentOffset && offset < (currentOffset + bytes.length)) {
                var internalIndex = offset - currentOffset;
                var di = new ByteContainerStructure(dataIndex, internalIndex);
                if (bytes.length >= (internalIndex + currentLength)) {
                    di.length = currentLength;
                    result.add(di);
                    return result;
                } else {
                    di.length = bytes.length - internalIndex;
                    currentLength -= di.length;
                    result.add(di);
                }
            }
            currentOffset += bytes.length;
        }
        if (currentLength > 0) {
            data.add(new byte[currentLength]);
            var remain = new ByteContainerStructure(data.size() - 1, 0);
            remain.length = currentLength;
            result.add(remain);
        }
        return result;
    }

    /**
     * Returns the total size of the ByteContainer.
     *
     * @return the size of the container.
     */
    public int size() {
        return size;
    }

    /**
     * Reads a range of bytes from the container.
     *
     * @param offset the starting offset.
     * @param length the number of bytes to read.
     * @return the read bytes as a byte array.
     * @throws IndexOutOfBoundsException if the range exceeds the container size.
     */
    public byte[] read(int offset, int length) {
        if ((offset + length) > size) {
            throw new IndexOutOfBoundsException();
        }
        var data = getBytes();
        var result = new byte[length];
        System.arraycopy(data, offset, result, 0, length);
        return result;
    }

    /**
     * Writes a single byte to the container at the current write-cursor position.
     *
     * @param data the byte to write.
     */
    public void write(byte data) {
        write(data, writeCursor);
        writeCursor++;
    }

    /**
     * Clears the container, resetting all internal states.
     */
    public void clear() {
        writeCursor = 0;
        readCursor = 0;
        changed = false;
        size = 0;
        data.clear();
    }

    /**
     * Reads a single byte from the container at the current read cursor position.
     *
     * @return the read byte.
     */
    public byte read() {
        var result = read(1);
        return result[0];
    }

    /**
     * Reads all remaining bytes from the current read cursor to the end.
     *
     * @return the remaining bytes as a byte array.
     */
    public byte[] readToEnd() {
        var len = size - readCursor;
        return read(len);
    }

    /**
     * Returns the number of bytes remaining to be read.
     *
     * @return the remaining byte count.
     */
    public int getRemaining() {
        return size - readCursor;
    }

    /**
     * Returns the current write-cursor position.
     *
     * @return the write-cursor position.
     */
    public int getWriteCursor() {
        return writeCursor;
    }

    /**
     * Resets the write-cursor to the beginning.
     */
    public void resetWriteCursor() {
        writeCursor = 0;
    }

    /**
     * Resets the read cursor to the beginning.
     */
    public void resetReadCursor() {
        readCursor = 0;
    }

    /**
     * Returns the current read cursor position.
     *
     * @return the read cursor position.
     */
    public int getReadCursor() {
        return readCursor;
    }

    /**
     * Reads a specified number of bytes from the current read cursor position.
     *
     * @param length the number of bytes to read.
     * @return the read bytes as a byte array.
     * @throws IndexOutOfBoundsException if the range exceeds the container size.
     */
    public byte[] read(int length) {
        if ((readCursor + length) > size) {
            throw new IndexOutOfBoundsException();
        }
        var data = getBytes();
        var result = new byte[length];
        System.arraycopy(data, readCursor, result, 0, length);
        readCursor += length;
        return result;
    }

    /**
     * Writes an object to the container using a registered converter.
     *
     * @param toWrite the object to write.
     * @param <T>     the type of the object.
     * @throws ClassCastException if no converter is registered for the object's type.
     */
    @SuppressWarnings("unchecked")
    public <T> void writeType(T toWrite) {
        var converter = converters.get(toWrite.getClass());
        if (converter != null) {
            var bytes = converter.toBytes(toWrite);
            this.write(bytes);
            return;
        }
        throw new ClassCastException();
    }

    /**
     * Writes an object to the container at a specific offset using a registered converter.
     *
     * @param toWrite the object to write.
     * @param offset  the offset at which to write the object.
     * @param <T>     the type of the object.
     * @throws ClassCastException if no converter is registered for the object's type.
     */
    @SuppressWarnings("unchecked")
    public <T> void writeType(T toWrite, int offset) {
        var converter = converters.get(toWrite.getClass());
        if (converter != null) {
            var bytes = converter.toBytes(toWrite);
            this.write(bytes, offset, converter.getSize());
            return;
        }
        throw new ClassCastException();
    }

    /**
     * Reads an object of a specified type from the container using a registered converter.
     *
     * @param type the class of the object to read.
     * @param <T>  the type of the object.
     * @return the read object.
     * @throws ClassCastException if no converter is registered for the specified type.
     */
    @SuppressWarnings("unchecked")
    public <T> T readType(Class<T> type) {
        var converter = converters.get(type);
        if (converter != null) {
            var size = converter.getSize(this, -1);
            var bytes = this.read(size);
            return (T) converter.fromBytes(bytes);
        }
        throw new ClassCastException();
    }

    /**
     * Reads an object of a specified type from a specific offset using a registered converter.
     *
     * @param type   the class of the object to read.
     * @param offset the offset at which to read the object.
     * @param <T>    the type of the object.
     * @return the read object.
     * @throws ClassCastException if no converter is registered for the specified type.
     */
    @SuppressWarnings("unchecked")
    public <T> T readType(Class<T> type, int offset) {
        var converter = converters.get(type);
        if (converter != null) {
            var size = converter.getSize(this, offset);
            var bytes = this.read(offset, size);
            return (T) converter.fromBytes(bytes);
        }
        throw new ClassCastException();
    }

    /**
     * Writes a byte array to the container at the current write-cursor position.
     *
     * @param data the byte array to write.
     */
    public ByteContainer write(byte[] data) {
        write(data, writeCursor, data.length);
        writeCursor += data.length;
        return this;
    }

    /**
     * Writes a portion of a byte array to the container at a specific offset.
     *
     * @param buf    the byte array to write from.
     * @param offset the offset at which to write.
     * @param length the number of bytes to write.
     * @throws IllegalArgumentException       if the length exceeds the buffer size.
     * @throws ArrayIndexOutOfBoundsException if the offset exceeds the container size.
     */
    public ByteContainer write(byte[] buf, int offset, int length) {
        if (length > buf.length) {
            throw new IllegalArgumentException("Length exceeds buffer size.");
        }
        if (offset > size) {
            throw new ArrayIndexOutOfBoundsException();
        }

        if (offset == size || size == 0) {
            data.add(buf);
            size += length;
            changed = true;
            return this;
        }
        var remainingLength = length;
        var sourceOffset = 0;

        var internalOffset = getInternalOffsets(offset, length);
        for (var io : internalOffset) {
            var dataItem = data.get(io.dataIndex);
            System.arraycopy(buf, sourceOffset, dataItem, io.internalIndex, io.length);
            sourceOffset += io.length;
            remainingLength -= io.length;
            if (remainingLength == 0) {
                break;
            }
        }
        size = data.stream().mapToInt(bytes -> bytes.length).sum();
        return this;
    }

    /**
     * Writes a single byte to the container at a specific offset.
     *
     * @param value  the byte to write.
     * @param offset the offset at which to write.
     */
    public void write(byte value, int offset) {
        write(new byte[]{value}, offset, 1);
    }

    /**
     * Removes a portion of the container's data and returns it as a new ByteContainer.
     *
     * @param offset the starting offset of the portion to remove.
     * @param length the length of the portion to remove.
     * @return a new ByteContainer containing the removed portion.
     * @throws IndexOutOfBoundsException if the range exceeds the container size.
     */
    public ByteContainer splice(int offset, int length) {
        var data = getBytes();
        if (size < offset + length) {
            throw new IndexOutOfBoundsException();
        }
        var resultData = new byte[length];
        System.arraycopy(data, offset, resultData, 0, length);
        var result = clone();
        result.write(resultData);

        if (offset == 0) {
            var trailing = new byte[size - length];
            System.arraycopy(data, length, trailing, 0, trailing.length);
            clear();
            write(trailing);
        } else {
            var lastLen = data.length - offset - length;
            var prefix = new byte[offset + lastLen];
            System.arraycopy(data, 0, prefix, 0, offset);
            System.arraycopy(data, length + offset, prefix, offset, lastLen);
            clear();
            write(prefix);
        }
        return result;
    }

    /**
     * Returns the entire content of the container as a single byte array.
     *
     * @return the container's content as a byte array.
     */
    public byte[] getBytes() {
        if (changed && data.size() > 1) {
            var result = new byte[size];
            var offset = 0;
            for (byte[] b : data) {
                System.arraycopy(b, 0, result, offset, b.length);
                offset += b.length;
            }
            data.clear();
            data.add(result);
        }

        if (data.size() == 1) {
            return data.get(0);
        } else {
            return new byte[]{};
        }
    }
}
