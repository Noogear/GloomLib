package gloomlib.translation.factory;

import gloomlib.translation.api.TranslationManager;
import gloomlib.translation.api.TranslationMigrator;
import gloomlib.translation.api.TranslationSource;
import gloomlib.translation.config.FileSourceOptions;
import gloomlib.translation.source.PropertiesTranslationSource;
import gloomlib.translation.source.YamlTranslationSource;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

/**
 * Factory for creating TranslationSource instances with automatic file creation and migration support.
 *
 * @since 1.0.0
 */
public final class FileSourceFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileSourceFactory.class);
    private static final String LANG_VERSION_KEY = "lang-version";
    
    private static final String YAML_EXT = ".yml";
    private static final String YAML_ALT_EXT = ".yaml";
    private static final String PROPERTIES_EXT = ".properties";
    private static final String BACKUP_EXT = ".bak";
    
    private static final String COMMENT_AUTO_UPDATED = "# Auto-updated translation file";
    private static final String COMMENT_TRANSLATION_FILE = "# Translation file";
    private static final String COMMENT_ADDED_BY_UPDATER = "# Added by auto-updater";
    private static final String LINE_SEP = System.lineSeparator();

    private FileSourceFactory() {
        throw new UnsupportedOperationException();
    }

    /**
     * Creates a translation source from a file.
     *
     * @param dataFolder the folder containing translation files
     * @param fileName the file name (e.g., "en_US.yml")
     * @param locale the locale for this source
     * @return the translation source
     * @throws Exception if the file cannot be loaded
     */
    public static TranslationSource create(@NotNull Path dataFolder, @NotNull String fileName, @NotNull Locale locale) throws Exception {
        return create(dataFolder, fileName, locale, FileSourceOptions.defaults());
    }

    /**
     * Creates a translation source from a file with custom options.
     *
     * @param dataFolder the folder containing translation files
     * @param fileName the file name (e.g., "en_US.yml")
     * @param locale the locale for this source
     * @param options the loading options
     * @return the translation source
     * @throws Exception if the file cannot be loaded
     */
    public static TranslationSource create(@NotNull Path dataFolder, @NotNull String fileName,
                                           @NotNull Locale locale, @NotNull FileSourceOptions options) throws Exception {
        Path filePath = dataFolder.resolve(fileName);

        String prefix = options.internalPathPrefix();
        if (!prefix.endsWith("/")) prefix += "/";
        if (fileName.startsWith("/")) fileName = fileName.substring(1);

        String resourcePath = prefix + fileName;

        try (InputStream resourceStream = FileSourceFactory.class.getClassLoader().getResourceAsStream(resourcePath)) {

            if (!Files.exists(dataFolder)) {
                Files.createDirectories(dataFolder);
            }

            if (resourceStream != null) {
                if (!Files.exists(filePath)) {
                    if (options.createIfMissing()) {
                        if (options.verbose()) LOGGER.info("Creating locale file: {}", fileName);
                        Files.copy(resourceStream, filePath);
                    }
                } else {
                    boolean needsVersionUpdate = false;
                    if (options.langVersion() != null) {
                        needsVersionUpdate = checkVersionUpdate(filePath, options.langVersion(), isYaml(fileName), options);
                    }

                    if (needsVersionUpdate) {
                        updateWithVersion(filePath, resourceStream, isYaml(fileName), options);
                    } else if (options.enableMigration() && options.scope() != FileSourceOptions.Scope.NONE) {
                        migrate(filePath, resourceStream, isYaml(fileName), options);
                    }
                }
            } else {
                if (!Files.exists(filePath)) {
                    throw new FileNotFoundException("Translation file not found: " + fileName + " (Resource: " + resourcePath + ")");
                }
                if (options.verbose()) LOGGER.warn("Internal resource missing for {}, skipping migration.", fileName);
            }
        }

        if (isYaml(fileName)) {
            return new YamlTranslationSource(filePath, locale);
        } else if (isProperties(fileName)) {
            return new PropertiesTranslationSource(filePath, locale);
        } else {
            throw new IllegalArgumentException("Unsupported format: " + fileName);
        }
    }

    private static boolean isYaml(String name) {
        return name.endsWith(YAML_EXT) || name.endsWith(YAML_ALT_EXT);
    }

    private static boolean isProperties(String name) {
        return name.endsWith(PROPERTIES_EXT);
    }

    /**
     * Checks if the file needs a version update.
     */
    private static boolean checkVersionUpdate(Path userFile, String expectedVersion,
                                              boolean isYaml, FileSourceOptions options) {
        try {
            String content = Files.readString(userFile, options.charset());
            Map<String, String> map = parseToMap(content, isYaml);
            String currentVersion = map.get(LANG_VERSION_KEY);

            if (currentVersion == null || !currentVersion.equals(expectedVersion)) {
                if (options.verbose()) {
                    LOGGER.info("Version mismatch for {}: current={}, expected={}",
                            userFile.getFileName(), currentVersion, expectedVersion);
                }
                return true;
            }
            return false;
        } catch (Exception e) {
            LOGGER.warn("Failed to check version for {}", userFile, e);
            return false;
        }
    }

    /**
     * Updates the file with new version, merging user customizations with internal defaults.
     */
    private static void updateWithVersion(Path userFile, InputStream internalStream,
                                          boolean isYaml, FileSourceOptions options) {
        try {
            if (options.backupBeforeMigration()) {
                createBackup(userFile, options);
            }

            String userContent = Files.readString(userFile, options.charset());
            Map<String, String> userMap = parseToMap(userContent, isYaml);

            String internalContent = new String(internalStream.readAllBytes(), options.charset());
            Map<String, String> internalMap = parseToMap(internalContent, isYaml);

            Map<String, String> merged = new LinkedHashMap<>(internalMap);
            for (Map.Entry<String, String> entry : userMap.entrySet()) {
                if (!LANG_VERSION_KEY.equals(entry.getKey())) {
                    merged.put(entry.getKey(), entry.getValue());
                }
            }

            if (internalMap.containsKey(LANG_VERSION_KEY)) {
                merged.put(LANG_VERSION_KEY, internalMap.get(LANG_VERSION_KEY));
            } else if (options.langVersion() != null) {
                merged.put(LANG_VERSION_KEY, options.langVersion());
            }

            String content = buildTranslationContent(merged, isYaml, COMMENT_AUTO_UPDATED);
            writeTranslationFile(userFile, content, options);

            if (options.verbose()) {
                LOGGER.info("Updated {} with new version: {}", userFile.getFileName(), options.langVersion());
            }

        } catch (Exception e) {
            LOGGER.error("Version update failed for " + userFile, e);
        }
    }

    private static void migrate(Path userFile, InputStream internalStream, boolean isYaml, FileSourceOptions options) {
        try {
            FileSourceOptions.Scope scope = options.scope();
            if (scope == FileSourceOptions.Scope.NONE && options.migrator() == null) {
                return;
            }

            String userContent = Files.readString(userFile, options.charset());
            Map<String, String> userMap = parseToMap(userContent, isYaml);
            Set<String> userKeys = new LinkedHashSet<>(userMap.keySet());

            String internalContent = new String(internalStream.readAllBytes(), options.charset());
            Map<String, String> internalMap = parseToMap(internalContent, isYaml);
            Set<String> internalKeys = internalMap.keySet();

            TranslationMigrator migrator = options.migrator();
            Map<String, String> migratedMap = new LinkedHashMap<>();
            boolean hasMigrations = false;

            if (migrator != null && migrator.shouldMigrate(userFile.getFileName().toString(), userMap)) {
                String fileName = userFile.getFileName().toString();
                String localeName = fileName.substring(0, fileName.lastIndexOf('.'));
                Locale locale = TranslationManager.parseLocale(localeName);
                if (locale == null) locale = Locale.getDefault();

                for (Map.Entry<String, String> entry : userMap.entrySet()) {
                    TranslationMigrator.Migration migration = migrator.migrate(locale, entry.getKey(), entry.getValue());
                    if (migration == null) {
                        migratedMap.put(entry.getKey(), entry.getValue());
                    } else if (migration.drop()) {
                        hasMigrations = true;
                        if (options.verbose()) {
                            LOGGER.info("Migrator dropped key: {}", entry.getKey());
                        }
                    } else {
                        hasMigrations = true;
                        migratedMap.put(migration.key(), migration.value());
                        if (options.verbose() && !entry.getKey().equals(migration.key())) {
                            LOGGER.info("Migrator renamed: {} -> {}", entry.getKey(), migration.key());
                        }
                    }
                }

                if (hasMigrations) {
                    userMap = migratedMap;
                    userKeys = new LinkedHashSet<>(userMap.keySet());
                }
            }

            if (scope == FileSourceOptions.Scope.NONE) {
                if (hasMigrations) {
                    writeMigrationResult(userFile, userMap, isYaml, options, 0, 0);
                }
                return;
            }

            boolean needsFill = scope == FileSourceOptions.Scope.FILL || scope == FileSourceOptions.Scope.FILTER_AND_FILL;
            boolean needsFilter = scope == FileSourceOptions.Scope.FILTER || scope == FileSourceOptions.Scope.FILTER_AND_FILL;

            List<String> keysToAdd = new ArrayList<>();
            List<String> keysToRemove = new ArrayList<>();

            if (needsFill) {
                for (String key : internalKeys) {
                    if (!userKeys.contains(key)) {
                        keysToAdd.add(key);
                    }
                }
            }

            if (needsFilter) {
                for (String key : userKeys) {
                    if (!internalKeys.contains(key) && !LANG_VERSION_KEY.equals(key)) {
                        keysToRemove.add(key);
                    }
                }
            }

            if (keysToAdd.isEmpty() && keysToRemove.isEmpty()) {
                return;
            }

            if (options.backupBeforeMigration()) {
                try {
                    createBackup(userFile, options);
                } catch (IOException e) {
                    LOGGER.warn("Backup failed for {}", userFile, e);
                }
            }

            if (needsFilter && !keysToRemove.isEmpty()) {
                Map<String, String> merged = new LinkedHashMap<>();

                for (String key : internalKeys) {
                    if (userMap.containsKey(key)) {
                        merged.put(key, userMap.get(key));
                    } else if (needsFill) {
                        merged.put(key, internalMap.get(key));
                    }
                }

                String comment = "# Translation file (scope: " + scope + ")";
                String content = buildTranslationContent(merged, isYaml, comment);
                writeTranslationFile(userFile, content, options);

                if (options.verbose()) {
                    LOGGER.info("Migrated {}: added {} keys, removed {} keys",
                            userFile.getFileName(), keysToAdd.size(), keysToRemove.size());
                }

            } else if (!keysToAdd.isEmpty()) {
                Map<String, String> newEntries = new LinkedHashMap<>();
                for (String key : keysToAdd) {
                    newEntries.put(key, internalMap.get(key));
                }

                String appendContent = LINE_SEP + buildTranslationContent(newEntries, isYaml, COMMENT_ADDED_BY_UPDATER);
                Files.writeString(userFile, appendContent, options.charset(),
                        java.nio.file.StandardOpenOption.APPEND);

                if (options.verbose()) {
                    LOGGER.info("Migrated {}: added {} keys", userFile.getFileName(), keysToAdd.size());
                }
            }

        } catch (Exception e) {
            LOGGER.error("Migration failed for " + userFile, e);
        }
    }

    private static String escapeYaml(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    /**
     * Writes migration result to file.
     */
    private static void writeMigrationResult(Path userFile, Map<String, String> content,
                                             boolean isYaml, FileSourceOptions options,
                                             int added, int removed) {
        try {
            if (options.backupBeforeMigration()) {
                try {
                    createBackup(userFile, options);
                } catch (IOException e) {
                    LOGGER.warn("Backup failed for {}", userFile, e);
                }
            }

            String fileContent = buildTranslationContent(content, isYaml, COMMENT_TRANSLATION_FILE);
            writeTranslationFile(userFile, fileContent, options);

            if (options.verbose()) {
                LOGGER.info("Migrated {}: added {} keys, removed {} keys",
                        userFile.getFileName(), added, removed);
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write migration result for " + userFile, e);
        }
    }

    private static Map<String, String> parseToMap(String content, boolean isYaml) {
        Map<String, String> result = new LinkedHashMap<>();
        if (content == null || content.isBlank()) return result;

        if (isYaml) {
            try (Scanner scanner = new Scanner(content)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine().trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    int idx = line.indexOf(':');
                    if (idx > 0) {
                        String key = line.substring(0, idx).trim();
                        String val = (idx + 1 < line.length()) ? line.substring(idx + 1).trim().replaceAll("^[\"']|[\"']$", "") : "";
                        result.put(key, val);
                    }
                }
            } catch (Exception ignored) {
            }
        } else {
            Properties props = new Properties();
            try {
                props.load(new StringReader(content));
            } catch (IOException ignored) {
            }
            for (String k : props.stringPropertyNames()) result.put(k, props.getProperty(k));
        }
        return result;
    }

    private static void createBackup(Path userFile, FileSourceOptions options) throws IOException {
        Path backup = userFile.resolveSibling(userFile.getFileName() + BACKUP_EXT);
        Files.copy(userFile, backup, StandardCopyOption.REPLACE_EXISTING);
        if (options.verbose()) {
            LOGGER.info("Backup created: {}", backup);
        }
    }

    private static String buildTranslationContent(Map<String, String> translations, boolean isYaml, String comment) {
        StringBuilder sb = new StringBuilder();
        if (comment != null && !comment.isEmpty()) {
            sb.append(comment).append(LINE_SEP);
        }

        for (Map.Entry<String, String> entry : translations.entrySet()) {
            if (isYaml) {
                sb.append(entry.getKey()).append(": \"")
                        .append(escapeYaml(entry.getValue())).append("\"")
                        .append(LINE_SEP);
            } else {
                sb.append(entry.getKey()).append("=").append(entry.getValue())
                        .append(LINE_SEP);
            }
        }
        return sb.toString();
    }

    private static void writeTranslationFile(Path file, String content, FileSourceOptions options) throws IOException {
        Files.writeString(file, content, options.charset());
    }
}
