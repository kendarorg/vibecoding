package org.kendar.sync.lib.protocol;

import org.kendar.sync.lib.buffer.ByteContainer;
import org.kendar.sync.lib.twoway.LogEntry;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Message sent by the client to the server with a list of files in the source directory.
 * Used during both backup and restore operations.
 */
public class FileSyncMessage extends Message {
    static {
        Message.registerMessageType(FileSyncMessage.class);
    }

    private int partNumber;
    private int totalParts;
    private List<LogEntry> changes;
    private Instant lastlyUpdateTime;

    // Default constructor for Jackson
    public FileSyncMessage() {
        this.changes = new ArrayList<>();
    }


    @Override
    public MessageType getMessageType() {
        return MessageType.FILE_SYNC;
    }

    @Override
    protected Message deserialize(ByteContainer buffer) {
        var filesLines = buffer.readType(String.class).split("\n");
        changes = new ArrayList<>();
        for (var fileLine : filesLines) {
            if (!fileLine.isEmpty()) {
                changes.add(LogEntry.fromLine(fileLine));
            }
        }
        partNumber = buffer.readType(Integer.class);
        totalParts = buffer.readType(Integer.class);
        return this;
    }

    @Override
    protected void serialize(ByteContainer buffer) {
        var filesLines = changes.stream()
                .map(LogEntry::toLine)
                .collect(Collectors.toList());
        buffer.writeType(String.join("\n", filesLines));
        buffer.writeType(partNumber);
        buffer.writeType(totalParts);
    }


    public int getPartNumber() {
        return partNumber;
    }

    public void setPartNumber(int partNumber) {
        this.partNumber = partNumber;
    }

    public int getTotalParts() {
        return totalParts;
    }

    public void setTotalParts(int totalParts) {
        this.totalParts = totalParts;
    }

    public List<LogEntry> getChanges() {
        return changes;
    }

    public void setChanges(List<LogEntry> changes) {
        this.changes = changes;
    }

    public Instant getLastlyUpdateTime() {
        return lastlyUpdateTime;
    }

    public void setLastlyUpdateTime(Instant lastlyUpdateTime) {
        this.lastlyUpdateTime = lastlyUpdateTime;
    }
}