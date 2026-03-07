package gloomlib.configuration.core.util;

import com.google.common.reflect.TypeToken;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

/**
 * Utility for extracting generic type arguments, backed by Guava's {@link TypeToken}.
 */
public final class TypeInference {

    private TypeInference() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Extracts the raw class of the Nth generic type argument.
     *
     * <p>Examples:
     * <ul>
     *   <li>{@code Map<String, Integer>}, index 0 → {@code String.class}</li>
     *   <li>{@code Map<String, Integer>}, index 1 → {@code Integer.class}</li>
     *   <li>{@code List<? extends Number>}, index 0 → {@code Number.class}</li>
     * </ul>
     *
     * @param type  the generic type (e.g., from {@link java.lang.reflect.Field#getGenericType()})
     * @param index parameter index (0-based)
     * @return resolved raw class, or {@code Object.class} if resolution fails
     */
    @NotNull
    @SuppressWarnings("UnstableApiUsage")
    public static Class<?> extractGenericParameter(@Nullable Type type, int index) {
        if (!(type instanceof ParameterizedType pt)) {
            return Object.class;
        }

        Type[] args = pt.getActualTypeArguments();
        if (index < 0 || index >= args.length) {
            return Object.class;
        }

        Type arg = args[index];
        if (arg == null) {
            return Object.class;
        }
        return TypeToken.of(arg).getRawType();
    }
}
