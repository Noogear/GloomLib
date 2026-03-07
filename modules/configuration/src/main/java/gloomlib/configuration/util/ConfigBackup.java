package gloomlib.configuration.util;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

/**
 * Manages configuration file backups with automatic versioning and restoration capabilities.
 */
public final class ConfigBackup {

    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String BACKUP_DIR_NAME = "backups";

    private ConfigBackup() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Creates a backup of the configuration file.
     *
     * @param file the file to backup
     * @return the backup file, or null if backup failed
     */
    @Nullable
    public static File backup(@NotNull File file) {
        return backup(file, null);
    }

    /**
     * Creates a backup of the configuration file with a specific reason suffix.
     *
     * @param file   the file to backup
     * @param reason optional reason for the backup
     * @return the backup file, or null if backup failed
     */
    @Nullable
    public static File backup(@NotNull File file, @Nullable String reason) {
        if (!file.exists()) {
            ConfigurationLogger.info("Cannot backup non-existent file: " + file.getName());
            return null;
        }

        try {
            File backupDir = new File(file.getParent(), BACKUP_DIR_NAME);
            if (!backupDir.exists() && !backupDir.mkdirs()) {
                ConfigurationLogger.error("Failed to create backup directory: " + backupDir.getAbsolutePath(), null);
                return null;
            }

            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String suffix = (reason != null && !reason.isEmpty()) ? "_" + reason : "";
            String backupName = file.getName() + "." + timestamp + suffix + ".bak";
            File backupFile = new File(backupDir, backupName);

            Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ConfigurationLogger.info("Created backup: " + backupFile.getName());
            return backupFile;
        } catch (IOException e) {
            ConfigurationLogger.error("Failed to backup file: " + file.getName(), e);
            return null;
        }
    }

    /**
     * Creates a backup asynchronously.
     *
     * @param file the file to backup
     * @return a CompletableFuture containing the backup file
     */
    @NotNull
    public static CompletableFuture<File> backupAsync(@NotNull File file) {
        return backupAsync(file, null);
    }

    /**
     * Creates a backup asynchronously with a specific reason.
     *
     * @param file   the file to backup
     * @param reason optional reason for the backup
     * @return a CompletableFuture containing the backup file
     */
    @NotNull
    public static CompletableFuture<File> backupAsync(@NotNull File file, @Nullable String reason) {
        return CompletableFuture.supplyAsync(() -> backup(file, reason));
    }

    /**
     * Restores a configuration file from a backup.
     *
     * @param backupFile the backup file to restore from
     * @param targetFile the target file to restore to
     * @return true if restoration was successful
     */
    public static boolean restore(@NotNull File backupFile, @NotNull File targetFile) {
        if (!backupFile.exists()) {
            ConfigurationLogger.error("Backup file does not exist: " + backupFile.getName(), null);
            return false;
        }

        try {
            Files.copy(backupFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            ConfigurationLogger.info("Restored configuration from backup: " + backupFile.getName());
            return true;
        } catch (IOException e) {
            ConfigurationLogger.error("Failed to restore from backup: " + backupFile.getName(), e);
            return false;
        }
    }

    /**
     * Cleans up old backup files, keeping only the most recent N backups.
     *
     * @param file      the configuration file whose backups should be cleaned
     * @param keepCount number of recent backups to keep
     * @return number of backups deleted
     */
    public static int cleanOldBackups(@NotNull File file, int keepCount) {
        File backupDir = new File(file.getParent(), BACKUP_DIR_NAME);
        if (!backupDir.exists() || !backupDir.isDirectory()) {
            return 0;
        }

        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith(file.getName()) && name.endsWith(".bak"));
        if (backups == null || backups.length <= keepCount) {
            return 0;
        }

        // Sort by last modified time (newest first)
        java.util.Arrays.sort(backups, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));

        int deleted = 0;
        for (int i = keepCount; i < backups.length; i++) {
            if (backups[i].delete()) {
                deleted++;
                ConfigurationLogger.info("Deleted old backup: " + backups[i].getName());
            }
        }

        return deleted;
    }

    /**
     * Gets the total size of all backups for a configuration file (in bytes).
     *
     * @param file the configuration file
     * @return total size in bytes
     */
    public static long getBackupSize(@NotNull File file) {
        File backupDir = new File(file.getParent(), BACKUP_DIR_NAME);
        if (!backupDir.exists()) {
            return 0;
        }

        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith(file.getName()) && name.endsWith(".bak"));
        if (backups == null) {
            return 0;
        }

        long totalSize = 0;
        for (File backup : backups) {
            totalSize += backup.length();
        }
        return totalSize;
    }

    /**
     * Lists all backup files for a configuration file.
     *
     * @param file the configuration file
     * @return array of backup files, or empty array if none exist
     */
    @NotNull
    public static File[] listBackups(@NotNull File file) {
        File backupDir = new File(file.getParent(), BACKUP_DIR_NAME);
        if (!backupDir.exists()) {
            return new File[0];
        }

        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith(file.getName()) && name.endsWith(".bak"));
        return backups != null ? backups : new File[0];
    }


}
