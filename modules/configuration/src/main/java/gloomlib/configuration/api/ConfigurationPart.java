package gloomlib.configuration.api;

import gloomlib.configuration.core.model.FieldMeta;
import gloomlib.configuration.core.util.ConfigurationCache;

import java.util.List;
import java.util.Map;

/**
 * Base class for nested configuration sections.
 */
public abstract class ConfigurationPart {

    /**
     * Retrieves a value using a dot-separated path.
     * <p>
     * Supports fuzzy matching (ignores case, underscores, and hyphens).
     *
     * @param path The path to the value (e.g., "database.host").
     * @return The value, or null if not found.
     */
    public Object get(String path) {
        if (path == null || path.isEmpty()) return this;

        int dotIndex = path.indexOf('.');
        String key = (dotIndex == -1) ? path : path.substring(0, dotIndex);
        String remaining = (dotIndex == -1) ? null : path.substring(dotIndex + 1);
        String fuzzyKey = key.replace("_", "").replace("-", "").toLowerCase();

        try {
            List<FieldMeta> metas = ConfigurationCache.getCachedMeta(this.getClass());
            for (FieldMeta meta : metas) {
                String fieldFuzzy = meta.field().getName().replace("_", "").replace("-", "").toLowerCase();
                if (fieldFuzzy.equals(fuzzyKey)) {
                    Object val = meta.get(this);
                    if (remaining == null) return val;

                    if (val instanceof ConfigurationPart part) {
                        return part.get(remaining);
                    }
                    if (val instanceof Map<?, ?> map) {
                        return map.get(remaining.split("\\.")[0]);
                    }
                }
            }
        } catch (Exception ignored) {
            // Expected: field access or nested access may fail
        }
        return null;
    }
}
