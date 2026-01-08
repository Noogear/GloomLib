package gloomlib.configuration;

import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;

public abstract class ConfigurationFile extends ConfigurationPart {
    private YamlConfiguration yaml;
    private File file;

    public YamlConfiguration getYaml() { return yaml; }
    public void setYaml(YamlConfiguration yaml) { this.yaml = yaml; }

    void setFile(File file) { this.file = file; }
    public File getFile() { return file; }

    public void save() {
        if (file == null) throw new IllegalStateException("無法保存：文件路徑未設置");
        try { ConfigurationManager.save(this, file); } catch (Exception e) { e.printStackTrace(); }
    }

    public void reload() {
        if (file == null) throw new IllegalStateException("無法重載：文件路徑未設置");
        try { ConfigurationManager.reload(this); } catch (Exception e) { e.printStackTrace(); }
    }
}
