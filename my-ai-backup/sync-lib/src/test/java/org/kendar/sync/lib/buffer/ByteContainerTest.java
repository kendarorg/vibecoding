package org.kendar.sync.lib.buffer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.buffer.converters.*;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ByteContainerTest {

    private ByteContainer container;

    @BeforeEach
    void setUp() {
        container = new ByteContainer().withConverters(
                new IntConverter(),
                new StringConverter(),
                new ByteArrayConverter(),
                new LongConverter(),
                new UUIDConverter(),
                new MessageTypeConverter()
        );
    }

    @Test
    void constructor_withSize_shouldCreateContainerWithSpecifiedSize() {
        ByteContainer sizedContainer = new ByteContainer(10);
        assertEquals(10, sizedContainer.size());
        assertEquals(0, sizedContainer.getReadCursor());
        assertEquals(0, sizedContainer.getWriteCursor());
    }

    @Test
    void clone_shouldCreateShallowCopy() {
        ByteContainer cloned = container.clone();
        assertNotSame(container, cloned);
        assertEquals(0, cloned.size());
        assertEquals(0, cloned.getReadCursor());
        assertEquals(0, cloned.getWriteCursor());
    }

    @Test
    void write_singleByte_shouldIncrementWriteCursor() {
        container.write((byte) 0x42);
        assertEquals(1, container.size());
        assertEquals(1, container.getWriteCursor());

        byte[] result = container.getBytes();
        assertEquals(0x42, result[0]);
    }

    @Test
    void write_byteArrayWithOffset_shouldAppendBytes() {
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        container.write(data, 0, 2);
        assertEquals(2, container.size());
        byte[] result = container.read(2);
        assertArrayEquals(new byte[]{0x01, 0x02}, result);
    }

    @Test
    void write_byteArrayWithoutOffset_shouldAppendEntireArray() {
        byte[] data = {0x01, 0x02, 0x03};
        container.write(data);
        assertEquals(3, container.size());
        assertEquals(3, container.getWriteCursor());

        byte[] result = container.getBytes();
        assertArrayEquals(data, result);
    }

    @Test
    void write_beyondCurrentSize_shouldThrowException() {
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> {
            container.write(new byte[]{0x01}, 1, 1);
        });
    }

    @Test
    void read_withOffsetAndLength_shouldReturnCorrectBytes() {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
        container.write(data);

        byte[] result = container.read(1, 3);
        assertArrayEquals(new byte[]{0x02, 0x03, 0x04}, result);
        assertEquals(0, container.getReadCursor()); // Should not change read cursor
    }

    @Test
    void read_withLength_shouldIncrementReadCursor() {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
        container.write(data);

        byte[] result = container.read(2);
        assertArrayEquals(new byte[]{0x01, 0x02}, result);
        assertEquals(2, container.getReadCursor());

        result = container.read(2);
        assertArrayEquals(new byte[]{0x03, 0x04}, result);
        assertEquals(4, container.getReadCursor());
    }

    @Test
    void read_singleByte_shouldReadCorrectByte() {
        byte[] data = {0x01, 0x02, 0x03};
        container.write(data);

        byte result = container.read();
        assertEquals(0x01, result);
        assertEquals(1, container.getReadCursor());
    }

    @Test
    void readToEnd_shouldReadAllRemainingBytes() {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
        container.write(data);
        container.read(2); // Advance cursor

        byte[] result = container.readToEnd();
        assertArrayEquals(new byte[]{0x03, 0x04, 0x05}, result);
        assertEquals(5, container.getReadCursor());
    }

    @Test
    void cursor_resetCursors_shouldResetToZero() {
        byte[] data = {0x01, 0x02, 0x03};
        container.write(data);
        container.read(2);

        assertEquals(3, container.getWriteCursor());
        assertEquals(2, container.getReadCursor());

        container.resetWriteCursor();
        container.resetReadCursor();

        assertEquals(0, container.getWriteCursor());
        assertEquals(0, container.getReadCursor());
    }

    @Test
    void getRemaining_shouldReturnCorrectCount() {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
        container.write(data);
        container.read(2);

        assertEquals(3, container.getRemaining());

        container.read(1);
        assertEquals(2, container.getRemaining());
    }

    @Test
    void clear_shouldResetAllState() {
        byte[] data = {0x01, 0x02, 0x03};
        container.write(data);
        container.read(1);

        container.clear();

        assertEquals(0, container.size());
        assertEquals(0, container.getReadCursor());
        assertEquals(0, container.getWriteCursor());
    }

    @Test
    void writeType_shouldWriteObjectUsingConverter() {
        container.writeType(42);
        assertEquals(4, container.size());

        container.resetReadCursor();
        int result = container.readType(Integer.class);
        assertEquals(42, result);
    }

    @Test
    void writeType_withOffset_shouldWriteAtSpecifiedPosition() {
        container.write(new byte[]{0, 0, 0, 0, 0, 0});
        container.writeType(42, 1);

        int result = container.readType(Integer.class, 1);
        assertEquals(42, result);
    }

    @Test
    void writeType_withInvalidType_shouldThrowException() {
        assertThrows(ClassCastException.class, () -> {
            container.writeType(new Object());
        });
    }

    @Test
    void readType_shouldReadObjectUsingConverter() {
        container.writeType("Hello");
        container.resetReadCursor();

        String result = container.readType(String.class);
        assertEquals("Hello", result);
    }

    @Test
    void splice_fromBeginning_shouldExtractAndRemoveBytes() {
        byte[] data = {0x01, 0x02, 0x03, 0x04};
        container.write(data);

        ByteContainer spliced = container.splice(0, 2);

        assertArrayEquals(new byte[]{0x01, 0x02}, spliced.getBytes());
        assertArrayEquals(new byte[]{0x03, 0x04}, container.getBytes());
    }

    @Test
    void splice_fromMiddle_shouldExtractAndRemoveBytes() {
        byte[] data = {0x01, 0x02, 0x03, 0x04, 0x05};
        container.write(data);

        ByteContainer spliced = container.splice(1, 3);

        assertArrayEquals(new byte[]{0x02, 0x03, 0x04}, spliced.getBytes());
        assertArrayEquals(new byte[]{0x01, 0x05}, container.getBytes());
    }

    @Test
    void getBytes_shouldReturnCompactByteArray() {
        byte[] data1 = {0x01, 0x02};
        byte[] data2 = {0x03, 0x04};
        container.write(data1);
        container.write(data2);

        byte[] result = container.getBytes();
        assertArrayEquals(new byte[]{0x01, 0x02, 0x03, 0x04}, result);
    }

    @Test
    void multipleTypeOperations_shouldHandleComplexScenario() {
        UUID uid1 = UUID.randomUUID();
        String text = "Hello World";
        int number = 42;

        container.writeType(uid1);
        container.writeType(text);
        container.writeType(number);

        container.resetReadCursor();
        UUID readUid = container.readType(UUID.class);
        String readText = container.readType(String.class);
        int readNumber = container.readType(Integer.class);

        assertEquals(uid1, readUid);
        assertEquals(text, readText);
        assertEquals(number, readNumber);
    }
}