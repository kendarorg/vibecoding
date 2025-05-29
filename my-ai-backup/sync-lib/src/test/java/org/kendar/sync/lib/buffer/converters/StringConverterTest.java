package org.kendar.sync.lib.buffer.converters;

import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.buffer.ByteContainer;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StringConverterTest {

    @Test
    void testGetType() {
        StringConverter converter = new StringConverter();
        assertEquals(String.class, converter.getType());
    }

    @Test
    void testFromBytes() {
        StringConverter converter = new StringConverter();
        String expected = "Hello World";
        byte[] bytes = expected.getBytes(StandardCharsets.UTF_8);

        String result = converter.fromBytes(bytes);
        assertEquals(expected, result);
    }

    @Test
    void testToBytes() {
        StringConverter converter = new StringConverter();
        String input = "Test String";
        byte[] result = converter.toBytes(input);

        // Structure: 'S' + length(4 bytes) + data
        assertEquals('S', result[0]);

        // Extract the length from the result
        IntConverter intConverter = new IntConverter();
        byte[] lengthBytes = new byte[4];
        System.arraycopy(result, 1, lengthBytes, 0, 4);
        int length = intConverter.fromBytes(lengthBytes);

        assertEquals(input.length(), length);

        // Extract and verify the actual string data
        byte[] stringBytes = new byte[length];
        System.arraycopy(result, 5, stringBytes, 0, length);
        assertEquals(input, new String(stringBytes, StandardCharsets.UTF_8));
    }

    @Test
    void testGetSize() {
        StringConverter converter = new StringConverter();
        assertEquals(0, converter.getSize());
    }

    @Test
    void testGetSizeWithoutOffset() {
        StringConverter converter = new StringConverter();
        ByteContainer container = mock(ByteContainer.class);

        when(container.read()).thenReturn((byte) 'S');

        IntConverter intConverter = new IntConverter();
        byte[] sizeBytes = intConverter.toBytes(20);
        when(container.read(4)).thenReturn(sizeBytes);

        assertEquals(20, converter.getSize(container, -1));
    }

    @Test
    void testGetSizeWithInvalidType() {
        StringConverter converter = new StringConverter();
        ByteContainer container = mock(ByteContainer.class);

        when(container.read()).thenReturn((byte) 'X');

        IntConverter intConverter = new IntConverter();
        byte[] sizeBytes = intConverter.toBytes(20);
        when(container.read(4)).thenReturn(sizeBytes);

        assertThrows(RuntimeException.class, () -> converter.getSize(container, -1));
    }
}