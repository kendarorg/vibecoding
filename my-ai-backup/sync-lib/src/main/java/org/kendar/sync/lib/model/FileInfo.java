package org.kendar.sync.lib.model;

import org.kendar.sync.lib.utils.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/**
 * Represents metadata about a file.
 */
public class FileInfo {
    private String path;
    private String relativePath;
    private long size;
    private Instant creationTime;
    private Instant modificationTime;
    private boolean isDirectory;

    // Default constructor for Jackson
    public FileInfo() {
    }

    /**
     * Creates a new file info object.
     *
     * @param path             The absolute path of the file
     * @param relativePath     The path relative to the base directory
     * @param size             The size of the file in bytes
     * @param creationTime     The creation time of the file
     * @param modificationTime The last modification time of the file
     * @param isDirectory      Whether the file is a directory
     */
    public FileInfo(String path, String relativePath, long size, Instant creationTime,
                    Instant modificationTime, boolean isDirectory) {
        this.path = path;
        this.relativePath = relativePath;
        this.size = size;
        this.creationTime = creationTime;
        this.modificationTime = modificationTime;
        this.isDirectory = isDirectory;
    }

    /**
     * Creates a FileInfo object from a file.
     *
     * @param file    The file
     * @param baseDir The base directory for calculating the relative path
     * @return A new FileInfo object
     * @throws IOException If an I/O error occurs
     */
    public static FileInfo fromFile(File file, String baseDir) throws IOException {
        Path filePath = file.toPath();
        Path basePath = Paths.get(baseDir).toAbsolutePath();
        Path relativePath = basePath.relativize(filePath.toAbsolutePath());

        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

        return new FileInfo(
                file.getAbsolutePath(),
                FileUtils.makeUniformPath(relativePath.toString()),
                file.length(),
                attrs.creationTime().toInstant(),
                attrs.lastModifiedTime().toInstant(),
                file.isDirectory()
        );
    }

    public static FileInfo fromLine(String fileLine) {
        try {
            String[] parts = fileLine.split("\t");
            if (parts.length < 5) {
                throw new IllegalArgumentException("Invalid file line format: " + fileLine);
            }
            var dtf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            String relativePath = parts[0];
            long size = Long.parseLong(parts[1]);
            Instant creationTime = dtf.parse(parts[2]).toInstant();
            Instant modificationTime = dtf.parse(parts[3]).toInstant();
            boolean isDirectory = Boolean.parseBoolean(parts[4]);
            return new FileInfo(
                    null, // path is not needed here
                    relativePath,
                    size,
                    creationTime,
                    modificationTime,
                    isDirectory
            );
        }catch (Exception ex){
            throw new RuntimeException(ex);
        }
    }

    /**
     * Gets the file represented by this FileInfo.
     *
     * @param baseDir The base directory to resolve the relative path against
     * @return The file
     */
    public File toFile(String baseDir) {
        return new File(baseDir, relativePath);
    }

    // Getters and setters
    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
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

    public boolean isDirectory() {
        return isDirectory;
    }

    public void setDirectory(boolean directory) {
        isDirectory = directory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileInfo fileInfo = (FileInfo) o;
        return Objects.equals(relativePath, fileInfo.relativePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relativePath);
    }

    @Override
    public String toString() {
        return "FileInfo{" +
                "relativePath='" + relativePath + '\'' +
                ", size=" + size +
                ", modificationTime=" + modificationTime +
                '}';
    }

    public String toLine() {
        var dtf = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
        return String.join("\t",
                relativePath,
                String.valueOf(size),
                dtf.format(new Date(creationTime.toEpochMilli())),
                dtf.format(new Date(modificationTime.toEpochMilli())),
                String.valueOf(isDirectory)
        );
    }
}