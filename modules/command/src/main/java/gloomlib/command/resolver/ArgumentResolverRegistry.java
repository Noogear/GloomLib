package gloomlib.command.resolver;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 参数解析器注册表。
 *
 * <p>
 * 管理所有参数类型的解析器，支持自动类型匹配。
 * </p>
 */
public class ArgumentResolverRegistry {

    private final Map<Class<?>, ArgumentResolver<?>> resolvers = new ConcurrentHashMap<>();

    /**
     * 注册参数解析器。
     *
     * @param type     参数类型
     * @param resolver 解析器
     * @param <T>      类型
     */
    public <T> void register(Class<T> type, ArgumentResolver<T> resolver) {
        resolvers.put(type, resolver);
    }

    private final Map<Class<?>, ArgumentResolver<?>> resolverCache = new ConcurrentHashMap<>();

    /**
     * 获取指定类型的解析器。
     *
     * @param type 参数类型
     * @param <T>  类型
     * @return 解析器，或 null
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable ArgumentResolver<T> getResolver(Class<T> type) {
        // 尝试从缓存获取
        ArgumentResolver<?> cached = resolverCache.get(type);
        if (cached != null) {
            return (ArgumentResolver<T>) cached;
        }

        ArgumentResolver<?> resolver = findResolver(type);
        if (resolver != null) {
            resolverCache.put(type, resolver);
        }
        return (ArgumentResolver<T>) resolver;
    }

    /**
     * 查找解析器（无缓存）。
     */
    @SuppressWarnings("unchecked")
    private <T> ArgumentResolver<T> findResolver(Class<T> type) {
        // 1. 精确匹配
        ArgumentResolver<?> resolver = resolvers.get(type);
        if (resolver != null) {
            return (ArgumentResolver<T>) resolver;
        }

        // 2. 处理基本类型包装类
        Class<?> primitiveWrapper = getPrimitiveWrapper(type);
        if (primitiveWrapper != null) {
            resolver = resolvers.get(primitiveWrapper);
            if (resolver != null) {
                return (ArgumentResolver<T>) resolver;
            }
        }

        // 3. 继承匹配（查找父类/接口的解析器）
        for (Map.Entry<Class<?>, ArgumentResolver<?>> entry : resolvers.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return (ArgumentResolver<T>) entry.getValue();
            }
        }

        // 4. 枚举类型通用处理
        if (type.isEnum()) {
            return (ArgumentResolver<T>) createEnumResolver(type);
        }

        return null;
    }

    /**
     * 清除缓存。
     */
    public void clearCache() {
        resolverCache.clear();
    }

    /**
     * 检查是否支持指定类型。
     *
     * @param type 参数类型
     * @return 是否支持
     */
    public boolean hasResolver(Class<?> type) {
        return getResolver(type) != null;
    }

    /**
     * 获取所有已注册的解析器。
     *
     * @return 解析器映射
     */
    public Map<Class<?>, ArgumentResolver<?>> getAllResolvers() {
        return Map.copyOf(resolvers);
    }

    /**
     * 获取基本类型的包装类。
     */
    private @Nullable Class<?> getPrimitiveWrapper(Class<?> type) {
        if (type == int.class)
            return Integer.class;
        if (type == long.class)
            return Long.class;
        if (type == double.class)
            return Double.class;
        if (type == float.class)
            return Float.class;
        if (type == boolean.class)
            return Boolean.class;
        if (type == byte.class)
            return Byte.class;
        if (type == short.class)
            return Short.class;
        if (type == char.class)
            return Character.class;
        return null;
    }

    /**
     * 为枚举类型创建通用解析器。
     */
    @SuppressWarnings({ "unchecked", "rawtypes" })
    private <E extends Enum<E>> ArgumentResolver<?> createEnumResolver(Class<?> enumType) {
        return new EnumArgumentResolver<>((Class<E>) enumType);
    }
}
