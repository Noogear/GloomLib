package gloomlib.command.injection;

import gloomlib.command.annotation.Inject;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 依赖注入器。
 *
 * <p>
 * 管理服务实例的注册和注入。
 * </p>
 */
public class DependencyInjector {

    /** 按类型存储的单例服务 */
    private final Map<Class<?>, Object> singletons = new ConcurrentHashMap<>();

    /** 按限定符存储的服务（用于同类型多实例） */
    private final Map<String, Object> qualifiedBeans = new ConcurrentHashMap<>();

    /**
     * 注册单例服务。
     *
     * @param type     服务类型
     * @param instance 服务实例
     * @param <T>      类型
     */
    public <T> void registerSingleton(Class<T> type, T instance) {
        singletons.put(type, instance);
    }

    /**
     * 注册带限定符的服务。
     *
     * @param qualifier 限定符
     * @param instance  服务实例
     * @param <T>       类型
     */
    public <T> void registerBean(String qualifier, T instance) {
        qualifiedBeans.put(qualifier, instance);
    }

    /**
     * 获取服务实例。
     *
     * @param type 服务类型
     * @param <T>  类型
     * @return 服务实例，或 null
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getService(Class<T> type) {
        return (T) singletons.get(type);
    }

    /**
     * 获取带限定符的服务实例。
     *
     * @param qualifier 限定符
     * @param <T>       类型
     * @return 服务实例，或 null
     */
    @SuppressWarnings("unchecked")
    public <T> @Nullable T getService(String qualifier) {
        return (T) qualifiedBeans.get(qualifier);
    }

    /**
     * 向目标对象注入依赖。
     *
     * @param target 目标对象
     */
    public void injectDependencies(Object target) {
        Class<?> clazz = target.getClass();

        // 遍历所有字段（包括父类）
        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (field.isAnnotationPresent(Inject.class)) {
                    injectField(target, field);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }

    /**
     * 注入单个字段。
     */
    private void injectField(Object target, Field field) {
        Inject inject = field.getAnnotation(Inject.class);
        String qualifier = inject.value();

        Object dependency;

        if (!qualifier.isEmpty()) {
            // 使用限定符查找
            dependency = qualifiedBeans.get(qualifier);
        } else {
            // 按类型查找
            dependency = findByType(field.getType());
        }

        if (dependency == null) {
            throw new RuntimeException(
                    "无法注入依赖: " + field.getDeclaringClass().getName() + "." + field.getName() +
                            " (类型: " + field.getType().getName() + ", 限定符: " + qualifier + ")");
        }

        try {
            field.setAccessible(true);
            field.set(target, dependency);
        } catch (IllegalAccessException e) {
            throw new RuntimeException("依赖注入失败", e);
        }
    }

    /**
     * 按类型查找服务（支持继承匹配）。
     */
    private @Nullable Object findByType(Class<?> type) {
        // 精确匹配
        Object result = singletons.get(type);
        if (result != null) {
            return result;
        }

        // 继承匹配
        for (Map.Entry<Class<?>, Object> entry : singletons.entrySet()) {
            if (type.isAssignableFrom(entry.getKey())) {
                return entry.getValue();
            }
        }

        return null;
    }

    /**
     * 清空所有注册的服务。
     */
    public void clear() {
        singletons.clear();
        qualifiedBeans.clear();
    }
}
