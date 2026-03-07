package gloomlib.configuration.model;

import gloomlib.configuration.util.ReflectionUtils;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Field metadata with hybrid optimization.
 * <p>
 * Primitives use VarHandle, objects use MethodHandle, annotations are cached.
 */
public final class FieldMeta {

    private final Field field;
    private final String key;
    private final boolean hasCheck;
    private final boolean hasComment;
    private final boolean hasInline;

    private final VarHandle varHandle;
    private final MethodHandle getter;
    private final MethodHandle setter;
    private final boolean isPrimitive;

    private final Map<Class<? extends Annotation>, Annotation> annotationCache = new ConcurrentHashMap<>();

    public FieldMeta(Field field, String key, boolean hasCheck, boolean hasComment, boolean hasInline) {
        this.field = field;
        this.key = key;
        this.hasCheck = hasCheck;
        this.hasComment = hasComment;
        this.hasInline = hasInline;
        this.isPrimitive = field.getType().isPrimitive();

        try {
            field.setAccessible(true);
            MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(
                    field.getDeclaringClass(),
                    MethodHandles.lookup()
            );

            if (isPrimitive) {
                this.varHandle = lookup.unreflectVarHandle(field);
                this.getter = null;
                this.setter = null;
            } else {
                this.varHandle = null;
                this.getter = lookup.unreflectGetter(field);
                this.setter = lookup.unreflectSetter(field);
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Failed to create handles for field: " + field.getName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends Annotation> T getAnnotation(Class<T> type) {
        return (T) annotationCache.computeIfAbsent(type, field::getAnnotation);
    }

    public Object get(Object instance) throws IllegalAccessException {
        try {
            return isPrimitive ? varHandle.get(instance) : getter.invoke(instance);
        } catch (Throwable e) {
            throw ReflectionUtils.wrapReflectionException(e, "Failed to get field value");
        }
    }

    public void set(Object instance, Object value) throws IllegalAccessException {
        try {
            if (isPrimitive) {
                varHandle.set(instance, value);
            } else {
                setter.invoke(instance, value);
            }
        } catch (Throwable e) {
            throw ReflectionUtils.wrapReflectionException(e, "Failed to set field value");
        }
    }

    public String key() {
        return key;
    }

    public boolean hasCheck() {
        return hasCheck;
    }

    public boolean hasComment() {
        return hasComment;
    }

    public boolean hasInline() {
        return hasInline;
    }

    public Field field() {
        return field;
    }

    public Class<?> getType() {
        return field.getType();
    }

    public java.lang.reflect.Type getGenericType() {
        return field.getGenericType();
    }

    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return field.isAnnotationPresent(annotationClass);
    }
}
