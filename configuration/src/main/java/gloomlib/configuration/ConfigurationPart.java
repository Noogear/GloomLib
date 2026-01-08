package gloomlib.configuration;

import java.lang.reflect.Field;
import java.util.Map;

public abstract class ConfigurationPart {
    public Object get(String path) {
        if (path == null || path.isEmpty()) return this;
        String[] parts = path.split("\\.", 2);
        String key = parts[0];
        String remaining = parts.length > 1 ? parts[1] : null;

        try {
            for (Field f : this.getClass().getFields()) {
                if (f.getName().replace("_", "").replace("-", "").equalsIgnoreCase(key.replace("_", "").replace("-", ""))) {
                    Object val = f.get(this);
                    if (remaining == null) return val;

                    if (val instanceof ConfigurationPart part) {
                        return part.get(remaining);
                    }
                    if (val instanceof Map<?,?> map) {
                        return map.get(remaining.split("\\.")[0]);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }
}


