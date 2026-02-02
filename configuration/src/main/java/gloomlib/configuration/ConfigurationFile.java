package gloomlib.configuration;

import gloomlib.configuration.service.ConfigBackupManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.concurrent.CompletableFuture;

/**
 * Base class for top-level configuration files.
 * Provides methods for saving, reloading, and backing up configuration.
 */
public abstract class ConfigurationFile extends ConfigurationPart {
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
        if (file == null) throw new IllegalStateException("Cannot save: File path is not set.");
        try {
            ConfigurationManager.save(this, file);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reloads the configuration from disk, updating this object's fields.
     *
     * @throws IllegalStateException if the file path has not been set
     */
    public void reload() {
        if (file == null) throw new IllegalStateException("Cannot reload: File path is not set.");
        try {
            ConfigurationManager.reload(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Reloads the configuration asynchronously.
     *
     * @return a CompletableFuture that completes when the reload finishes
     * @throws IllegalStateException if the file path has not been set
     */
    @NotNull
    public CompletableFuture<Void> reloadAsync() {
        if (file == null) {
            return CompletableFuture.failedFuture(
                    new IllegalStateException("Cannot reload: File path is not set.")
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
        if (file == null) throw new IllegalStateException("Cannot backup: File path is not set.");
        return ConfigBackupManager.backup(file);
    }

    /**
     * Creates a backup of this configuration file with a specific reason.
     *
     * @param reason the reason for the backup
     * @return the backup file, or null if backup failed
     */
    public File backup(@NotNull String reason) {
        if (file == null) throw new IllegalStateException("Cannot backup: File path is not set.");
        return ConfigBackupManager.backup(file, reason);
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
                    new IllegalStateException("Cannot backup: File path is not set.")
            );
        }
        return ConfigBackupManager.backupAsync(file);
    }
}
