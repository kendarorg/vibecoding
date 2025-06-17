package org.kendar.sync.lib.utils;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.PathMatcher;
import java.nio.file.attribute.*;
import java.util.regex.Pattern;
import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.protocol.BackupType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Utility class for file operations.
 */
public class FileUtils {
    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    public static String readFile(String filename) throws IOException
    {
        String content = null;
        File file = new File(filename); // For example, foo.txt
        FileReader reader = null;
        try {
            reader = new FileReader(file);
            char[] chars = new char[(int) file.length()];
            reader.read(chars);
            content = new String(chars);
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if(reader != null){
                reader.close();
            }
        }
        return content;
    }

    public static String readFile(Path lastCompactLogPath) throws IOException {
        return readFile(lastCompactLogPath.toAbsolutePath().normalize().toString());
    }
    /**
     * Lists all files in a directory recursively.
     *
     * @param directory The directory to list
     * @param baseDir   The base directory for calculating relative paths
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
     * @param baseDir   The base directory for calculating relative paths
     * @param files     The list to add the files to
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
            } else {
                files.add(fileInfo);
            }
        }
    }

    public static String makeUniformPath(String path) {
        var res = path.replaceAll("\\\\", "/");
        if (res.startsWith("/")) {
            return res.substring(1);
        }
        return res;
    }

    /**
     * Calculates the files that need to be transferred and deleted.
     *
     * @param sourceFiles The source files
     * @param targetFiles The target files
     * @param backupType  The backup type
     * @return A map with the files to transfer and delete
     */
    public static Map<String, List<FileInfo>> calculateFileDifferences(
            List<FileInfo> sourceFiles, List<FileInfo> targetFiles, BackupType backupType) {
        Map<String, List<FileInfo>> result = new HashMap<>();

        // Create maps for a faster lookup
        Map<String, FileInfo> sourceMap = sourceFiles.stream()
                .collect(Collectors.toMap(FileInfo::getRelativePath, f -> f));

        Map<String, FileInfo> targetMap = targetFiles.stream()
                .collect(Collectors.toMap(FileInfo::getRelativePath, f -> f));

        // Files to transfer: files that don't exist in the target or have different timestamps
        List<FileInfo> filesToTransfer = sourceFiles.stream()
                .filter(sourceFile -> {
                    if (Attributes.isDirectory(sourceFile.getExtendedUmask())) {
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
                    .filter(targetFile -> !Attributes.isDirectory(targetFile.getExtendedUmask()) && !sourceMap.containsKey(targetFile.getRelativePath()))
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
     * @param file       The file
     * @param targetDir  The target directory
     * @param backupType The backup type
     * @return The target path
     */
    public static String getTargetPath(FileInfo file, String targetDir, BackupType backupType) {
        if (backupType != BackupType.DATE_SEPARATED) {
            return targetDir;
        }

        var dtf = new SimpleDateFormat("yyyy-MM-dd");
        // For DATE_SEPARATED, create a directory structure based on the file's modification date
        var dateDir = dtf.format(new Date(file.getModificationTime().toEpochMilli()));

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
                throw new IOException("Failed to create directory 8: " + directory);
            }
        }
    }

    /**
     * Sets the file times.
     *
     * @param file             The file
     * @param creationTime     The creation time
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

    /**
     * Deletes all files and subdirectories in the specified directory.
     * The directory itself is not deleted.
     *
     * @param directory The directory to clean
     * @return true if all files were deleted successfully, false otherwise
     * @throws IOException If an I/O error occurs
     */
    public static boolean deleteDirectoryContents(Path directory) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return false;
        }

        boolean success = true;

        // Use Files.walk to traverse the directory tree in depth-first order
        try (var paths = Files.walk(directory)) {
            // Skip the root directory itself
            var filesToDelete = paths
                    .filter(path -> !path.equals(directory))
                    .sorted((a, b) -> -a.compareTo(b)) // Reverse order to delete children before parents
                    .collect(Collectors.toList());

            for (Path path : filesToDelete) {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    success = false;
                    log.error("Failed to delete: {} - {}", path, e.getMessage());
                }
            }
        }
        Files.delete(directory);

        return success;
    }

    private static ConcurrentHashMap<String, Pattern> regexPatterns = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, PathMatcher> globPatterns = new ConcurrentHashMap<>();

    /**
     * Checks if a given path matches a specified pattern.
     * The pattern can be a glob pattern or a regex pattern (prefixed with '@').
     *
     * @param path    The path to check
     * @param pattern The pattern to match against
     * @return true if the path matches the pattern, false otherwise
     */
    public static boolean matches(String path,String pattern){
        try {
            path = makeUniformPath(path);
            if (pattern.startsWith("@")) {
                regexPatterns.computeIfAbsent(pattern, p -> Pattern.compile(p.substring(1)));
                return regexPatterns.get(pattern).matcher(path).matches();
            } else {
                globPatterns.computeIfAbsent(pattern, p -> FileSystems.getDefault().getPathMatcher("glob:" + p));
                return globPatterns.get(pattern).matches(Path.of(path));
            }
        }catch (Exception e){
            return false;
        }
    }

    public static Attributes readFileAttributes(Path path) throws IOException {
        BasicFileAttributes attributes =Files.readAttributes(path, BasicFileAttributes.class);
        int umask = 0x0;
        var file = path.toFile();
        if(file.isHidden()){
            umask |= 0x1000;
        }
        if(file.isDirectory()){
            umask |= 0x8000;
        }
        if(attributes.isSymbolicLink()){
            umask |= 0x2000;
        }

        if(attributes instanceof PosixFileAttributes) {
            var posixAttributes = (PosixFileAttributes) attributes;
            for (var permission : posixAttributes.permissions()) {
                switch (permission) {
                    case OWNER_READ:     umask |= 0x0400; break;
                    case OWNER_WRITE:    umask |= 0x0200; break;
                    case OWNER_EXECUTE:  umask |= 0x0100; break;
                    case GROUP_READ:     umask |= 0x0040; break;
                    case GROUP_WRITE:    umask |= 0x0020; break;
                    case GROUP_EXECUTE:  umask |= 0x0010; break;
                    case OTHERS_READ:    umask |= 0x0004; break;
                    case OTHERS_WRITE:   umask |= 0x0002; break;
                    case OTHERS_EXECUTE: umask |= 0x0001; break;
                }
            }
        } else if (attributes instanceof DosFileAttributes) {
            var dosAttributes = (DosFileAttributes) attributes;
            if (dosAttributes.isSystem()) {
                umask |= 0x4000;
            }
            if(file.canExecute()){
                umask |= 0x0001;
            }
            if(file.canRead()){
                umask |= 0x0004;
            }
            if(file.canWrite()){
                umask |= 0x0002;
            }
        }


        return new Attributes(umask,attributes.creationTime().toInstant(),attributes.lastModifiedTime().toInstant(),
                attributes.size());
    }




    /**
     * Converts a Unix file mode (umask) to a set of PosixFilePermission
     *
     * @param umask The Unix file mode as an integer (e.g., 644, 755)
     * @return A set of PosixFilePermission
     */
    public static void writeFileAttributes(Path path, int umask, BasicFileAttributes attributes) throws IOException {

        var file = path.toFile();
        if(attributes instanceof PosixFileAttributes) {
            var posixAttributes = (PosixFileAttributes) attributes;
            Set<PosixFilePermission> permissions = EnumSet.noneOf(PosixFilePermission.class);

            // Owner permissions
            if ((umask & 0x0400) != 0) permissions.add(PosixFilePermission.OWNER_READ);
            if ((umask & 0x0200) != 0) permissions.add(PosixFilePermission.OWNER_WRITE);
            if ((umask & 0x0100) != 0) permissions.add(PosixFilePermission.OWNER_EXECUTE);

            // Group permissions
            if ((umask & 0x0040) != 0) permissions.add(PosixFilePermission.GROUP_READ);
            if ((umask & 0x0020) != 0) permissions.add(PosixFilePermission.GROUP_WRITE);
            if ((umask & 0x0010) != 0) permissions.add(PosixFilePermission.GROUP_EXECUTE);

            // Others permissions
            if ((umask & 0x0004) != 0) permissions.add(PosixFilePermission.OTHERS_READ);
            if ((umask & 0x0002) != 0) permissions.add(PosixFilePermission.OTHERS_WRITE);
            if ((umask & 0x0001) != 0) permissions.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(path,permissions);
        }else if(attributes instanceof DosFileAttributes){
            var dosAttributes = (DosFileAttributes) attributes;
            if( (umask & 0x4000) != 0) {
                // Set system attribute
                Files.setAttribute(path, "dos:system", true);
            }
            if( (umask & 0x1000) != 0) {
                // Set system attribute
                Files.setAttribute(path, "dos:hidden", true);
            }

            file.setWritable( (umask & 0x0002) == 0);
            file.setExecutable( (umask & 0x0001) == 0);
            file.setReadable( (umask & 0x0004) == 0);
        }
    }
}
