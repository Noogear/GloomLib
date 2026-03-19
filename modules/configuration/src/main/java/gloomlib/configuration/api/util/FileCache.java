package gloomlib.configuration.api.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe file cache that tracks file freshness via {@code lastModified} + {@code size}.
 *
 * <p>Designed for configuration reload scenarios where files are read repeatedly.
 * The cache avoids re-reading unchanged files from disk and provides a simple
 * {@link #isFresh(File)} check for callers who manage their own parsing.</p>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * FileCache cache = new FileCache();
 *
 * // Smart reload — only re-read when file changed
 * String content = cache.readIfChanged(myFile);
 * if (content != null) {
 *     // re-parse ...
 * }
 *
 * // Directory-level check
 * if (!cache.isDirectoryFresh(dir)) {
 *     // at least one file added/removed/modified
 * }
 * }</pre>
 */
public final class FileCache {

    /**
     * Cache entry holding metadata and raw content of a file.
     *
     * @param lastModified OS last-modified timestamp
     * @param size         file size in bytes
     * @param content      raw UTF-8 content
     */
    public record Entry(long lastModified, long size, @NotNull String content) {
        /** Returns {@code true} if the file on disk still matches this entry's metadata. */
        public boolean isFresh(@NotNull File file) {
            return file.exists()
                    && file.lastModified() == lastModified
                    && file.length() == size;
        }
    }

    private final ConcurrentHashMap<String, Entry> cache = new ConcurrentHashMap<>();

    /**
     * Returns {@code true} if the file has not been modified since it was last cached.
     */
    public boolean isFresh(@NotNull File file) {
        Entry entry = cache.get(file.getAbsolutePath());
        return entry != null && entry.isFresh(file);
    }

    /**
     * Reads the file only if it has changed since the last read (or was never read).
     *
     * @return the UTF-8 content if the file changed, or {@code null} if it's still fresh
     */
    @Nullable
    public String readIfChanged(@NotNull File file) throws IOException {
        if (isFresh(file)) return null;
        return read(file);
    }

    /**
     * Reads the file and updates the cache unconditionally (force read).
     */
    @NotNull
    public String read(@NotNull File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        cache.put(file.getAbsolutePath(), new Entry(file.lastModified(), file.length(), content));
        return content;
    }

    /**
     * Stores a pre-built entry directly (e.g. after a write operation where the content is already known).
     */
    public void put(@NotNull File file, @NotNull Entry entry) {
        cache.put(file.getAbsolutePath(), entry);
    }

    /**
     * Returns the cached content for a file, or {@code null} if not cached.
     */
    @Nullable
    public String getCachedContent(@NotNull File file) {
        Entry entry = cache.get(file.getAbsolutePath());
        return entry != null ? entry.content() : null;
    }

    /**
     * Checks if a directory is "fresh" — none of its {@code .yml}/{@code .yaml} files
     * have been added, removed, or modified since the last load.
     *
     * @param directory the directory to check
     * @param recursive if {@code true}, sub-directories are also checked
     */
    public boolean isDirectoryFresh(@NotNull File directory, boolean recursive) {
        List<File> files = collectYamlFiles(directory, recursive);
        String dirPrefix = directory.getAbsolutePath() + File.separator;
        long cachedCount = cache.keySet().stream()
                .filter(k -> k.startsWith(dirPrefix))
                .count();
        if (files.size() != cachedCount) return false;
        for (File f : files) {
            if (!isFresh(f)) return false;
        }
        return true;
    }

    /** @see #isDirectoryFresh(File, boolean) */
    public boolean isDirectoryFresh(@NotNull File directory) {
        return isDirectoryFresh(directory, false);
    }

    /**
     * Removes cache entries for files that no longer exist under the given directory.
     *
     * @param recursive if {@code true}, sub-directory entries are also purged
     */
    public void purgeStale(@NotNull File directory, boolean recursive) {
        List<File> liveFiles = collectYamlFiles(directory, recursive);
        String dirPrefix = directory.getAbsolutePath() + File.separator;
        cache.keySet().removeIf(key -> key.startsWith(dirPrefix)
                && liveFiles.stream().noneMatch(f -> f.getAbsolutePath().equals(key)));
    }

    /** @see #purgeStale(File, boolean) */
    public void purgeStale(@NotNull File directory) {
        purgeStale(directory, false);
    }

    private static List<File> collectYamlFiles(@NotNull File directory, boolean recursive) {
        List<File> result = new ArrayList<>();
        doCollect(directory, result, recursive);
        return result;
    }

    private static void doCollect(@NotNull File dir, @NotNull List<File> out, boolean recursive) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File f : children) {
            if (f.isDirectory() && recursive) doCollect(f, out, true);
            else if (f.isFile() && (f.getName().endsWith(".yml") || f.getName().endsWith(".yaml")))
                out.add(f);
        }
    }

    /** Clears all entries from the cache. */
    public void clear() {
        cache.clear();
    }

    /** Returns the number of cached entries. */
    public int size() {
        return cache.size();
    }
}
