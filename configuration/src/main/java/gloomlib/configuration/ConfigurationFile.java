package gloomlib.configuration;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/**
 * The main entry point for a configuration file.
 * <p>
 * Classes extending this should map directly to a YAML file.
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

    void setFile(File file) {
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
}