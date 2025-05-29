package org.kendar.sync.lib.buffer.converters;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UUIDConverterTest {

    @Test
    void testGetType() {
        UUIDConverter converter = new UUIDConverter();
        assertEquals(UUID.class, converter.getType());
    }

    @Test
    void testRoundTrip() {
        UUIDConverter converter = new UUIDConverter();
        UUID original = UUID.randomUUID();

        byte[] bytes = converter.toBytes(original);
        UUID result = converter.fromBytes(bytes);

        assertEquals(original, result);
    }

    @Test
    void testFromBytes() {
        UUIDConverter converter = new UUIDConverter();
        long msb = 0x1234567890ABCDEFL;
        long lsb = 0xFEDCBA0987654321L;
        UUID expected = new UUID(msb, lsb);

        LongConverter longConverter = new LongConverter();
        byte[] msbBytes = longConverter.toBytes(msb);
        byte[] lsbBytes = longConverter.toBytes(lsb);

        byte[] combined = new byte[16];
        System.arraycopy(msbBytes, 0, combined, 0, 8);
        System.arraycopy(lsbBytes, 0, combined, 8, 8);

        UUID result = converter.fromBytes(combined);

        assertEquals(expected, result);
    }

    @Test
    void testToBytes() {
        UUIDConverter converter = new UUIDConverter();
        long msb = 0x1234567890ABCDEFL;
        long lsb = 0xFEDCBA0987654321L;
        UUID uuid = new UUID(msb, lsb);

        byte[] result = converter.toBytes(uuid);

        assertEquals(16, result.length);

        LongConverter longConverter = new LongConverter();
        byte[] msbBytes = new byte[8];
        byte[] lsbBytes = new byte[8];
        System.arraycopy(result, 0, msbBytes, 0, 8);
        System.arraycopy(result, 8, lsbBytes, 0, 8);

        assertEquals(msb, longConverter.fromBytes(msbBytes));
        assertEquals(lsb, longConverter.fromBytes(lsbBytes));
    }

    @Test
    void testGetSize() {
        UUIDConverter converter = new UUIDConverter();
        assertEquals(16, converter.getSize());
    }
}