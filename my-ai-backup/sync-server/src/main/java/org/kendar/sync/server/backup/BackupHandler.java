package org.kendar.sync.server.backup;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.server.server.ClientSession;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Interface for handling backup operations based on backup type.
 */
public abstract class BackupHandler {
    
    /**
     * Handles a file list message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The file list message
     * @throws IOException If an I/O error occurs
     */
    public abstract void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException;
    
    /**
     * Handles a file descriptor message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The file descriptor message
     * @throws IOException If an I/O error occurs
     */
    public abstract void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException;
    
    /**
     * Handles a file data message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The file data message
     * @throws IOException If an I/O error occurs
     */
    public abstract void handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException;
    
    /**
     * Handles a file end message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The file end message
     * @throws IOException If an I/O error occurs
     */
    public abstract void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException;
    
    /**
     * Handles a sync end message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The sync end message
     * @throws IOException If an I/O error occurs
     */
    public abstract void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException;



    protected List<Path> listAllFiles(Path sourcePath) throws IOException {
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            return new ArrayList<>();
        }

        return Files.walk(sourcePath)
                .filter(path -> !Files.isDirectory(path))
                .collect(Collectors.toList());
    }

    protected List<Path> listAllFilesAndDirs(Path sourcePath) throws IOException {
        if (!Files.exists(sourcePath) || !Files.isDirectory(sourcePath)) {
            return new ArrayList<>();
        }

        return Files.walk(sourcePath)
                .collect(Collectors.toList());
    }

    protected boolean shouldUpdate(FileInfo fileInfo, Path file, BasicFileAttributes attr) {
        if(fileInfo==null)return true;
        if(fileInfo.getModificationTime().isAfter(attr.lastModifiedTime().toInstant()))return true;
        if(fileInfo.getCreationTime().isAfter(attr.creationTime().toInstant()))return true;
        if(fileInfo.getSize()!=attr.size())return true;
        return false;
    }
}