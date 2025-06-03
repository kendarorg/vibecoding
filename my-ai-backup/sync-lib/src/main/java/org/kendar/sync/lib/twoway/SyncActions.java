package org.kendar.sync.lib.twoway;

import java.util.ArrayList;
import java.util.List;

public class SyncActions {
    public final List<SyncItem> filesToUpdate = new ArrayList<>();
    public final List<SyncItem> filesToSend = new ArrayList<>();
    public final List<String> filesToDelete = new ArrayList<>();
    public final List<String> filesToDeleteRemote = new ArrayList<>();
    public final List<ConflictItem> conflicts = new ArrayList<>();

    public List<SyncItem> getFilesToUpdate() {
        return filesToUpdate;
    }

    public List<SyncItem> getFilesToSend() {
        return filesToSend;
    }

    public List<String> getFilesToDelete() {
        return filesToDelete;
    }

    public List<String> getFilesToDeleteRemote() {
        return filesToDeleteRemote;
    }

    public List<ConflictItem> getConflicts() {
        return conflicts;
    }
}
