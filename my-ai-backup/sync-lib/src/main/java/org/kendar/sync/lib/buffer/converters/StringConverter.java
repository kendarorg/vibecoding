package org.kendar.sync.lib.buffer.converters;

import org.kendar.sync.lib.buffer.ByteContainer;

import java.nio.charset.StandardCharsets;

public class StringConverter extends ByteContainerConverter<String> {
    private static final IntConverter intConverter = new IntConverter();

    @Override
    public Class<String> getType() {
        return String.class;
    }

    @Override
    public String fromBytes(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
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
        if (type != 'S') {
            throw new RuntimeException("type is not 'S/String'");
        }
        return size;
    }

    @Override
    public byte[] toBytes(String input) {
        var data = input.getBytes(StandardCharsets.UTF_8);
        var result = new byte[data.length + 5];
        var length = intConverter.toBytes(data.length);
        result[0] = 'S';
        System.arraycopy(length, 0, result, 1, length.length);
        System.arraycopy(data, 0, result, 5, data.length);
        return result;
    }

    @Override
    public int getSize() {
        return 0;
    }
}
