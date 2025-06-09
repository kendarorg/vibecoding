package org.kendar.sync.lib.twoway;

public class SyncItem {
    public final String relativePath;
    public final LogEntry logEntry;

    public SyncItem(String relativePath, LogEntry logEntry) {
        this.relativePath = relativePath;
        this.logEntry = logEntry;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public LogEntry getLogEntry() {
        return logEntry;
    }
}
