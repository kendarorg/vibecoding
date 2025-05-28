package org.kendar.sync.server.backup;

import org.kendar.sync.lib.model.FileInfo;
import org.kendar.sync.lib.network.TcpConnection;
import org.kendar.sync.lib.protocol.*;
import org.kendar.sync.server.server.ClientSession;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles backup operations for the MIRROR backup type.
 * Files on the target that don't exist on the source are deleted.
 */
public class MirrorBackupHandler implements BackupHandler {

    public MirrorBackupHandler() {
    }

    @Override
    public void handleFileList(TcpConnection connection, ClientSession session, FileListMessage message) throws IOException {
        System.out.println("[MIRROR] Received FILE_LIST message");

        // For MIRROR backup type, we need to identify files that exist in the target but not in the source
        // These files will be deleted during the sync process

        if (session.isDryRun()) {
            // In dry run mode, just acknowledge the message with an empty list
            connection.sendMessage(new FileListResponseMessage(new ArrayList<>(), new ArrayList<>(), true, 1, 1));
            return;
        }

        // Get the list of files from the source
        List<String> sourceRelativePaths = message.getFiles().stream()
                .map(FileInfo::getRelativePath)
                .collect(Collectors.toList());

        // Get the list of files from the target
        File targetDir = new File(session.getFolder().getRealPath());
        List<String> targetFiles = new ArrayList<>();
        if (targetDir.exists() && targetDir.isDirectory()) {
            targetFiles = Files.walk(targetDir.toPath())
                    .filter(path -> !Files.isDirectory(path))
                    .map(path -> getRelativePath(path, targetDir.toPath()))
                    .collect(Collectors.toList());
        }

        // Find files that exist in the target but not in the source
        List<String> filesToDelete = new ArrayList<>();
        for (String targetFile : targetFiles) {
            if (!sourceRelativePaths.contains(targetFile)) {
                filesToDelete.add(targetFile);
            }
        }

        // Send the response with the list of files to delete
        connection.sendMessage(new FileListResponseMessage(new ArrayList<>(), filesToDelete, true, 1, 1));
    }

    @Override
    public void handleFileDescriptor(TcpConnection connection, ClientSession session, FileDescriptorMessage message) throws IOException {
        System.out.println("[MIRROR] Received FILE_DESCRIPTOR message: " + message.getFileInfo().getRelativePath());

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

        System.out.println("[MIRROR] Received FILE_DATA message");

        // Write the data to the file
        File file = new File(session.getFolder().getRealPath(), message.getRelativePath());
        try (FileOutputStream fos = new FileOutputStream(file, !message.isFirstBlock())) {
            fos.write(message.getData());
        }
    }

    @Override
    public void handleFileEnd(TcpConnection connection, ClientSession session, FileEndMessage message) throws IOException {
        System.out.println("[MIRROR] Received FILE_END message");

        connection.sendMessage(FileEndAckMessage.success(message.getRelativePath()));
    }

    @Override
    public void handleSyncEnd(TcpConnection connection, ClientSession session, SyncEndMessage message) throws IOException {
        System.out.println("[MIRROR] Received SYNC_END message");

        // For MIRROR backup type, we need to delete files that exist in the target but not in the source
        // This should have been handled during the FILE_LIST phase

        connection.sendMessage(new SyncEndAckMessage(true, "Sync completed"));
    }

    /**
     * Gets the relative path of a file to a base directory.
     *
     * @param path The file path
     * @param basePath The base directory path
     * @return The relative path
     */
    private String getRelativePath(Path path, Path basePath) {
        return basePath.relativize(path).toString().replace('\\', '/');
    }
}
