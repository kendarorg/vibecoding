package org.kendar.sync.lib.utils;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.protocol.BackupType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Utility class for file operations.
 */
public class FileUtils {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * Lists all files in a directory recursively.
     *
     * @param directory The directory to list
     * @param baseDir The base directory for calculating relative paths
     * @return A list of file information
     * @throws IOException If an I/O error occurs
     */
    public static List<FileInfo> listFiles(File directory, String baseDir) throws IOException {
        List<FileInfo> files = new ArrayList<>();
        listFilesRecursive(directory, baseDir, files);
        return files;
    }

    /**
     * Lists all files in a directory recursively.
     *
     * @param directory The directory to list
     * @param baseDir The base directory for calculating relative paths
     * @param files The list to add the files to
     * @throws IOException If an I/O error occurs
     */
    private static void listFilesRecursive(File directory, String baseDir, List<FileInfo> files) throws IOException {
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        File[] fileList = directory.listFiles();
        if (fileList == null) {
            return;
        }

        for (File file : fileList) {
            FileInfo fileInfo = FileInfo.fromFile(file, baseDir);

            if (file.isDirectory()) {
                listFilesRecursive(file, baseDir, files);
            }else{
                files.add(fileInfo);
            }
        }
    }

    /**
     * Calculates the files that need to be transferred and deleted.
     *
     * @param sourceFiles The source files
     * @param targetFiles The target files
     * @param backupType The backup type
     * @return A map with the files to transfer and delete
     */
    public static Map<String, List<FileInfo>> calculateFileDifferences(
            List<FileInfo> sourceFiles, List<FileInfo> targetFiles, BackupType backupType) {
        Map<String, List<FileInfo>> result = new HashMap<>();

        // Create maps for faster lookup
        Map<String, FileInfo> sourceMap = sourceFiles.stream()
                .collect(Collectors.toMap(FileInfo::getRelativePath, f -> f));

        Map<String, FileInfo> targetMap = targetFiles.stream()
                .collect(Collectors.toMap(FileInfo::getRelativePath, f -> f));

        // Files to transfer: files that don't exist in the target or have different timestamps
        List<FileInfo> filesToTransfer = sourceFiles.stream()
                .filter(sourceFile -> {
                    if (sourceFile.isDirectory()) {
                        return false; // Skip directories
                    }

                    FileInfo targetFile = targetMap.get(sourceFile.getRelativePath());
                    if (targetFile == null) {
                        return true; // File doesn't exist in target
                    }

                    // Check if the file has changed
                    return !sourceFile.getModificationTime().equals(targetFile.getModificationTime())
                            || sourceFile.getSize() != targetFile.getSize();
                })
                .collect(Collectors.toList());

        result.put("transfer", filesToTransfer);

        // Files to delete: files that exist in the target but not in the source
        if (backupType == BackupType.MIRROR) {
            List<FileInfo> filesToDelete = targetFiles.stream()
                    .filter(targetFile -> !targetFile.isDirectory() && !sourceMap.containsKey(targetFile.getRelativePath()))
                    .collect(Collectors.toList());

            result.put("delete", filesToDelete);
        } else {
            result.put("delete", new ArrayList<>());
        }

        return result;
    }

    /**
     * Gets the target path for a file based on the backup type.
     *
     * @param file The file
     * @param targetDir The target directory
     * @param backupType The backup type
     * @return The target path
     */
    public static String getTargetPath(FileInfo file, String targetDir, BackupType backupType) {
        if (backupType != BackupType.DATE_SEPARATED) {
            return targetDir;
        }

        // For DATE_SEPARATED, create a directory structure based on the file's modification date
        LocalDate date = LocalDate.ofInstant(file.getModificationTime(), ZoneId.systemDefault());
        String dateDir = date.format(DATE_FORMATTER);

        return Paths.get(targetDir, dateDir).toString();
    }

    /**
     * Creates a directory if it doesn't exist.
     *
     * @param directory The directory to create
     * @throws IOException If an I/O error occurs
     */
    public static void createDirectoryIfNotExists(File directory) throws IOException {
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                throw new IOException("Failed to create directory: " + directory);
            }
        }
    }

    /**
     * Sets the file times.
     *
     * @param file The file
     * @param creationTime The creation time
     * @param modificationTime The modification time
     * @throws IOException If an I/O error occurs
     */
    public static void setFileTimes(File file, Instant creationTime, Instant modificationTime) throws IOException {
        Path path = file.toPath();
        Files.setAttribute(path, "creationTime", FileTime.from(creationTime));
        Files.setAttribute(path, "lastModifiedTime", FileTime.from(modificationTime));
    }

    /**
     * Sets the file times to epoch (1970-01-01 00:00:00).
     *
     * @param file The file
     * @throws IOException If an I/O error occurs
     */
    public static void setFileTimesToEpoch(File file) throws IOException {
        Instant epoch = Instant.EPOCH;
        setFileTimes(file, epoch, epoch);
    }

    /**
     * Deletes a file or directory.
     *
     * @param file The file or directory to delete
     * @throws IOException If an I/O error occurs
     */
    public static void delete(File file) throws IOException {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    delete(child);
                }
            }
        }

        if (!file.delete()) {
            throw new IOException("Failed to delete: " + file);
        }
    }

    /**
     * Copies a file.
     *
     * @param source The source file
     * @param target The target file
     * @throws IOException If an I/O error occurs
     */
    public static void copyFile(File source, File target) throws IOException {
        createDirectoryIfNotExists(target.getParentFile());
        Files.copy(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);

        // Copy file attributes
        BasicFileAttributes attrs = Files.readAttributes(source.toPath(), BasicFileAttributes.class);
        setFileTimes(target, attrs.creationTime().toInstant(), attrs.lastModifiedTime().toInstant());
    }

    /**
     * Reads a file into a byte array.
     *
     * @param file The file to read
     * @return The file contents as a byte array
     * @throws IOException If an I/O error occurs
     */
    public static byte[] readFile(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    /**
     * Writes a byte array to a file.
     *
     * @param file The file to write to
     * @param data The data to write
     * @throws IOException If an I/O error occurs
     */
    public static void writeFile(File file, byte[] data) throws IOException {
        createDirectoryIfNotExists(file.getParentFile());
        Files.write(file.toPath(), data);
    }
}
