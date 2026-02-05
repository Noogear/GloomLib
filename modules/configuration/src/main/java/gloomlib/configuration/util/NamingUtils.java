package gloomlib.configuration.util;

import org.jetbrains.annotations.NotNull;

/**
 * Naming convention utilities.
 */
public final class NamingUtils {

    private NamingUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Converts camelCase to kebab-case.
     * <p>
     * Examples: {@code maxRetries → max-retries}, {@code serverPort → server-port}
     *
     * @param camelCase input string
     * @return kebab-case string
     */
    @NotNull
    public static String camelToKebab(@NotNull String camelCase) {
        return camelCase.replaceAll("([a-z])([A-Z]+)", "$1-$2").toLowerCase();
    }
}
