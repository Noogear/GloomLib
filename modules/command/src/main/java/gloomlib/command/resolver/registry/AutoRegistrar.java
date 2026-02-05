package gloomlib.command.resolver.registry;

import com.mojang.brigadier.arguments.ArgumentType;
import gloomlib.command.resolver.ArgumentResolver;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Automatically discovers and registers Paper ArgumentTypes.
 *
 * @implNote Thread-safe caching using AtomicReference.
 */
public final class AutoRegistrar {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(AutoRegistrar.class);
    private static final String LOG_PREFIX = "[AutoRegistrar]";
    private static final String ARGUMENT_TYPES_CLASS = "io.papermc.paper.command.brigadier.argument.ArgumentTypes";
    private static final AtomicReference<List<TypeInfo>> CACHED_TYPES = new AtomicReference<>();

    // Debug messages
    private static final String MSG_REGISTERED = LOG_PREFIX + " registered {}() -> {}";

    private AutoRegistrar() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Registers all auto-discoverable types.
     *
     * @param registry Argument resolver registry
     * @return Number of successfully registered types
     */
    public static int registerAll(@NotNull ArgumentResolverRegistry registry) {
        var types = discoverAllTypes();
        int registered = 0;

        for (var info : types) {
            if (!info.canAutoInvoke() || info.targetType() == null) {
                continue;
            }

            try {
                registerTypeWithSmartParams(registry, info);
                registered++;
            } catch (Exception ignored) {
                // Silently skip types that cannot be registered
            }
        }

        return registered;
    }

    /**
     * Registers only parameterless types.
     *
     * @param registry Argument resolver registry
     * @return Number of successfully registered types
     */
    public static int registerParameterless(@NotNull ArgumentResolverRegistry registry) {
        var types = discoverAllTypes();
        int registered = 0;

        for (var info : types) {
            if (info.parameterCount() > 0 || info.targetType() == null) {
                continue;
            }

            try {
                registerType(registry, info);
                registered++;
            } catch (Exception ignored) {
                // Silently skip registration failures
            }
        }

        return registered;
    }

    public static void listAutoRegisterableTypes() {
        var types = discoverAllTypes();

        StringBuilder sb = new StringBuilder();
        appendDebugLine(sb, "Auto-registrable Paper ArgumentTypes");

        int parameterless = 0;
        int withDefaults = 0;

        for (var info : types) {
            if (!info.canAutoInvoke() || info.targetType() == null) {
                continue;
            }

                String params = "";
            if (info.parameterCount() > 0) {
                params = new StringBuilder()
                    .append(" (defaults: ")
                    .append(info.getParameterDescription())
                    .append(')')
                    .toString();
            }

            String methodName = new StringBuilder()
                    .append(info.methodName())
                    .append("()")
                    .toString();

                    appendDebugLine(sb, String.format("  %-30s -> %-25s%s",
                        methodName,
                        info.targetType().getSimpleName(),
                        params));

            if (info.parameterCount() == 0) {
                parameterless++;
            } else {
                withDefaults++;
            }
        }

        appendDebugLine(sb, String.format("Total: %d", parameterless + withDefaults));
        appendDebugLine(sb, String.format("Parameterless: %d", parameterless));
        appendDebugLine(sb, String.format("With defaults: %d", withDefaults));

        LOGGER.debug(sb.toString());
    }

    @SuppressWarnings("unchecked")
    private static <T> void registerTypeWithSmartParams(
            ArgumentResolverRegistry registry,
            TypeInfo info) throws Exception {

        Class<T> targetType = (Class<T>) info.targetType();
        Method method = info.method();
        String methodName = info.methodName();

        ArgumentResolver<T> resolver = BrigadierResolver.of(targetType, param -> {
            try {
                if (info.parameterCount() == 0) {
                    return (ArgumentType<?>) method.invoke(null);
                } else {
                    Object[] args = createSmartArguments(info, param);
                    return (ArgumentType<?>) method.invoke(null, args);
                }
            } catch (Exception e) {
                throw new RuntimeException("创建 ArgumentType 失败: " + methodName, e);
            }
        });

        registry.register(targetType, resolver);

        LOGGER.debug(MSG_REGISTERED, methodName, targetType.getSimpleName());
    }

    @SuppressWarnings("unchecked")
    private static <T> void registerType(
            ArgumentResolverRegistry registry,
            TypeInfo info) throws Exception {

        Class<T> targetType = (Class<T>) info.targetType();
        Method method = info.method();

        ArgumentResolver<T> resolver = BrigadierResolver.of(targetType, param -> {
            try {
                if (info.parameterCount() == 0) {
                    return (ArgumentType<?>) method.invoke(null);
                } else {
                    Object[] defaultArgs = createDefaultArguments(info.parameterTypes());
                    return (ArgumentType<?>) method.invoke(null, defaultArgs);
                }
            } catch (Exception e) {
                throw new RuntimeException("创建 ArgumentType 失败: " + info.methodName(), e);
            }
        });

        registry.register(targetType, resolver);

        LOGGER.debug(MSG_REGISTERED, info.methodName(), targetType.getSimpleName());
    }

    private static Object[] createSmartArguments(
            TypeInfo info,
            java.lang.reflect.Parameter param) {

        String methodName = info.methodName();
        Class<?>[] paramTypes = info.parameterTypes();
        Object[] args = new Object[paramTypes.length];

        for (int i = 0; i < paramTypes.length; i++) {
            if (paramTypes[i] == boolean.class) {
                args[i] = getBooleanArgument(methodName, param);
            } else if (paramTypes[i] == int.class) {
                args[i] = getIntArgument(methodName, param);
            } else {
                throw new IllegalArgumentException(
                        "不支持的参数类型: " + paramTypes[i].getName()
                );
            }
        }

        return args;
    }

    private static boolean getBooleanArgument(String methodName, java.lang.reflect.Parameter param) {
        if (methodName.contains("position") || methodName.contains("Position")) {
            return true;
        }

        return true;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int getIntArgument(String methodName, java.lang.reflect.Parameter param) {
        if (param != null) {
            try {
                Class<?> rangeClass = Class.forName("gloomlib.command.annotation.Range");
                Object rangeAnnotation = param.getAnnotation((Class) rangeClass);

                if (rangeAnnotation != null) {
                    java.lang.reflect.Method minMethod = rangeClass.getMethod("min");
                    double min = (double) minMethod.invoke(rangeAnnotation);
                    return (int) min;
                }
            } catch (Exception e) {
            }
        }

        if (methodName.equals("time")) {
            return 0;
        }

        return 0;
    }

    private static Object[] createDefaultArguments(Class<?>[] parameterTypes) {
        Object[] args = new Object[parameterTypes.length];

        for (int i = 0; i < parameterTypes.length; i++) {
            if (parameterTypes[i] == boolean.class) {
                args[i] = true;
            } else if (parameterTypes[i] == int.class) {
                args[i] = 0;
            } else {
                throw new IllegalArgumentException(
                        "不支持的参数类型: " + parameterTypes[i].getName()
                );
            }
        }

        return args;
    }

    /**
     * Discovers all available ArgumentTypes from Paper API.
     *
     * @return List of discovered type information
     */
    public static List<TypeInfo> discoverAllTypes() {
        List<TypeInfo> cached = CACHED_TYPES.get();
        if (cached != null) {
            return cached;
        }

        try {
            Class<?> argumentTypesClass = Class.forName(ARGUMENT_TYPES_CLASS);
            Method[] methods = argumentTypesClass.getDeclaredMethods();

            List<TypeInfo> discovered = new ArrayList<>();

            for (Method method : methods) {
                if (!isValidFactoryMethod(method)) {
                    continue;
                }

                TypeInfo info = extractTypeInfo(method);
                if (info != null) {
                    discovered.add(info);
                }
            }

            discovered.sort(Comparator.comparing(TypeInfo::methodName));

            // Atomic cache update
            List<TypeInfo> immutable = List.copyOf(discovered);
            CACHED_TYPES.compareAndSet(null, immutable);
            return immutable;

        } catch (ClassNotFoundException ignored) {
            // Paper API not available
            return Collections.emptyList();
        }
    }

    public static Map<String, List<TypeInfo>> groupByCategory() {
        Map<String, List<TypeInfo>> grouped = new LinkedHashMap<>();

        for (TypeInfo info : discoverAllTypes()) {
            String category = categorize(info);
            grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(info);
        }

        return grouped;
    }

    public static void printAllTypes() {
        StringBuilder sb = new StringBuilder();
        appendDebugLine(sb, "Paper ArgumentTypes discovery");

        var byCategory = groupByCategory();

        for (Map.Entry<String, List<TypeInfo>> entry : byCategory.entrySet()) {
            appendDebugLine(sb, String.format("Category: %s (%d types)",
                entry.getKey(),
                entry.getValue().size()));

            for (TypeInfo info : entry.getValue()) {
                String methodName = new StringBuilder()
                        .append(info.methodName())
                        .append("()")
                        .toString();
                String targetName = info.targetType() != null
                        ? info.targetType().getSimpleName()
                        : "Unknown";

                String params = info.parameterCount() > 0
                    ? " [params: " + info.parameterCount() + "]"
                    : "";
                appendDebugLine(sb, String.format("  %-25s -> %-30s%s", methodName, targetName, params));
            }

            appendDebugLine(sb, "");
        }

        appendDebugLine(sb, String.format("Total: %d", discoverAllTypes().size()));

        LOGGER.debug(sb.toString());
    }

    private static boolean isValidFactoryMethod(Method method) {
        int modifiers = method.getModifiers();

        if (!Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)) {
            return false;
        }

        Class<?> returnType = method.getReturnType();
        return ArgumentType.class.isAssignableFrom(returnType);
    }

    private static TypeInfo extractTypeInfo(Method method) {
        try {
            Type genericReturnType = method.getGenericReturnType();
            Class<?> targetType = null;

            if (genericReturnType instanceof ParameterizedType paramType) {
                Type[] typeArgs = paramType.getActualTypeArguments();
                if (typeArgs.length > 0 && typeArgs[0] instanceof Class<?> clazz) {
                    targetType = clazz;
                }
            }

            Class<?>[] parameterTypes = method.getParameterTypes();

            return new TypeInfo(
                    method.getName(),
                    targetType,
                    parameterTypes.length,
                    parameterTypes,
                    canAutoInvoke(parameterTypes),
                    method
            );

        } catch (Exception e) {
            return null;
        }
    }

    private static boolean canAutoInvoke(Class<?>[] parameterTypes) {

        for (Class<?> paramType : parameterTypes) {
            if (paramType == boolean.class || paramType == int.class) {
                continue;
            }
            return false;
        }

        return true;
    }

    private static String categorize(TypeInfo info) {
        String methodName = info.methodName().toLowerCase();
        String targetName = info.targetType() != null
                ? info.targetType().getSimpleName().toLowerCase()
                : "";

        if (methodName.contains("uuid") || methodName.contains("key")) {
            return "Basics";
        }

        if (methodName.contains("world") || methodName.contains("dimension") ||
                methodName.contains("biome") || methodName.contains("structure")) {
            return "World";
        }

        if (methodName.contains("entity") || methodName.contains("player") ||
                methodName.contains("mob") || targetName.contains("entity") ||
                targetName.contains("player")) {
            return "Entity";
        }

        if (methodName.contains("block") || methodName.contains("position") ||
                targetName.contains("block") || targetName.contains("position")) {
            return "Block";
        }

        if (methodName.contains("item") || methodName.contains("enchant") ||
                methodName.contains("potion") || targetName.contains("item")) {
            return "Item";
        }

        if (methodName.contains("component") || methodName.contains("text") ||
                methodName.contains("color") || targetName.contains("component")) {
            return "Text";
        }

        if (methodName.contains("score") || methodName.contains("team") ||
                methodName.contains("objective") || methodName.contains("criteria")) {
            return "Scoreboard";
        }

        if (methodName.contains("gamemode") || methodName.contains("difficulty")) {
            return "Game";
        }

        if (methodName.contains("time") || methodName.contains("range") ||
                targetName.contains("range")) {
            return "Numeric";
        }

        return "Other";
    }

    private static void appendDebugLine(StringBuilder sb, String line) {
        sb.append(LOG_PREFIX)
                .append(' ')
                .append(line)
                .append('\n');
    }

    public record TypeInfo(
            @NotNull String methodName,
            Class<?> targetType,
            int parameterCount,
            @NotNull Class<?>[] parameterTypes,
            boolean canAutoInvoke,
            @NotNull Method method
    ) {
        public String getParameterDescription() {
            if (parameterCount == 0) {
                return "无参数";
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parameterTypes.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(parameterTypes[i].getSimpleName());
            }
            return sb.toString();
        }
    }
}
