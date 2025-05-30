package org.kendar.sync.lib.buffer;

class ByteContainerStructure {
    public final int dataIndex;
    public final int internalIndex;
    public int length;

    public ByteContainerStructure(int dataIndex, int internalIndex) {
        this.dataIndex = dataIndex;
        this.internalIndex = internalIndex;
    }

    @Override
    public String toString() {
        return "DataAndIndex{" +
                "dataIndex=" + dataIndex +
                ", internalIndex=" + internalIndex +
                '}';
    }
}
