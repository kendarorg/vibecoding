package org.kendar.sync.lib.buffer.converters;

public class BooleanConverter extends ByteContainerConverter<Boolean> {
    @Override
    public Class<Boolean> getType() {
        return Boolean.class;
    }

    @Override
    public Boolean fromBytes(byte[] bytes) {
        return bytes[0]>0;
    }

    @Override
    public byte[] toBytes(Boolean value) {
        return new byte[]{value? (byte)1:0};
    }

    @Override
    public int getSize() {
        return 1;
    }
}
