package org.kendar.sync.server.backup;

import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.server.server.ClientSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Handles backup operations for the PRESERVE backup type.
 * Files on the target that don't exist on the source are preserved.
 */
public class PreserveBackupHandler implements BackupHandler {

    public PreserveBackupHandler() {
    }

    @Override
    public void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        System.out.println("[PRESERVE] Received FILE_LIST message");

        // For PRESERVE backup type, we don't need to compare files or delete anything
        // Just acknowledge the message with an empty list
        connection.sendMessage(new FileListResponseMessage(new ArrayList<>(), new ArrayList<>(), true, 1, 1));
    }

    @Override
    public void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        System.out.println("[PRESERVE] Received FILE_DESCRIPTOR message: " + message.getFileInfo().getRelativePath());

        // If this is a dry run, just acknowledge the message
        if (session.isDryRun()) {
            System.out.println("Dry run: Would create file " + message.getFileInfo().getRelativePath());
            connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
            return;
        }

        // Create the file or directory
        File file = new File(session.getFolder().getRealPath(), message.getFileInfo().getRelativePath());

        if (message.getFileInfo().isDirectory()) {
            file.mkdirs();
        } else {
            file.getParentFile().mkdirs();
        }

        connection.sendMessage(FileDescriptorAckMessage.ready(message.getFileInfo().getRelativePath()));
    }

    @Override
    public void handleFileData(TcpConnection connection, ClientSession session, FileDataMessage message) throws IOException {
        // If this is a dry run, just ignore the data
        if (session.isDryRun()) {
            return;
        }

        System.out.println("[PRESERVE] Received FILE_DATA message");

        // Write the data to the file
        File file = new File(session.getFolder().getRealPath(), message.getRelativePath());
        try (FileOutputStream fos = new FileOutputStream(file, !message.isFirstBlock())) {
            fos.write(message.getData());
        }
    }

    @Override
    public void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException {
        System.out.println("[PRESERVE] Received FILE_END message");

        connection.sendMessage(FileEndAckMessage.success(message.getRelativePath()));
    }

    @Override
    public void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException {
        System.out.println("[PRESERVE] Received SYNC_END message");

        connection.sendMessage(new SyncEndAckMessage(true, "Sync completed"));
    }
}
