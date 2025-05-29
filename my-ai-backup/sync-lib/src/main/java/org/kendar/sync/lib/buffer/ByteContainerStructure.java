package org.kendar.sync.lib.buffer;

class ByteContainerStructure {
    public int length;
    public int dataIndex;
    public int internalIndex;

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
