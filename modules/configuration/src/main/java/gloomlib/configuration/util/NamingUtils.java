package gloomlib.configuration.util;

import com.google.common.base.CaseFormat;
import org.jetbrains.annotations.NotNull;

/**
 * Naming convention utilities.
 */
public final class NamingUtils {

    private NamingUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts camelCase to kebab-case using Guava's CaseFormat.
     * <p>
     * Examples: {@code maxRetries → max-retries}, {@code serverPort → server-port}
     *
     * @param camelCase input string
     * @return kebab-case string
     */
    @NotNull
    public static String camelToKebab(@NotNull String camelCase) {
        return CaseFormat.LOWER_CAMEL.to(CaseFormat.LOWER_HYPHEN, camelCase);
    }
}
