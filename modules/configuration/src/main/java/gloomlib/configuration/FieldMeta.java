package gloomlib.configuration;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Metadata holder for configuration fields.
 */
record FieldMeta(Field field, String key, boolean hasCheck, boolean hasComment, boolean hasInline) {

    <T extends Annotation> T getAnnotation(Class<T> type) {
        return field.getAnnotation(type);
    }

    Object get(Object instance) throws IllegalAccessException {
        return field.get(instance);
    }

    void set(Object instance, Object value) throws IllegalAccessException {
        field.set(instance, value);
    }

    Class<?> getType() {
        return field.getType();
    }

    java.lang.reflect.Type getGenericType() {
        return field.getGenericType();
    }

    boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return field.isAnnotationPresent(annotationClass);
    }
}
