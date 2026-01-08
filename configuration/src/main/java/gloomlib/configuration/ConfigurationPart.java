package gloomlib.configuration;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * Represents a node in the configuration structure.
 * Provides capabilities for deep value retrieval.
 */
public abstract class ConfigurationPart {

    /**
     * Retrieves a value using a dot-separated path.
     * <p>
     * Supports fuzzy matching (ignores case and underscores).
     *
     * @param path The path to the value (e.g., "database.host").
     * @return The value, or null if not found.
     */
    public Object get(String path) {
        if (path == null || path.isEmpty()) return this;

        int dotIndex = path.indexOf('.');
        String key = (dotIndex == -1) ? path : path.substring(0, dotIndex);
        String remaining = (dotIndex == -1) ? null : path.substring(dotIndex + 1);
        String fuzzyKey = key.replace("_", "").replace("-", "");

        try {
            for (Field f : this.getClass().getFields()) {
                if (f.getName().replace("_", "").replace("-", "").equalsIgnoreCase(fuzzyKey)) {
                    Object val = f.get(this);
                    if (remaining == null) return val;

                    if (val instanceof ConfigurationPart part) {
                        return part.get(remaining);
                    }
                    if (val instanceof Map<?, ?> map) {
                        // Assumes map keys are strings for configuration
                        return map.get(remaining.split("\\.")[0]);
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}



