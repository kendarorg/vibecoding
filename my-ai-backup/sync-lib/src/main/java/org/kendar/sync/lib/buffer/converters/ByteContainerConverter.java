package org.kendar.sync.lib.buffer.converters;

import org.kendar.sync.lib.buffer.ByteContainer;

public abstract class ByteContainerConverter<T> {
    public abstract Class<T> getType();

    public abstract T fromBytes(byte[] bytes);

    public abstract byte[] toBytes(T bytes);

    public abstract int getSize();

    public int getSize(ByteContainer container, int offset) {
        return getSize();
    }
}
