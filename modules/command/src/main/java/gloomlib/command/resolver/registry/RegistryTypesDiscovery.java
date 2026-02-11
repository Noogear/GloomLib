package gloomlib.command.resolver.registry;

import gloomlib.command.GloomCommand;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Keyed;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Discovers and registers Paper Registry types automatically.
 *
 * <p>
 * Scans {@link RegistryKey} fields reflectively to extract type information
 * and register {@link BrigadierResolver} instances for each discovered type.
 * </p>
 *
 * <br>
 * <b>Implementation Note:</b> Thread-safe singleton cache using AtomicReference
 * for lazy initialization.
 */
public final class RegistryTypesDiscovery {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(RegistryTypesDiscovery.class);
    private static final String LOG_PREFIX = "[RegistryTypesDiscovery]";
    private static final AtomicReference<List<RegistryTypeInfo>> CACHED_TYPES = new AtomicReference<>();

    // Debug log message templates
    private static final String MSG_REGISTERED_SUCCESS = LOG_PREFIX + " registered {} ({})";
    private static final String MSG_NON_KEYED_SKIP = LOG_PREFIX + " skip non-Keyed type: {}";
    private static final String MSG_EXTRACTION_FAILED = LOG_PREFIX + " extract failed: {} - {}";

    private RegistryTypesDiscovery() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers all discovered Registry types to the given GloomCommand instance.
     *
     * @param gloom GloomCommand instance
     * @return Number of successfully registered types
     */
    public static int registerAllDiscovered(@NotNull GloomCommand gloom) {
        List<RegistryTypeInfo> types = discoverAll();
        int registered = 0;

        for (RegistryTypeInfo info : types) {
            try {
                registerRegistryType(gloom, info);
                registered++;
                LOGGER.debug(MSG_REGISTERED_SUCCESS,
                        info.targetType().getSimpleName(),
                        info.fieldName());
            } catch (Exception ignored) {
                // Silently skip types that cannot be registered
            }
        }

        return registered;
    }

    /**
     * Discovers all Registry types from RegistryKey fields.
     *
     * <p>
     * Results are cached after first discovery for performance.
     * </p>
     *
     * @return List of discovered Registry type information
     */
    public static List<RegistryTypeInfo> discoverAll() {
        List<RegistryTypeInfo> cached = CACHED_TYPES.get();
        if (cached != null) {
            return cached;
        }

        List<RegistryTypeInfo> discovered = new ArrayList<>();

        try {
            Field[] fields = RegistryKey.class.getDeclaredFields();

            for (Field field : fields) {
                if (!isValidRegistryKeyField(field)) {
                    continue;
                }

                RegistryTypeInfo info = extractTypeInfo(field);
                if (info != null) {
                    discovered.add(info);
                }
            }

            discovered.sort(Comparator.comparing(RegistryTypeInfo::fieldName));

            // Atomic cache update
            CACHED_TYPES.compareAndSet(null, List.copyOf(discovered));

        } catch (Exception ignored) {
            // Silently handle discovery failures
        }

        return CACHED_TYPES.get() != null ? CACHED_TYPES.get() : List.of();
    }

    /**
     * Prints a formatted discovery report to the logger.
     *
     * <p>
     * Useful for debugging and verification purposes.
     * </p>
     */
    public static void printDiscoveryReport() {
        List<RegistryTypeInfo> types = discoverAll();

        StringBuilder sb = new StringBuilder();
        appendDebugLine(sb, "Paper Registry types discovery");
        appendDebugLine(sb, String.format("Total: %d", types.size()));
        appendDebugLine(sb, "Field Name                    | Target Type");
        appendDebugLine(sb, "------------------------------|---------------------------");

        for (RegistryTypeInfo info : types) {
            appendDebugLine(sb, String.format("  %-30s | %s",
                    info.fieldName(),
                    info.targetType().getSimpleName()));
        }

        LOGGER.debug(sb.toString());
    }

    /**
     * Checks if a field is a valid RegistryKey field for discovery.
     *
     * @param field Field to check
     * @return true if field is a public static final RegistryKey
     */
    private static boolean isValidRegistryKeyField(@NotNull Field field) {
        int modifiers = field.getModifiers();
        return Modifier.isStatic(modifiers)
                && Modifier.isFinal(modifiers)
                && Modifier.isPublic(modifiers)
                && RegistryKey.class.isAssignableFrom(field.getType());
    }

    @Nullable
    private static RegistryTypeInfo extractTypeInfo(@NotNull Field field) {
        try {
            Type genericType = field.getGenericType();

            if (!(genericType instanceof ParameterizedType paramType)) {
                return null;
            }

            Type[] typeArgs = paramType.getActualTypeArguments();
            if (typeArgs.length == 0) {
                return null;
            }

            Type targetType = typeArgs[0];

            Class<?> targetClass;
            if (targetType instanceof ParameterizedType pt) {
                targetClass = (Class<?>) pt.getRawType();
            } else if (targetType instanceof Class<?>) {
                targetClass = (Class<?>) targetType;
            } else {
                return null;
            }

            if (!Keyed.class.isAssignableFrom(targetClass)) {
                LOGGER.debug(MSG_NON_KEYED_SKIP, targetClass.getName());
                return null;
            }

            RegistryKey<?> registryKey = (RegistryKey<?>) field.get(null);

            return new RegistryTypeInfo(
                    field.getName(),
                    targetClass,
                    registryKey);

        } catch (IllegalAccessException | ClassCastException e) {
            LOGGER.debug(MSG_EXTRACTION_FAILED, field.getName(), e.getMessage());
            return null;
        }
    }

    /**
     * Registers a single Registry type to the GloomCommand instance.
     *
     * @param gloom GloomCommand instance
     * @param info  Registry type information
     * @param <T>   Type parameter extending Keyed
     */
    @SuppressWarnings("unchecked")
    private static <T extends Keyed> void registerRegistryType(
            @NotNull GloomCommand gloom,
            @NotNull RegistryTypeInfo info) {

        Class<T> type = (Class<T>) info.targetType();
        RegistryKey<T> key = (RegistryKey<T>) info.registryKey();

        gloom.registerArgumentResolver(
                type,
                BrigadierResolver.of(type, () -> ArgumentTypes.resource(key)));
    }

    /**
     * Container for discovered Registry type information.
     *
     * @param fieldName   Name of the RegistryKey field
     * @param targetType  Target type class (must extend Keyed)
     * @param registryKey The RegistryKey instance
     */
    public record RegistryTypeInfo(
            @NotNull String fieldName,
            @NotNull Class<?> targetType,
            @NotNull RegistryKey<?> registryKey) {
    }

    private static void appendDebugLine(StringBuilder sb, String line) {
        sb.append(LOG_PREFIX)
                .append(' ')
                .append(line)
                .append('\n');
    }
}
