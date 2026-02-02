package gloomlib.configuration.util;

import com.google.gson.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Advanced type inference utility for resolving complex generic types with caching support.
 * Now supports Gson TypeToken for precise generic type resolution.
 */
public final class TypeInference {

    private static final Map<TypeCacheKey, Class<?>> GENERIC_TYPE_CACHE = new ConcurrentHashMap<>();
    private static final Map<CompatibilityCacheKey, Boolean> COMPATIBILITY_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, Map<TypeVariable<?>, Type>> INHERITANCE_CACHE = new ConcurrentHashMap<>();

    /**
     * Extracts generic parameter type at the specified index.
     * Supports ParameterizedType, WildcardType, TypeVariable, and GenericArrayType.
     *
     * @param type  the generic type
     * @param index the parameter index (0-based)
     * @return the resolved class, or Object.class if resolution fails
     */
    @NotNull
    public static Class<?> extractGenericParameter(@Nullable Type type, int index) {
        if (type == null) {
            return Object.class;
        }

        // Cache check
        TypeCacheKey cacheKey = new TypeCacheKey(type, index);
        Class<?> cached = GENERIC_TYPE_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Class<?> result = extractGenericParameterInternal(type, index);
        GENERIC_TYPE_CACHE.put(cacheKey, result);
        return result;
    }

    private static Class<?> extractGenericParameterInternal(Type type, int index) {
        // 1. ParameterizedType: List<String>, Map<String, Integer>
        if (type instanceof ParameterizedType pt) {
            Type[] args = pt.getActualTypeArguments();
            if (index >= 0 && index < args.length) {
                return resolveType(args[index]);
            }
        }

        // 2. TypeVariable: T extends Number
        if (type instanceof TypeVariable<?> tv) {
            Type[] bounds = tv.getBounds();
            if (bounds.length > 0) {
                return resolveType(bounds[0]);
            }
        }

        // 3. WildcardType: ? extends Number, ? super Integer
        if (type instanceof WildcardType wt) {
            Type[] upperBounds = wt.getUpperBounds();
            if (upperBounds.length > 0) {
                return resolveType(upperBounds[0]);
            }
        }

        // 4. GenericArrayType: T[]
        if (type instanceof GenericArrayType gat) {
            Type componentType = gat.getGenericComponentType();
            Class<?> componentClass = resolveType(componentType);
            return Array.newInstance(componentClass, 0).getClass();
        }

        return Object.class;
    }

    /**
     * Resolves a Type to Class recursively.
     *
     * @param type the type to resolve
     * @return the resolved class
     */
    @NotNull
    private static Class<?> resolveType(@NotNull Type type) {
        // 1. Direct Class
        if (type instanceof Class<?> clazz) {
            return clazz;
        }

        // 2. ParameterizedType: extract raw type
        if (type instanceof ParameterizedType pt) {
            Type rawType = pt.getRawType();
            if (rawType instanceof Class<?> clazz) {
                return clazz;
            }
        }

        // 3. WildcardType: extract upper bound
        if (type instanceof WildcardType wt) {
            Type[] upperBounds = wt.getUpperBounds();
            if (upperBounds.length > 0) {
                return resolveType(upperBounds[0]);
            }
        }

        // 4. TypeVariable: extract upper bound
        if (type instanceof TypeVariable<?> tv) {
            Type[] bounds = tv.getBounds();
            if (bounds.length > 0) {
                return resolveType(bounds[0]);
            }
        }

        // 5. GenericArrayType: extract component type
        if (type instanceof GenericArrayType gat) {
            Type componentType = gat.getGenericComponentType();
            Class<?> componentClass = resolveType(componentType);
            return Array.newInstance(componentClass, 0).getClass();
        }

        return Object.class;
    }

    /**
     * Resolves generic inheritance chain from parent classes and interfaces.
     *
     * @param concreteClass the concrete class
     * @param targetClass   the target class or interface
     * @return the mapping of type variables to actual types
     */
    @NotNull
    public static Map<TypeVariable<?>, Type> resolveInheritanceChain(
            @NotNull Class<?> concreteClass,
            @NotNull Class<?> targetClass) {

        // Cache check
        Map<TypeVariable<?>, Type> cached = INHERITANCE_CACHE.get(concreteClass);
        if (cached != null) {
            return cached;
        }

        Map<TypeVariable<?>, Type> result = new HashMap<>();
        resolveInheritanceChainRecursive(concreteClass, targetClass, result);

        INHERITANCE_CACHE.put(concreteClass, result);
        return result;
    }

    private static void resolveInheritanceChainRecursive(
            Class<?> current,
            Class<?> target,
            Map<TypeVariable<?>, Type> mappings) {

        if (current == null || current == Object.class) {
            return;
        }

        // Check parent class
        Type genericSuperclass = current.getGenericSuperclass();
        if (genericSuperclass instanceof ParameterizedType pt) {
            Class<?> rawClass = (Class<?>) pt.getRawType();
            TypeVariable<?>[] typeParams = rawClass.getTypeParameters();
            Type[] actualArgs = pt.getActualTypeArguments();

            for (int i = 0; i < typeParams.length && i < actualArgs.length; i++) {
                mappings.put(typeParams[i], actualArgs[i]);
            }

            if (rawClass == target) {
                return;
            }
            resolveInheritanceChainRecursive(rawClass, target, mappings);
        }

        // Check interfaces
        for (Type genericInterface : current.getGenericInterfaces()) {
            if (genericInterface instanceof ParameterizedType pt) {
                Class<?> rawClass = (Class<?>) pt.getRawType();
                TypeVariable<?>[] typeParams = rawClass.getTypeParameters();
                Type[] actualArgs = pt.getActualTypeArguments();

                for (int i = 0; i < typeParams.length && i < actualArgs.length; i++) {
                    mappings.put(typeParams[i], actualArgs[i]);
                }

                if (rawClass == target) {
                    return;
                }
                resolveInheritanceChainRecursive(rawClass, target, mappings);
            }
        }
    }

    /**
     * Checks type compatibility.
     *
     * @param sourceType the source type
     * @param targetType the target type
     * @return true if compatible
     */
    public static boolean isCompatible(@Nullable Class<?> sourceType, @Nullable Class<?> targetType) {
        if (sourceType == null || targetType == null) {
            return false;
        }

        if (sourceType == targetType) {
            return true;
        }

        // Cache check
        CompatibilityCacheKey cacheKey = new CompatibilityCacheKey(sourceType, targetType);
        Boolean cached = COMPATIBILITY_CACHE.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        boolean result = targetType.isAssignableFrom(sourceType);
        COMPATIBILITY_CACHE.put(cacheKey, result);
        return result;
    }

    /**
     * Infers the value type from a field (e.g., Map value type, List element type).
     *
     * @param field the field to infer from
     * @return the inferred type
     */
    @NotNull
    public static Class<?> inferFieldType(@NotNull Field field) {
        Class<?> fieldType = field.getType();

        // 1. Map<K, V> -> infer V type
        if (Map.class.isAssignableFrom(fieldType)) {
            Type genericType = field.getGenericType();
            return extractGenericParameter(genericType, 1);
        }

        // 2. List<T> -> infer T type
        if (List.class.isAssignableFrom(fieldType)) {
            Type genericType = field.getGenericType();
            return extractGenericParameter(genericType, 0);
        }

        // 3. Set<T> -> infer T type
        if (Set.class.isAssignableFrom(fieldType)) {
            Type genericType = field.getGenericType();
            return extractGenericParameter(genericType, 0);
        }

        return fieldType;
    }

    /**
     * Gets all generic parameter types from a field.
     *
     * @param field the field to extract from
     * @return an array of generic parameter types
     */
    @NotNull
    public static Class<?>[] getGenericParameters(@NotNull Field field) {
        Type genericType = field.getGenericType();
        if (!(genericType instanceof ParameterizedType pt)) {
            return new Class<?>[0];
        }

        Type[] args = pt.getActualTypeArguments();
        Class<?>[] result = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            result[i] = resolveType(args[i]);
        }
        return result;
    }

    /**
     * Clears all caches.
     */
    public static void clearCaches() {
        GENERIC_TYPE_CACHE.clear();
        COMPATIBILITY_CACHE.clear();
        INHERITANCE_CACHE.clear();
    }

    /**
     * Gets cache statistics.
     *
     * @return the cache size information
     */
    @NotNull
    public static String getCacheStats() {
        return String.format("TypeInference Caches: Generic=%d, Compatibility=%d, Inheritance=%d",
                GENERIC_TYPE_CACHE.size(),
                COMPATIBILITY_CACHE.size(),
                INHERITANCE_CACHE.size());
    }

    /**
     * Extracts generic parameter from a TypeToken at the specified index.
     * <p>
     * This method provides precise generic type resolution for complex types like
     * {@code Map<UUID, List<ItemStack>>} using Gson's TypeToken.
     * </p>
     *
     * @param typeToken the TypeToken containing generic type information
     * @param index     the parameter index (0-based)
     * @return the resolved class, or Object.class if resolution fails
     */
    @NotNull
    public static Class<?> extractGenericParameter(@NotNull TypeToken<?> typeToken, int index) {
        return extractGenericParameter(typeToken.getType(), index);
    }

    /**
     * Gets the raw type from a TypeToken.
     *
     * @param typeToken the type token
     * @return the raw type class
     */
    @NotNull
    public static Class<?> getRawType(@NotNull TypeToken<?> typeToken) {
        return typeToken.getRawType();
    }

    // ======================== TypeToken Support ========================

    /**
     * Gets the full generic Type from a TypeToken.
     *
     * @param typeToken the type token
     * @return the generic type
     */
    @NotNull
    public static Type getType(@NotNull TypeToken<?> typeToken) {
        return typeToken.getType();
    }

    private record TypeCacheKey(Type type, int index) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof TypeCacheKey(Type type1, int index1))) return false;
            return index == index1 && Objects.equals(type, type1);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, index);
        }
    }

    private record CompatibilityCacheKey(Class<?> source, Class<?> target) {
        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (!(obj instanceof CompatibilityCacheKey(Class<?> source1, Class<?> target1))) return false;
            return source == source1 && target == target1;
        }

        @Override
        public int hashCode() {
            return Objects.hash(System.identityHashCode(source), System.identityHashCode(target));
        }
    }
}
