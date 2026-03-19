package gloomlib.configuration;

import gloomlib.configuration.api.ConfigurationManager;
import gloomlib.configuration.api.DirectoryConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link DirectoryConfiguration} and the directory loading pipeline.
 *
 * <p>Tests are written against the public API ({@link ConfigurationManager#directory}).
 * MockBukkit is started so that {@code YamlConfiguration} class-loading succeeds.</p>
 */
class DirectoryConfigurationTest {

    @TempDir
    Path tempDir;

    // ── rootKey filter ────────────────────────────────────────────────────────

    @Test
    void rootKey_presentSection_entriesLoaded() throws Exception {
        writeYaml("anim.yml",
                "animation:\n" +
                "  hero:\n" +
                "    value: warrior\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("animation")
                .load();

        assertEquals(Map.of("hero", "warrior"), cfg.all());
    }

    @Test
    void rootKey_missingSection_fileSkipped() throws Exception {
        writeYaml("preset.yml",
                "preset:\n" +
                "  p1:\n" +
                "    value: something\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("animation")
                .load();

        assertTrue(cfg.all().isEmpty(), "file without rootKey 'animation' should be skipped");
    }

    @Test
    void rootKey_multipleFiles_onlyMatchingLoaded() throws Exception {
        writeYaml("a.yml", "animation:\n  hero:\n    value: a_hero\n");
        writeYaml("b.yml", "preset:\n  spare:\n    value: spare\n");
        writeYaml("c.yml", "animation:\n  villain:\n    value: c_villain\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("animation")
                .load();

        assertEquals(2, cfg.all().size());
        assertEquals("a_hero",    cfg.all().get("hero"));
        assertEquals("c_villain", cfg.all().get("villain"));
        assertFalse(cfg.all().containsKey("spare"));
    }

    // ── no rootKey ────────────────────────────────────────────────────────────

    @Test
    void noRootKey_topLevelEntriesLoaded() throws Exception {
        writeYaml("entries.yml",
                "alpha:\n  value: first\n" +
                "beta:\n  value: second\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .load();

        assertEquals(2, cfg.all().size());
        assertEquals("first",  cfg.all().get("alpha"));
        assertEquals("second", cfg.all().get("beta"));
    }

    // ── recursive ─────────────────────────────────────────────────────────────

    @Test
    void recursive_subDirFilesFound() throws Exception {
        Path sub = Files.createDirectories(tempDir.resolve("subdir"));
        writeYamlAt(sub.resolve("nested.yml"),
                "animation:\n  deep:\n    value: found\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("animation")
                .recursive()
                .load();

        assertEquals("found", cfg.all().get("deep"));
    }

    @Test
    void nonRecursive_subDirFilesIgnored() throws Exception {
        Path sub = Files.createDirectories(tempDir.resolve("subdir"));
        writeYamlAt(sub.resolve("nested.yml"),
                "animation:\n  deep:\n    value: ignored\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("animation")
                .load(); // no .recursive()

        assertTrue(cfg.all().isEmpty(), "subdir file must be ignored without recursive()");
    }

    @Test
    void recursive_topAndSubDirMerged() throws Exception {
        writeYaml("top.yml", "animation:\n  top_entry:\n    value: top\n");
        Path sub = Files.createDirectories(tempDir.resolve("sub"));
        writeYamlAt(sub.resolve("sub.yml"), "animation:\n  sub_entry:\n    value: sub\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("animation")
                .recursive()
                .load();

        assertEquals(2, cfg.all().size());
        assertEquals("top", cfg.all().get("top_entry"));
        assertEquals("sub", cfg.all().get("sub_entry"));
    }

    // ── factory mode ──────────────────────────────────────────────────────────

    @Test
    void factory_nullReturnSkipped() throws Exception {
        writeYaml("f.yml",
                "animation:\n" +
                "  valid:\n    value: ok\n" +
                "  bad:\n    value: null_me\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> {
                    String val = sec.getString("value", "");
                    return "null_me".equals(val) ? null : val;
                })
                .rootKey("animation")
                .load();

        assertEquals(1, cfg.all().size());
        assertEquals("ok", cfg.all().get("valid"));
        assertFalse(cfg.all().containsKey("bad"), "null return from factory must not be stored");
    }

    @Test
    void factory_nameAndSectionPassedCorrectly() throws Exception {
        writeYaml("f.yml", "keyA:\n  label: hello\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> name + ":" + sec.getString("label"))
                .load();

        assertEquals("keyA:hello", cfg.all().get("keyA"));
    }

    // ── two-pass loading ──────────────────────────────────────────────────────

    @Test
    void twoPass_presetLoadedBeforeAnimation() throws Exception {
        writeYaml("preset.yml", "preset:\n  base:\n    value: preset_loaded\n");
        writeYaml("anim.yml",   "animation:\n  hero:\n    value: anim_loaded\n");

        // Pass 1 – presets
        DirectoryConfiguration<String> presets = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("preset")
                .load();

        // Pass 2 – animations (factory can reference presets.all())
        DirectoryConfiguration<String> anims = ConfigurationManager
                .directory(dir(), (name, sec) -> {
                    String ref = sec.getString("value", name);
                    return presets.all().getOrDefault(ref, ref);
                })
                .rootKey("animation")
                .load();

        assertEquals("preset_loaded", presets.all().get("base"));
        assertEquals("anim_loaded",   anims.all().get("hero")); // not in presets, so identity
    }

    // ── isFresh / reload ──────────────────────────────────────────────────────

    @Test
    void isFresh_afterLoad_returnsTrue() throws Exception {
        writeYaml("f.yml", "animation:\n  e:\n    value: v\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("animation")
                .load();

        assertTrue(cfg.isFresh(), "directory should be fresh immediately after load");
    }

    @Test
    void reload_unchangedDir_returnsFalse() throws Exception {
        writeYaml("f.yml", "animation:\n  e:\n    value: v\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("animation")
                .load();

        assertFalse(cfg.reload(), "reload() must return false when nothing changed");
    }

    @Test
    void reload_modifiedFile_returnsTrueAndUpdatesEntries() throws Exception {
        Path file = tempDir.resolve("f.yml");
        writeYamlAt(file, "animation:\n  hero:\n    value: initial\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("animation")
                .load();

        assertEquals("initial", cfg.all().get("hero"));

        // overwrite with different content and bump mtime to guarantee staleness
        writeYamlAt(file, "animation:\n  hero:\n    value: updated\n");
        touch(file);

        assertTrue(cfg.reload(), "reload() must return true after file change");
        assertEquals("updated", cfg.all().get("hero"), "entries must reflect new content after reload");
    }

    @Test
    void reload_newFileAdded_returnsTrueAndIncludesNewEntry() throws Exception {
        writeYaml("existing.yml", "animation:\n  old:\n    value: old\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("animation")
                .load();

        assertEquals(1, cfg.all().size());

        // add a new file
        writeYaml("new.yml", "animation:\n  fresh:\n    value: fresh\n");

        assertTrue(cfg.reload());
        assertEquals(2, cfg.all().size());
        assertEquals("fresh", cfg.all().get("fresh"));
    }

    @Test
    void reload_fileDeleted_returnsTrueAndRemovesEntry() throws Exception {
        Path kept  = tempDir.resolve("kept.yml");
        Path extra = tempDir.resolve("extra.yml");
        writeYamlAt(kept,  "animation:\n  keep:\n    value: kept\n");
        writeYamlAt(extra, "animation:\n  gone:\n    value: bye\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .rootKey("animation")
                .load();

        assertEquals(2, cfg.all().size());

        Files.delete(extra);

        assertTrue(cfg.reload());
        assertEquals(1, cfg.all().size());
        assertFalse(cfg.all().containsKey("gone"), "deleted file's entries must be removed");
    }

    // ── getOrDefault ──────────────────────────────────────────────────────────

    @Test
    void getOrDefault_existingKey_returnsEntry() throws Exception {
        writeYaml("f.yml", "hero:\n  value: hero_val\ndefault:\n  value: fallback\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .load();

        assertEquals("hero_val", cfg.getOrDefault("hero"));
    }

    @Test
    void getOrDefault_missingKey_fallsBackToDefault() throws Exception {
        writeYaml("f.yml", "default:\n  value: fallback\n");

        DirectoryConfiguration<String> cfg = ConfigurationManager
                .directory(dir(), (name, sec) -> sec.getString("value", name))
                .load();

        assertEquals("fallback", cfg.getOrDefault("nonexistent"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private File dir() {
        return tempDir.toFile();
    }

    private void writeYaml(String name, String content) throws IOException {
        Files.writeString(tempDir.resolve(name), content, StandardCharsets.UTF_8);
    }

    private void writeYamlAt(Path path, String content) throws IOException {
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }

    /**
     * Bumps the last-modified time of a file by 2 seconds so that
     * {@link gloomlib.configuration.api.util.FileCache#isFresh} will detect a change
     * even when the OS timestamp resolution doesn't capture a fast rewrite.
     */
    private void touch(Path path) throws IOException {
        long bumped = path.toFile().lastModified() + 2_000L;
        Files.setLastModifiedTime(path, FileTime.fromMillis(bumped));
    }
}
