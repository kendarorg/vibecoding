package org.kendar.sync.lib.buffer.converters;

import java.util.UUID;

public class UUIDConverter extends ByteContainerConverter<UUID> {
    private static final LongConverter longConverter = new LongConverter();

    @Override
    public Class<UUID> getType() {
        return UUID.class;
    }

    @Override
    public UUID fromBytes(byte[] bytes) {

        var msb = longConverter.fromBytes(bytes);
        var lsbBytes = new byte[]{bytes[8], bytes[9], bytes[10], bytes[11], bytes[12], bytes[13], bytes[14], bytes[15]};
        var lsb = longConverter.fromBytes(lsbBytes);
        return new UUID(msb, lsb);
    }

    @Override
    public byte[] toBytes(UUID value) {
        var msb = longConverter.toBytes(value.getMostSignificantBits());
        var lsb = longConverter.toBytes(value.getLeastSignificantBits());

        var result = new byte[16];
        System.arraycopy(msb, 0, result, 0, 8);
        System.arraycopy(lsb, 0, result, 8, 8);
        return result;
    }

    @Override
    public int getSize() {
        return 16;
    }
}
