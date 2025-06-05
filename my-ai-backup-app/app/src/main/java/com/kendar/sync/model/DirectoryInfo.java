package com.kendar.sync.model;

import java.io.File;

/**
 * Data model class representing directory information
 */
public class DirectoryInfo {
    private File directory;
    private int fileCount;
    private boolean isSelected;

    public DirectoryInfo(File directory, int fileCount) {
        this.directory = directory;
        this.fileCount = fileCount;
        this.isSelected = false;
    }

    public File getDirectory() {
        return directory;
    }

    public String getName() {
        return directory.getName();
    }

    public String getPath() {
        return directory.getAbsolutePath();
    }

    public int getFileCount() {
        return fileCount;
    }

    public boolean isSelected() {
        return isSelected;
    }

    public void setSelected(boolean selected) {
        isSelected = selected;
    }
}
