package org.kendar.sync.lib.buffer.converters;

import org.kendar.sync.lib.protocol.BackupType;

public class BackupTypeConverter extends ByteContainerConverter<BackupType> {
    @Override
    public Class<BackupType> getType() {
        return BackupType.class;
    }

    @Override
    public BackupType fromBytes(byte[] bytes) {
        var str = new String(bytes);
        if (str.equalsIgnoreCase("PR")) return BackupType.PRESERVE;
        if (str.equalsIgnoreCase("MI")) return BackupType.MIRROR;
        if (str.equalsIgnoreCase("NO")) return BackupType.NONE;
        if (str.equalsIgnoreCase("DA")) return BackupType.DATE_SEPARATED;
        if (str.equalsIgnoreCase("TW")) return BackupType.TWO_WAY_SYNC;
        return BackupType.valueOf(new String(bytes));
    }

    @Override
    public byte[] toBytes(BackupType value) {
        if (value == BackupType.PRESERVE) return new byte[]{(byte) 'P', (byte) 'R'};
        if (value == BackupType.MIRROR) return new byte[]{(byte) 'M', (byte) 'I'};
        if (value == BackupType.NONE) return new byte[]{(byte) 'N', (byte) 'O'};
        if (value == BackupType.DATE_SEPARATED) return new byte[]{(byte) 'D', (byte) 'A'};
        if (value == BackupType.TWO_WAY_SYNC) return new byte[]{(byte) 'T', (byte) 'W'};
        return new byte[]{(byte) value.name().charAt(0), (byte) value.name().charAt(1)};
    }

    @Override
    public int getSize() {
        return 2;
    }
}
