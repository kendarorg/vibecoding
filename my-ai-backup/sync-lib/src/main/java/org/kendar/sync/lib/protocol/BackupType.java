package org.kendar.sync.lib.protocol;

/**
 * Defines the types of backup operations.
 */
public enum BackupType {
    /**
     * Backup/Restore without deleting old files.
     * Files on the target that don't exist on the source are preserved.
     */
    PRESERVE,

    /**
     * Backup/Restore deleting the files not present on the source.
     * Files on the target that don't exist on the source are deleted.
     */
    MIRROR,

    /**
     * Backup/Restore without deleting old files with "date separated structure" on the backup.
     * Files are organized in directories based on their modification date.
     * During restore, the date structure is ignored and files are placed directly in the target directory.
     */
    DATE_SEPARATED
}