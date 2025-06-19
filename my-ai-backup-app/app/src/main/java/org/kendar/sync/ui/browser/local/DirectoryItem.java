package org.kendar.sync.ui.browser.local;

public class DirectoryItem {
    private final String displayName;
    private final String path;

    public DirectoryItem(String displayName, String path) {
        this.displayName = displayName;
        this.path = path;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getPath() {
        return path;
    }
}