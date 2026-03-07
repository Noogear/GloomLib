package gloomlib.configuration.integration;

import gloomlib.configuration.ConfigurationFile;
import gloomlib.configuration.ConfigurationPart;
import gloomlib.configuration.annotations.Sensitive;
import gloomlib.configuration.util.NamingUtils;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Integration with Spark profiler for configuration monitoring.
 * Automatically registers configuration files with Spark's server config provider
 * and hides sensitive fields marked with {@link Sensitive} annotation.
 */
public final class SparkConfigIntegration {

    private static final String SPARK_EXTRA_CONFIGS_PROPERTY = "spark.serverconfigs.extra";
    private static final String SPARK_HIDDEN_PATHS_PROPERTY = "spark.serverconfigs.hiddenpaths";

    private static final List<String> registeredConfigs = new ArrayList<>();
    private static final List<String> hiddenPaths = new ArrayList<>();

    private SparkConfigIntegration() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers a configuration file with Spark profiler.
     *
     * @param file        the configuration file
     * @param configClass the configuration class (to scan for @Sensitive fields)
     */
    public static void register(@NotNull File file, @NotNull Class<? extends ConfigurationFile> configClass) {
        String relativePath = file.getPath().replace("\\", "/").replace("./", "");

        // Add to registered configs
        if (!registeredConfigs.contains(relativePath)) {
            registeredConfigs.add(relativePath);
        }

        // Scan for @Sensitive fields
        scanSensitiveFields(configClass, "");

        // Update system properties
        updateSparkProperties();
    }

    /**
     * Registers multiple configuration files.
     *
     * @param configs array of (file, class) pairs
     */
    @SafeVarargs
    public static void registerAll(@NotNull ConfigPair<? extends ConfigurationFile>... configs) {
        for (ConfigPair<? extends ConfigurationFile> pair : configs) {
            register(pair.file, pair.configClass);
        }
    }

    /**
     * Manually adds a hidden path (for nested sensitive values).
     *
     * @param path the YAML path to hide (e.g., "database.password")
     */
    public static void addHiddenPath(@NotNull String path) {
        if (!hiddenPaths.contains(path)) {
            hiddenPaths.add(path);
            updateSparkProperties();
        }
    }

    /**
     * Gets the list of registered configuration files.
     *
     * @return list of file paths
     */
    @NotNull
    public static List<String> getRegisteredConfigs() {
        return new ArrayList<>(registeredConfigs);
    }

    /**
     * Gets the list of hidden paths.
     *
     * @return list of YAML paths
     */
    @NotNull
    public static List<String> getHiddenPaths() {
        return new ArrayList<>(hiddenPaths);
    }

    /**
     * Checks if Spark is available in the runtime environment.
     *
     * @return true if Spark plugin is loaded or spark.enabled is true
     */
    public static boolean isSparkAvailable() {
        try {
            // Check if spark is enabled in Paper configuration
            Class<?> globalConfigClass = Class.forName("io.papermc.paper.configuration.GlobalConfiguration");
            Object globalConfig = globalConfigClass.getMethod("get").invoke(null);
            Object sparkConfig = globalConfig.getClass().getField("spark").get(globalConfig);
            boolean enabled = (boolean) sparkConfig.getClass().getField("enabled").get(sparkConfig);
            if (enabled) {
                return true;
            }
        } catch (Exception ignored) {
            // Expected: Spark may not be available or configuration structure may differ
        }

        // Check if Spark plugin is loaded
        try {
            Class.forName("me.lucko.spark.api.Spark");
            return true;
        } catch (ClassNotFoundException ignored) {
            // Expected: Spark plugin may not be installed
        }

        return false;
    }

    private static void scanSensitiveFields(Class<?> clazz, String prefix) {
        for (Field field : clazz.getFields()) {
            if (field.isAnnotationPresent(Sensitive.class)) {
                Sensitive annotation = field.getAnnotation(Sensitive.class);
                if (annotation.hideFromMonitoring()) {
                    String path = prefix.isEmpty() ?
                            NamingUtils.camelToKebab(field.getName()) :
                            prefix + "." + NamingUtils.camelToKebab(field.getName());
                    hiddenPaths.add(path);
                }
            }

            // Recursively scan nested ConfigurationPart
            if (ConfigurationPart.class.isAssignableFrom(field.getType())) {
                String newPrefix = prefix.isEmpty() ?
                        NamingUtils.camelToKebab(field.getName()) :
                        prefix + "." + NamingUtils.camelToKebab(field.getName());
                scanSensitiveFields(field.getType(), newPrefix);
            }
        }
    }

    private static void updateSparkProperties() {
        // Update extra configs
        String existingConfigs = System.getProperty(SPARK_EXTRA_CONFIGS_PROPERTY, "");
        List<String> allConfigs = new ArrayList<>(registeredConfigs);
        if (!existingConfigs.isEmpty()) {
            allConfigs.addAll(List.of(existingConfigs.split(",")));
        }
        String newConfigs = allConfigs.stream().distinct().collect(Collectors.joining(","));
        System.setProperty(SPARK_EXTRA_CONFIGS_PROPERTY, newConfigs);

        // Update hidden paths
        String existingHidden = System.getProperty(SPARK_HIDDEN_PATHS_PROPERTY, "");
        List<String> allHidden = new ArrayList<>(hiddenPaths);
        if (!existingHidden.isEmpty()) {
            allHidden.addAll(List.of(existingHidden.split(",")));
        }
        String newHidden = allHidden.stream().distinct().collect(Collectors.joining(","));
        System.setProperty(SPARK_HIDDEN_PATHS_PROPERTY, newHidden);
    }

    /**
     * Helper record for batch registration.
     *
     * @param <T>         configuration type
     * @param file        configuration file
     * @param configClass configuration class
     */
    public record ConfigPair<T extends ConfigurationFile>(File file, Class<T> configClass) {

        /**
         * Creates a ConfigPair instance.
         *
         * @param <T>         configuration type
         * @param file        configuration file
         * @param configClass configuration class
         * @return new ConfigPair instance
         */
        public static <T extends ConfigurationFile> ConfigPair<T> of(File file, Class<T> configClass) {
            return new ConfigPair<>(file, configClass);
        }
    }
}
