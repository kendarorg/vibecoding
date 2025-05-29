package org.kendar.sync.lib.buffer.converters;

import org.junit.jupiter.api.Test;
import org.kendar.sync.lib.protocol.MessageType;

import static org.junit.jupiter.api.Assertions.assertEquals;


class MessageTypeConverterTest {

    @Test
    void testGetType() {
        MessageTypeConverter converter = new MessageTypeConverter();
        assertEquals(MessageType.class, converter.getType());
    }

    @Test
    void testFromBytes() {
        MessageTypeConverter converter = new MessageTypeConverter();
        byte[] bytes = "CN".getBytes();
        MessageType result = converter.fromBytes(bytes);
        assertEquals("CN", result.getCode());
    }

    @Test
    void testToBytes() {
        MessageTypeConverter converter = new MessageTypeConverter();
        MessageType messageType = MessageType.SYNC_END_ACK;
        byte[] result = converter.toBytes(messageType);

        assertEquals(2, result.length);
        assertEquals('S', result[0]);
        assertEquals('A', result[1]);
    }

    @Test
    void testGetSize() {
        MessageTypeConverter converter = new MessageTypeConverter();
        assertEquals(2, converter.getSize());
    }
}