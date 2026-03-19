package gloomlib.configuration.api;

import gloomlib.configuration.api.util.ConfigurationLogger;
import gloomlib.configuration.core.util.ConfigBackup;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for top-level configuration files.
 * Provides methods for saving, reloading, and backing up configuration.
 */
public abstract class ConfigurationFile extends ConfigurationPart {

    private static final String ERROR_FILE_NOT_SET = "Cannot %s: File path is not set.";

    private YamlConfiguration yaml;
    private File file;

    /**
     * Gets the underlying Bukkit {@link YamlConfiguration} object.
     *
     * @return the YamlConfiguration
     */
    public YamlConfiguration getYaml() {
        return yaml;
    }

    /**
     * Sets the underlying Bukkit {@link YamlConfiguration} object.
     *
     * @param yaml the YamlConfiguration to set
     */
    public void setYaml(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    /**
     * Gets the file associated with this configuration instance.
     *
     * @return the file object
     */
    public File getFile() {
        return file;
    }

    public void setFile(File file) {
        this.file = file;
    }

    /**
     * Saves the current state of this configuration object to disk.
     *
     * @throws IllegalStateException if the file path has not been set (e.g., object created manually via constructor)
     */
    public void save() {
        if (file == null) throw new IllegalStateException(String.format(ERROR_FILE_NOT_SET, "save"));
        try {
            ConfigurationManager.save(this, file);
        } catch (Exception e) {
            ConfigurationLogger.error("Failed to save configuration", e);
        }
    }

    /**
     * Reloads the configuration from disk, updating this object's fields.
     *
     * @throws IllegalStateException if the file path has not been set
     */
    public void reload() {
        if (file == null) throw new IllegalStateException(String.format(ERROR_FILE_NOT_SET, "reload"));
        try {
            ConfigurationManager.reload(this);
        } catch (Exception e) {
            ConfigurationLogger.error("Failed to reload configuration", e);
        }
    }

    /**
     * Reloads the configuration asynchronously.
     *
     * <p><b>Thread safety:</b> During async reload, field values may be
     * inconsistent if read from another thread. Use the returned future
     * to ensure completion before accessing fields.</p>
     *
     * @return a CompletableFuture that completes when the reload finishes
     * @throws IllegalStateException if the file path has not been set
     */
    @NotNull
    public CompletableFuture<Void> reloadAsync() {
        if (file == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(String.format(ERROR_FILE_NOT_SET, "reload"))
            );
        }
        return CompletableFuture.runAsync(() -> {
            try {
                ConfigurationManager.reload(this);
            } catch (Exception e) {
                throw new RuntimeException("Async reload failed", e);
            }
        });
    }

    /**
     * Creates a backup of this configuration file.
     *
     * @return the backup file, or null if backup failed
     */
    public File backup() {
        if (file == null) throw new IllegalStateException(String.format(ERROR_FILE_NOT_SET, "backup"));
        return ConfigBackup.backup(file);
    }

    /**
     * Creates a backup of this configuration file with a specific reason.
     *
     * @param reason the reason for the backup
     * @return the backup file, or null if backup failed
     */
    public File backup(@NotNull String reason) {
        if (file == null) throw new IllegalStateException(String.format(ERROR_FILE_NOT_SET, "backup"));
        return ConfigBackup.backup(file, reason);
    }

    /**
     * Creates an async backup of this configuration file.
     *
     * @return a CompletableFuture containing the backup file
     */
    @NotNull
    public CompletableFuture<File> backupAsync() {
        if (file == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException(String.format(ERROR_FILE_NOT_SET, "backup"))
            );
        }
        return ConfigBackup.backupAsync(file);
    }

    // ── Smart Reload ─────────────────────────────────────────────────────

    private transient long lastModified;
    private transient long lastSize;

    /**
     * Reloads only if the file has been modified since the last reload.
     * Tracks freshness via {@code lastModified} timestamp and file size.
     *
     * @return {@code true} if the file was changed and reloaded
     */
    public boolean smartReload() {
        if (file == null || !file.exists()) return false;
        if (file.lastModified() == lastModified && file.length() == lastSize) return false;
        reload();
        lastModified = file.lastModified();
        lastSize = file.length();
        return true;
    }

    /**
     * Refreshes the internal timestamp cache without reloading.
     * Call this after the initial {@link ConfigurationManager#load} to prime the freshness state.
     */
    public void refreshTimestamp() {
        if (file != null && file.exists()) {
            lastModified = file.lastModified();
            lastSize = file.length();
        }
    }
}
