package org.kendar.sync.lib.buffer;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.buffer.converters.*;
import org.kendar.sync.lib.protocol.MessageType;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ByteContainerBasicTest {
    private ByteContainer target;

    protected byte[] buildArray(byte... bytes) {
        return bytes;
    }

    protected byte[] buildArray(char... chars) {
        var result = new byte[chars.length];
        for (int i = 0; i < chars.length; i++) {
            result[i] = (byte) chars[i];
        }
        return result;
    }


    @BeforeEach
    void setUp() {
        target = new ByteContainer().withConverters(
                new IntConverter(),
                new StringConverter(),
                new ByteArrayConverter(),
                new LongConverter(),
                new UUIDConverter(),
                new MessageTypeConverter());
    }

    @Test
    void simpleTestA() {

        target.write((byte) 'a', 0);
        var all = target.getBytes();
        Assertions.assertArrayEquals(buildArray('a'), all);
    }

    @Test
    void simpleTestB() {

        var src = buildArray('a', 'b');
        target.write(src, 0, src.length);
        var all = target.getBytes();
        assertArrayEquals(src, all);
    }

    @Test
    void simpleTestC() {

        var src = buildArray('a', 'b', 'c', 'd');

        target.write(buildArray('a', 'b'), 0, 2);
        target.write(buildArray('c', 'd'), 2, 2);

        var all = target.getBytes();
        assertArrayEquals(src, all);
    }

    @Test
    void writeCursorSimpleTestC() {

        var src = buildArray('a', 'b', 'c', 'd');

        target.write(buildArray('a', 'b'));
        target.write(buildArray('c', 'd'));
        assertEquals(4, target.getWriteCursor());

        var all = target.getBytes();
        assertArrayEquals(src, all);

        target.resetWriteCursor();
        target.write(buildArray('e'));
        var src2 = buildArray('e', 'b', 'c', 'd');
        var all2 = target.getBytes();
        assertArrayEquals(src2, all2);
    }

    @Test
    void simpleTestOffsetOutOfBounds() {


        target.write(buildArray('a', 'b'), 0, 2);
        assertThrows(ArrayIndexOutOfBoundsException.class, () -> target.write(buildArray('c', 'd'), 3, 2));
    }

    @Test
    void simpleTestRewriteExactOffset() {

        var src = buildArray('c', 'd');

        target.write(buildArray('a', 'b'), 0, 2);
        target.write(buildArray('c', 'd'), 0, 2);

        var all = target.getBytes();
        assertArrayEquals(src, all);
    }

    @Test
    void simpleTestRewritePartialOffset() {

        var src = buildArray('a', 'c', 'd');

        target.write(buildArray('a', 'b'), 0, 2);
        target.write(buildArray('c', 'd'), 1, 2);

        var all = target.getBytes();
        assertArrayEquals(src, all);
    }


    @Test
    void simpleTestRewritePartialOffsetLong() {

        var src = buildArray('a', 'd', 'e', 'f');

        target.write(buildArray('a', 'b', 'c'), 0, 3);
        target.write(buildArray('d', 'e', 'f'), 1, 3);

        var all = target.getBytes();
        assertArrayEquals(src, all);
    }

    @Test
    void simpleTestRewritePartialOffsetLongCover() {

        var src = buildArray('d', 'k', 'l', 'g');

        target.write(buildArray('a', 'b', 'c'), 0, 3);
        target.write(buildArray('d', 'e', 'f', 'g'), 0, 4);
        target.write(buildArray('k', 'l'), 1, 2);

        var all = target.getBytes();
        assertArrayEquals(src, all);
    }

    @Test
    void testReadCursor() {

        var src = buildArray('d', 'k', 'l', 'g');

        target.write(src);
        var base = target.read(2);
        Assertions.assertArrayEquals(buildArray('d', 'k'), base);
        base = target.read(2);
        Assertions.assertArrayEquals(buildArray('l', 'g'), base);
        assertEquals(0, target.getRemaining());
    }

    @Test
    void testReadCursorToEnd() {

        var src = buildArray('d', 'k', 'l', 'g');

        target.write(src);
        var base = target.read(2);
        Assertions.assertArrayEquals(buildArray('d', 'k'), base);
        base = target.readToEnd();
        Assertions.assertArrayEquals(buildArray('l', 'g'), base);
        assertEquals(0, target.getRemaining());
    }

    @Test
    void testReadCursorError() {

        var src = buildArray('d', 'k', 'l', 'g');

        target.write(src);
        var base = target.read(2);
        Assertions.assertArrayEquals(buildArray('d', 'k'), base);
        assertThrows(IndexOutOfBoundsException.class, () -> target.read(3));
    }

    @Test
    void testIntType() {
        target.writeType(22);
        var base = target.readType(Integer.class);
        assertEquals(22, base);
    }

    @Test
    void testStringType() {
        target.writeType("fuffa");
        var base = target.readType(String.class);
        assertEquals("fuffa", base);
    }


    @Test
    void testLongType() {
        target.writeType(154L);
        var base = target.readType(Long.class);
        assertEquals(154L, base);
    }

    @Test
    void testByteArrayType() {
        var src = buildArray('a', 'b');
        target.writeType(src);
        var all = target.readType(byte[].class);
        assertArrayEquals(src, all);
    }


    @Test
    void testUUIDType() {
        var src = UUID.randomUUID();
        target.writeType(src);
        var all = target.readType(UUID.class);
        assertEquals(src, all);
    }

    @Test
    void testRealSituation() {
        var uid1 = UUID.fromString("11111111-1111-1111-1111-111111111111");
        var uid2 = UUID.fromString("22222222-2222-2222-2222-222222222222");
        target.writeType(0);
        target.writeType(uid1);
        target.writeType(MessageType.CONNECT);
        target.writeType("login");
        target.writeType("password");
        target.writeType("targetDir");

        var len = target.size();
        target.writeType(len, 0);
        target.writeType(uid2, 4);
        assertEquals(59, target.readType(Integer.class));
        assertEquals(uid2, target.readType(UUID.class));
        target.read(2);
        assertEquals("login", target.readType(String.class));
        assertEquals("password", target.readType(String.class));
        assertEquals("targetDir", target.readType(String.class));
        System.out.println(target.size());
    }

    @Test
    void testSplice() {

        var src = buildArray('d', 'k', 'l', 'g');

        target.write(src);
        var base = target.splice(0, 2);
        Assertions.assertArrayEquals(buildArray('d', 'k'), base.getBytes());
        Assertions.assertArrayEquals(buildArray('l', 'g'), target.getBytes());
    }


    @Test
    void testSpliceMiddle() {

        var src = buildArray('d', 'k', 'l', 'g');

        target.write(src);
        var base = target.splice(1, 2);
        Assertions.assertArrayEquals(buildArray('k', 'l'), base.getBytes());
        Assertions.assertArrayEquals(buildArray('d', 'g'), target.getBytes());
    }

}
