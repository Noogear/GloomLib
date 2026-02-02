package gloomlib.translation.loader;

import gloomlib.translation.api.TranslationManager;
import gloomlib.translation.api.TranslationMigrator;
import gloomlib.translation.api.TranslationSource;
import gloomlib.translation.config.FileSourceOptions;
import gloomlib.translation.impl.MapTranslationSource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Loads translation files with migration support.
 */
public final class TranslationLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(TranslationLoader.class);
    private static final String LANG_VERSION_KEY = "lang-version";
    private static final String BACKUP_EXT = ".bak";
    private static final String LINE_SEP = System.lineSeparator();

    private TranslationLoader() {
    }

    /**
     * Loads a translation source from file.
     *
     * @param dataFolder the folder containing translation files
     * @param fileName the file name
     * @param locale the locale
     * @return the translation source
     * @throws Exception if loading fails
     */
    public static TranslationSource load(@NotNull Path dataFolder, @NotNull String fileName,
                                         @NotNull Locale locale) throws Exception {
        return load(dataFolder, fileName, locale, FileSourceOptions.defaults());
    }

    /**
     * Loads a translation source with options.
     *
     * @param dataFolder the folder containing translation files
     * @param fileName the file name
     * @param locale the locale
     * @param options the loading options
     * @return the translation source
     * @throws Exception if loading fails
     */
    public static TranslationSource load(@NotNull Path dataFolder, @NotNull String fileName,
                                         @NotNull Locale locale, @NotNull FileSourceOptions options) throws Exception {
        Path filePath = dataFolder.resolve(fileName);
        boolean isYaml = isYaml(fileName);
        Charset charset = options.charset();

        String prefix = options.internalPathPrefix();
        if (!prefix.endsWith("/")) prefix += "/";
        if (fileName.startsWith("/")) fileName = fileName.substring(1);
        String resourcePath = prefix + fileName;

        if (!Files.exists(dataFolder)) {
            Files.createDirectories(dataFolder);
        }

        try (InputStream resourceStream = TranslationLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (resourceStream != null) {
                if (!Files.exists(filePath)) {
                    if (options.createIfMissing()) {
                        if (options.verbose()) LOGGER.info("Creating locale file: {}", fileName);
                        Files.copy(resourceStream, filePath);
                    }
                } else {
                    handleMigration(filePath, resourceStream, isYaml, charset, options);
                }
            } else {
                if (!Files.exists(filePath)) {
                    throw new FileNotFoundException("Translation file not found: " + fileName);
                }
                if (options.verbose()) {
                    LOGGER.warn("Internal resource missing for {}, skipping migration.", fileName);
                }
            }
        }

        Map<String, String> translations = TranslationParsers.parse(filePath, charset);
        return new MapTranslationSource(locale, translations);
    }

    private static boolean isYaml(String name) {
        return name.endsWith(".yml") || name.endsWith(".yaml");
    }

    private static void handleMigration(Path userFile, InputStream internalStream, boolean isYaml,
                                        Charset charset, FileSourceOptions options) throws IOException {
        if (options.langVersion() == null && !options.enableMigration()) {
            return;
        }

        Map<String, String> userMap = TranslationParsers.parse(userFile, charset);
        String internalContent = new String(internalStream.readAllBytes(), charset);
        Map<String, String> internalMap = isYaml
                ? TranslationParsers.parseYamlContent(internalContent)
                : TranslationParsers.parsePropertiesContent(internalContent);

        boolean needsUpdate = false;
        Map<String, String> resultMap = new LinkedHashMap<>(userMap);

        if (options.langVersion() != null) {
            String currentVersion = userMap.get(LANG_VERSION_KEY);
            if (currentVersion == null || !currentVersion.equals(options.langVersion())) {
                if (options.verbose()) {
                    LOGGER.info("Version mismatch: current={}, expected={}", currentVersion, options.langVersion());
                }
                resultMap = new LinkedHashMap<>(internalMap);
                for (Map.Entry<String, String> e : userMap.entrySet()) {
                    if (!LANG_VERSION_KEY.equals(e.getKey())) {
                        resultMap.put(e.getKey(), e.getValue());
                    }
                }
                if (internalMap.containsKey(LANG_VERSION_KEY)) {
                    resultMap.put(LANG_VERSION_KEY, internalMap.get(LANG_VERSION_KEY));
                } else {
                    resultMap.put(LANG_VERSION_KEY, options.langVersion());
                }
                needsUpdate = true;
            }
        }

        if (options.migrator() != null && options.migrator().shouldMigrate(userFile.getFileName().toString(), resultMap)) {
            String fileName = userFile.getFileName().toString();
            String localeName = fileName.substring(0, fileName.lastIndexOf('.'));
            Locale locale = TranslationManager.parseLocale(localeName);
            if (locale == null) locale = Locale.getDefault();

            Map<String, String> migrated = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : resultMap.entrySet()) {
                TranslationMigrator.Migration m = options.migrator().migrate(locale, e.getKey(), e.getValue());
                if (m == null) {
                    migrated.put(e.getKey(), e.getValue());
                } else if (!m.drop()) {
                    migrated.put(m.key(), m.value());
                    if (options.verbose() && !e.getKey().equals(m.key())) {
                        LOGGER.info("Migrated: {} -> {}", e.getKey(), m.key());
                    }
                } else if (options.verbose()) {
                    LOGGER.info("Dropped: {}", e.getKey());
                }
            }
            resultMap = migrated;
            needsUpdate = true;
        }

        FileSourceOptions.Scope scope = options.scope();
        if (options.enableMigration() && scope != FileSourceOptions.Scope.NONE) {
            boolean fill = scope == FileSourceOptions.Scope.FILL || scope == FileSourceOptions.Scope.FILTER_AND_FILL;
            boolean filter = scope == FileSourceOptions.Scope.FILTER || scope == FileSourceOptions.Scope.FILTER_AND_FILL;

            if (fill) {
                for (String key : internalMap.keySet()) {
                    if (!resultMap.containsKey(key)) {
                        resultMap.put(key, internalMap.get(key));
                        needsUpdate = true;
                    }
                }
            }
            if (filter) {
                resultMap.keySet().removeIf(key ->
                        !internalMap.containsKey(key) && !LANG_VERSION_KEY.equals(key));
                needsUpdate = true;
            }
        }

        if (needsUpdate) {
            if (options.backupBeforeMigration()) {
                createBackup(userFile, options);
            }
            writeTranslationFile(userFile, resultMap, isYaml, charset);
            if (options.verbose()) {
                LOGGER.info("Updated translation file: {}", userFile.getFileName());
            }
        }
    }

    private static void createBackup(Path userFile, FileSourceOptions options) throws IOException {
        Path backup = userFile.resolveSibling(userFile.getFileName() + BACKUP_EXT);
        Files.copy(userFile, backup, StandardCopyOption.REPLACE_EXISTING);
        if (options.verbose()) {
            LOGGER.info("Backup created: {}", backup);
        }
    }

    private static void writeTranslationFile(Path file, Map<String, String> map, boolean isYaml,
                                             Charset charset) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Translation file").append(LINE_SEP);
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (isYaml) {
                sb.append(e.getKey()).append(": \"").append(escapeYaml(e.getValue())).append("\"").append(LINE_SEP);
            } else {
                sb.append(e.getKey()).append("=").append(e.getValue()).append(LINE_SEP);
            }
        }
        Files.writeString(file, sb.toString(), charset);
    }

    private static String escapeYaml(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }
}
