package gloomlib.configuration.model;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Metadata holder for configuration fields.
 */
public record FieldMeta(Field field, String key, boolean hasCheck, boolean hasComment, boolean hasInline) {

    public <T extends Annotation> T getAnnotation(Class<T> type) {
        return field.getAnnotation(type);
    }

    public Object get(Object instance) throws IllegalAccessException {
        return field.get(instance);
    }

    public void set(Object instance, Object value) throws IllegalAccessException {
        field.set(instance, value);
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
