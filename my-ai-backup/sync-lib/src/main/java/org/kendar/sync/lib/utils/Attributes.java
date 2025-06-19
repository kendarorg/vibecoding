package org.kendar.sync.lib.utils;

import java.time.Instant;

public class Attributes {
    private final int umask;
    private Instant creationTime;
    private Instant modificationTime;
    private long size;

    public Instant getCreationTime() {
        return creationTime;
    }

    public Instant getModificationTime() {
        return modificationTime;
    }

    public long getSize() {
        return size;
    }

    public Attributes(int umask) {
        this.umask = umask;
    }

    public Attributes(int umask, Instant creationTime, Instant modificationTime, long size) {
        this.umask = umask;
        this.creationTime = creationTime;
        this.modificationTime = modificationTime;
        this.size = size;
    }
    public int getBaseUmask() {
        return umask & 0x0777;
    }
    public boolean isReadable() {
        return (0x444 & getBaseUmask()) != 0;
    }
    public boolean isExecutable() {
        return (0x111 & getBaseUmask()) != 0;
    }
    public boolean isWritable() {
        return (0x222 & getBaseUmask()) != 0;
    }
    public boolean isHidden() {
        return (0x1000 & umask) != 0;
    }
    public boolean isSystem() {
        return (0x4000 & umask) != 0;
    }
    public boolean isSymbolicLink(){
        return (0x2000 & umask) != 0;
    }
    public boolean isDirectory(){
        return (0x8000 & umask) != 0;
    }
    public int getExtendedUmask() {
        return umask;
    }

    public static boolean isDirectory(int umask) {
        return (0x8000 & umask) != 0;
    }
}
