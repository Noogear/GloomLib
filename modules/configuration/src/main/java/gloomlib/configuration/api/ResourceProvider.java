package gloomlib.configuration.api;

import org.jetbrains.annotations.Nullable;

import java.io.InputStream;

/**
 * Supplies default resource streams for directory-based configurations.
 * <p>
 * Implementations typically delegate to {@code Plugin.getResource(path)}.
 * </p>
 *
 * @see DirectoryConfiguration
 */
@FunctionalInterface
public interface ResourceProvider {

    /**
     * Returns an input stream for the given resource path, or {@code null} if not found.
     *
     * @param path the resource path inside the JAR (e.g. {@code "indicator/damage-indicator.yml"})
     * @return the input stream, or {@code null}
     */
    @Nullable
    InputStream getResource(String path);
}
