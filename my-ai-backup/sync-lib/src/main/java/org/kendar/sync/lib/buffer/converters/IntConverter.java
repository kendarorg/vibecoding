package org.kendar.sync.lib.buffer.converters;

public class IntConverter extends ByteContainerConverter<Integer> {
    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }

    @Override
    public Integer fromBytes(byte[] bytes) {
        return ((bytes[0] & 0xFF) << 24) |
                ((bytes[1] & 0xFF) << 16) |
                ((bytes[2] & 0xFF) << 8) |
                ((bytes[3] & 0xFF) << 0);
    }

    @Override
    public byte[] toBytes(Integer value) {
        return new byte[]{
                (byte) (value >> 24),
                (byte) (value >> 16),
                (byte) (value >> 8),
                (byte) (int) value};
    }

    @Override
    public int getSize() {
        return 4;
    }
}
