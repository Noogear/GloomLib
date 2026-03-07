package gloomlib.command.core.resolver.registry;

import com.google.common.base.Suppliers;
import com.mojang.brigadier.arguments.ArgumentType;
import gloomlib.command.api.resolver.ArgumentResolver;
import gloomlib.command.core.resolver.ArgumentResolverRegistry;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;
import java.util.function.Supplier;

/**
 * Automatically discovers and registers Paper ArgumentTypes.
 *
 * <br>
 * <b>Implementation Note:</b> Thread-safe caching using AtomicReference.
 */
public final class AutoRegistrar {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(AutoRegistrar.class);
    private static final String LOG_PREFIX = "[AutoRegistrar]";
    private static final String ARGUMENT_TYPES_CLASS = "io.papermc.paper.command.brigadier.argument.ArgumentTypes";
    @SuppressWarnings("UnstableApiUsage")
    private static final Supplier<List<TypeInfo>> CACHED_TYPES = Suppliers.memoize(AutoRegistrar::computeAllTypes);

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
                registerTypeWithSmartParams(registry, info);
                registered++;
            } catch (Exception ignored) {
                // Silently skip registration failures
            }
        }

        return registered;
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
                    Object[] args = createDefaultArguments(info.parameterTypes());
                    return (ArgumentType<?>) method.invoke(null, args);
                }
            } catch (Exception e) {
                throw new RuntimeException("Failed to create ArgumentType: " + methodName, e);
            }
        });

        registry.register(targetType, resolver);

        LOGGER.debug(MSG_REGISTERED, methodName, targetType.getSimpleName());
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
                        "Unsupported parameter type: " + parameterTypes[i].getName());
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
        return CACHED_TYPES.get();
    }

    private static List<TypeInfo> computeAllTypes() {
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
            return List.copyOf(discovered);

        } catch (ClassNotFoundException ignored) {
            // Paper API not available
            return Collections.emptyList();
        }
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
                    method);

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

    public record TypeInfo(
            @NotNull String methodName,
            Class<?> targetType,
            int parameterCount,
            @NotNull Class<?>[] parameterTypes,
            boolean canAutoInvoke,
            @NotNull Method method) {
    }
}
