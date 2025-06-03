package org.kendar.sync.lib.twoway;

public class ConflictItem {
    public final String relativePath;
    public final LogEntry localEntry;
    public final LogEntry remoteEntry;

    public ConflictItem(String relativePath, LogEntry localEntry, LogEntry remoteEntry) {
        this.relativePath = relativePath;
        this.localEntry = localEntry;
        this.remoteEntry = remoteEntry;
    }
}
