package org.kendar.sync.lib.buffer.converters;

import org.kendar.sync.lib.protocol.MessageType;

public class MessageTypeConverter extends ByteContainerConverter<MessageType> {
    @Override
    public Class<MessageType> getType() {
        return MessageType.class;
    }

    @Override
    public MessageType fromBytes(byte[] bytes) {
        return MessageType.fromCode(new String(bytes));
    }

    @Override
    public byte[] toBytes(MessageType value) {
        return new byte[]{(byte) value.getCode().charAt(0), (byte) value.getCode().charAt(1)};
    }

    @Override
    public int getSize() {
        return 2;
    }
}
