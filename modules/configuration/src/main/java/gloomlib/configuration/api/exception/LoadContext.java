package gloomlib.configuration.api.exception;

import gloomlib.configuration.core.util.YamlLineIndex;
import gloomlib.diagnostic.SourceLocation;

import java.util.List;
import java.util.Map;

/**
 * ThreadLocal context for the currently-loading configuration file.
 *
 * <p>Set by {@link gloomlib.configuration.core.service.ConfigurationLoader} before
 * deserialization begins, and cleared in a {@code finally} block after.
 * This avoids threading extra parameters through every deserialization method.
 *
 * <p>{@link SerializationException} factory methods read this context to include
 * real file name and line numbers in {@link gloomlib.diagnostic.SourceLocation}.
 */
public final class LoadContext {

    private static final ThreadLocal<Ctx> CURRENT = new ThreadLocal<>();

    private LoadContext() {
    }

    /**
     * Sets the current loading context.
     *
     * @param filename  the config file name (e.g. {@code "config.yml"})
     * @param lineIndex dotpath → 1-based line, from {@link YamlLineIndex#build}
     */
    public static void set(String filename, Map<String, Integer> lineIndex) {
        CURRENT.set(new Ctx(filename, lineIndex));
    }

    /**
     * Removes the current context. Always call in a {@code finally} block.
     */
    public static void clear() {
        CURRENT.remove();
    }

    /**
     * Returns the current config file name, or {@code null} if no context is active.
     * When null, {@link gloomlib.configuration.api.exception.SerializationException}
     * falls back to path-only location.
     */
    public static String filename() {
        Ctx ctx = CURRENT.get();
        return ctx != null ? ctx.filename() : null;
    }

    /**
     * Looks up the 1-based line number for the given YAML key path.
     *
     * @param path dot-split YAML key path
     * @return line number (≥ 1), or 0 if unknown
     */
    public static int lineFor(List<String> path) {
        Ctx ctx = CURRENT.get();
        if (ctx == null || path == null || path.isEmpty()) return 0;
        return ctx.lineIndex().getOrDefault(String.join(".", path), 0);
    }

    /**
     * Creates a {@link SourceLocation} from the current loading context and YAML key path.
     * Includes file name and line number when a context is active.
     *
     * @param path YAML key path
     * @return SourceLocation with file:line and dotpath, or path-only fallback
     */
    public static SourceLocation location(List<String> path) {
        if (path == null || path.isEmpty()) return SourceLocation.UNKNOWN;
        String fn = filename();
        String dotPath = String.join(".", path);
        if (fn != null) {
            int line = lineFor(path);
            String source = line > 0
                    ? fn + ":" + line + " (" + dotPath + ")"
                    : fn + " (" + dotPath + ")";
            return new SourceLocation(source, 0, 0);
        }
        return SourceLocation.ofYamlPath(path);
    }

    /**
     * Varargs convenience overload of {@link #location(List)}.
     */
    public static SourceLocation location(String... pathParts) {
        return location(List.of(pathParts));
    }

    private record Ctx(String filename, Map<String, Integer> lineIndex) {
    }
}
