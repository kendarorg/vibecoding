package org.kendar.sync.lib.twoway;

import org.kendar.sync.lib.model.FileInfo;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;

public class LogEntry {
     Instant runStartTime;
     Instant creationTime;
     Instant modificationTime;
     long size;
     String operation;
     String relativePath;

    LogEntry(Instant runStartTime, Instant creationTime, Instant modificationTime, long size, String operation, String relativePath) {
        this.runStartTime = runStartTime;
        this.creationTime = creationTime;
        this.modificationTime = modificationTime;
        this.size = size;
        this.operation = operation;
        this.relativePath = relativePath;
    }

    public Instant getRunStartTime() {
        return runStartTime;
    }

    public void setRunStartTime(Instant runStartTime) {
        this.runStartTime = runStartTime;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Instant creationTime) {
        this.creationTime = creationTime;
    }

    public Instant getModificationTime() {
        return modificationTime;
    }

    public void setModificationTime(Instant modificationTime) {
        this.modificationTime = modificationTime;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public String toLine() {
        var dtf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        return String.format("%s\t%s\t%s\t%d\t%s\t%s",
                dtf.format(new Date(runStartTime.toEpochMilli())),
                dtf.format(new Date(creationTime.toEpochMilli())),
                dtf.format(new Date(modificationTime.toEpochMilli())),
                size,
                operation,
                relativePath);
    }

    public static LogEntry fromLine(String fileLine) {
        try {
            String[] parts = fileLine.split("\t");
            if (parts.length < 6) {
                throw new IllegalArgumentException("Invalid file line format: " + fileLine);
            }
            var dtf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

            Instant runStartTime = dtf.parse(parts[0]).toInstant();
            Instant creationTime = dtf.parse(parts[1]).toInstant();
            Instant modificationTime = dtf.parse(parts[2]).toInstant();
            long size = Long.parseLong(parts[3]);
            String operation = parts[4];
            String relativePath = parts[5];
            return new LogEntry(
                    runStartTime,
                    creationTime,
                    modificationTime,
                    size,
                    operation,
                    relativePath
            );
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
