package gloomlib.configuration.exception;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Enhanced exception for configuration serialization/deserialization errors.
 * Provides detailed context including node path, expected type, actual value, and cause.
 */
public class SerializationException extends Exception {

    private final List<String> nodePath;
    private final Class<?> expectedType;
    private final Object actualValue;
    private final String context;

    /**
     * Creates a SerializationException with detailed context.
     *
     * @param message      the error message
     * @param nodePath     the path to the node that failed (e.g., ["classes", "warrior", "health"])
     * @param expectedType the expected Java type
     * @param actualValue  the actual value from YAML
     * @param cause        the underlying exception
     */
    public SerializationException(
            @NotNull String message,
            @Nullable List<String> nodePath,
            @Nullable Class<?> expectedType,
            @Nullable Object actualValue,
            @Nullable Throwable cause) {
        super(buildMessage(message, nodePath, expectedType, actualValue), cause);
        this.nodePath = nodePath != null ? Collections.unmodifiableList(new ArrayList<>(nodePath)) : List.of();
        this.expectedType = expectedType;
        this.actualValue = actualValue;
        this.context = buildContext(nodePath, expectedType, actualValue);
    }

    /**
     * Creates a SerializationException with message and cause.
     *
     * @param message the error message
     * @param cause   the underlying exception
     */
    public SerializationException(@NotNull String message, @Nullable Throwable cause) {
        this(message, null, null, null, cause);
    }

    /**
     * Creates a SerializationException with message only.
     *
     * @param message the error message
     */
    public SerializationException(@NotNull String message) {
        this(message, null, null, null, null);
    }

    private static String buildMessage(String message, List<String> nodePath, Class<?> expectedType, Object actualValue) {
        StringBuilder sb = new StringBuilder();
        sb.append("[Config Error] ").append(message);

        if (nodePath != null && !nodePath.isEmpty()) {
            sb.append("\n  At path: '").append(String.join(".", nodePath)).append("'");
        }

        if (expectedType != null) {
            sb.append("\n  Expected type: ").append(expectedType.getSimpleName());
        }

        if (actualValue != null) {
            sb.append("\n  Actual value: ").append(formatValue(actualValue));
        }

        return sb.toString();
    }

    private static String buildContext(List<String> nodePath, Class<?> expectedType, Object actualValue) {
        StringBuilder sb = new StringBuilder();

        if (nodePath != null && !nodePath.isEmpty()) {
            sb.append("at path '").append(String.join(".", nodePath)).append("'");
        }

        if (expectedType != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("expected ").append(expectedType.getSimpleName());
        }

        if (actualValue != null) {
            if (sb.length() > 0) sb.append(", ");
            sb.append("got ").append(formatValue(actualValue));
        }

        return sb.toString();
    }

    private static String formatValue(Object value) {
        if (value == null) {
            return "null";
        }

        String str = value.toString();
        if (str.length() > 100) {
            return "'" + str.substring(0, 97) + "...'";
        }

        return "'" + str + "' (" + value.getClass().getSimpleName() + ")";
    }

    /**
     * Creates a new builder for SerializationException.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Gets the node path where the error occurred.
     *
     * @return the node path (e.g., ["classes", "warrior", "health"])
     */
    @NotNull
    public List<String> getNodePath() {
        return nodePath;
    }

    /**
     * Gets the expected Java type.
     *
     * @return the expected type, or null if not applicable
     */
    @Nullable
    public Class<?> getExpectedType() {
        return expectedType;
    }

    /**
     * Gets the actual value from YAML.
     *
     * @return the actual value, or null if not applicable
     */
    @Nullable
    public Object getActualValue() {
        return actualValue;
    }

    /**
     * Gets the formatted context string.
     *
     * @return the context string (e.g., "at path 'classes.warrior.health', expected int, got 'invalid'")
     */
    @NotNull
    public String getContext() {
        return context;
    }

    /**
     * Gets the node path as a dot-separated string.
     *
     * @return the path string (e.g., "classes.warrior.health")
     */
    @NotNull
    public String getPathString() {
        return nodePath.isEmpty() ? "<root>" : String.join(".", nodePath);
    }

    /**
     * Builder for creating SerializationException with fluent API.
     */
    public static class Builder {
        private String message;
        private List<String> nodePath;
        private Class<?> expectedType;
        private Object actualValue;
        private Throwable cause;

        public Builder message(@NotNull String message) {
            this.message = message;
            return this;
        }

        public Builder path(@NotNull List<String> nodePath) {
            this.nodePath = nodePath;
            return this;
        }

        public Builder path(@NotNull String... path) {
            this.nodePath = List.of(path);
            return this;
        }

        public Builder expectedType(@NotNull Class<?> type) {
            this.expectedType = type;
            return this;
        }

        public Builder actualValue(@Nullable Object value) {
            this.actualValue = value;
            return this;
        }

        public Builder cause(@Nullable Throwable cause) {
            this.cause = cause;
            return this;
        }

        public SerializationException build() {
            if (message == null) {
                throw new IllegalStateException("Message is required");
            }
            return new SerializationException(message, nodePath, expectedType, actualValue, cause);
        }
    }
}
