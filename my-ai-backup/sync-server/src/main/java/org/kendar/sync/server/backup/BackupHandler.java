package org.kendar.sync.server.backup;

import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.server.server.ClientSession;

import java.io.IOException;

/**
 * Interface for handling backup operations based on backup type.
 */
public interface BackupHandler {
    
    /**
     * Handles a file list message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The file list message
     * @throws IOException If an I/O error occurs
     */
    void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException;
    
    /**
     * Handles a file descriptor message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The file descriptor message
     * @throws IOException If an I/O error occurs
     */
    void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException;
    
    /**
     * Handles a file data message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The file data message
     * @throws IOException If an I/O error occurs
     */
    void handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException;
    
    /**
     * Handles a file end message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The file end message
     * @throws IOException If an I/O error occurs
     */
    void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException;
    
    /**
     * Handles a sync end message.
     *
     * @param connection The TCP connection
     * @param session The client session
     * @param message The sync end message
     * @throws IOException If an I/O error occurs
     */
    void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException;
}