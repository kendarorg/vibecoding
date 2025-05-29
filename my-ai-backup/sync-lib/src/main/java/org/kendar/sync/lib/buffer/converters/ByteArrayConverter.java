package org.kendar.sync.lib.buffer.converters;

import org.kendar.sync.lib.buffer.ByteContainer;

public class ByteArrayConverter extends ByteContainerConverter<byte[]> {
    private static final IntConverter intConverter = new IntConverter();

    @Override
    public Class<byte[]> getType() {
        return byte[].class;
    }

    @Override
    public byte[] fromBytes(byte[] bytes) {
        return bytes;
    }

    public int getSize(ByteContainer container, int offset) {
        char type;
        var intSize = intConverter.getSize();
        var size = 0;
        if (offset == -1) {
            type = (char) container.read();
            size = intConverter.fromBytes(container.read(intSize));
        } else {
            type = (char) container.read(offset, 1)[0];
            size = intConverter.fromBytes(container.read(intSize, offset));
        }
        if (type != 'B') {
            throw new RuntimeException("type is not 'B/byte[]'");
        }
        return size;
    }

    @Override
    public byte[] toBytes(byte[] data) {
        var result = new byte[data.length + 5];
        var length = intConverter.toBytes(data.length);
        result[0] = 'B';
        System.arraycopy(length, 0, result, 1, length.length);
        System.arraycopy(data, 0, result, 5, data.length);
        return result;
    }

    @Override
    public int getSize() {
        return 0;
    }
}
