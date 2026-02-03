package gloomlib.configuration.util;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jetbrains.annotations.Nullable;

/**
 * Centralized logging utility for the configuration system.
 */
public final class ConfigurationLogger {

    private static ComponentLogger logger;

    private ConfigurationLogger() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Enables logging with the provided ComponentLogger.
     *
     * @param componentLogger the plugin's ComponentLogger
     */
    public static void setLogger(ComponentLogger componentLogger) {
        logger = componentLogger;
    }

    /**
     * Logs an error message with exception.
     *
     * @param message   the error message
     * @param throwable the exception (can be null)
     */
    public static void error(String message, @Nullable Throwable throwable) {
        if (logger != null) {
            logger.error(message, throwable);
        } else {
            System.err.println("[Config] [ERROR] " + message);
            if (throwable != null) {
                throwable.printStackTrace();
            }
        }
    }

    /**
     * Logs a warning message.
     *
     * @param message the warning message
     */
    public static void warn(String message) {
        if (logger != null) {
            logger.warn(message);
        } else {
            System.out.println("[Config] [WARN] " + message);
        }
    }

    /**
     * Logs an info message.
     *
     * @param message the info message
     */
    public static void info(String message) {
        if (logger != null) {
            logger.info(message);
        } else {
            System.out.println("[Config] [INFO] " + message);
        }
    }
}
