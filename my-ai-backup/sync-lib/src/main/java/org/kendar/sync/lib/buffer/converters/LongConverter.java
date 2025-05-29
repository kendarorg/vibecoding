package org.kendar.sync.lib.buffer.converters;

public class LongConverter extends ByteContainerConverter<Long> {

    @Override
    public Class<Long> getType() {
        return Long.class;
    }

    @Override
    public Long fromBytes(byte[] buffer) {
        return (((long) buffer[0] << 56) +
                ((long) (buffer[1] & 255) << 48) +
                ((long) (buffer[2] & 255) << 40) +
                ((long) (buffer[3] & 255) << 32) +
                ((long) (buffer[4] & 255) << 24) +
                ((buffer[5] & 255) << 16) +
                ((buffer[6] & 255) << 8) +
                ((buffer[7] & 255) << 0));
    }

    @Override
    public byte[] toBytes(Long value) {
        byte[] writeBuffer = new byte[Long.BYTES];
        writeBuffer[0] = (byte) (value >>> 56);
        writeBuffer[1] = (byte) (value >>> 48);
        writeBuffer[2] = (byte) (value >>> 40);
        writeBuffer[3] = (byte) (value >>> 32);
        writeBuffer[4] = (byte) (value >>> 24);
        writeBuffer[5] = (byte) (value >>> 16);
        writeBuffer[6] = (byte) (value >>> 8);
        writeBuffer[7] = (byte) (value >>> 0);
        return writeBuffer;
    }

    @Override
    public int getSize() {
        return 8;
    }
}
