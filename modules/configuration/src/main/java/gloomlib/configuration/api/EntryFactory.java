package gloomlib.configuration.api;

import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.Nullable;

/**
 * Factory interface for creating a single entry from a YAML section.
 *
 * <p>Used with {@link DirectoryConfiguration.Builder} (factory mode) as an alternative to
 * reflection-based {@link ConfigurationPart} deserialization. Ideal for scenarios that
 * require custom parsing logic, multi-type dispatch, or cross-entry references.</p>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * ConfigurationManager
 *     .directory(dir, (name, sec) -> AnimationParser.parse(name, sec, registry))
 *     .rootKey("animation")
 *     .recursive()
 *     .load();
 * }</pre>
 *
 * @param <V> the entry value type
 */
@FunctionalInterface
public interface EntryFactory<V> {

    /**
     * Creates an entry from the given YAML section.
     *
     * @param entryName the key identifying this entry in the YAML file
     * @param section   the YAML section for this entry
     * @return the created entry, or {@code null} to skip this entry
     * @throws Exception if parsing fails
     */
    @Nullable
    V create(String entryName, ConfigurationSection section) throws Exception;
}
