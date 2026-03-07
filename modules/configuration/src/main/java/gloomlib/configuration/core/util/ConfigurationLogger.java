package gloomlib.configuration.core.util;

import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Centralized logging utility for the configuration system.
 * Uses ComponentLogger when available, falls back to SLF4J.
 */
public final class ConfigurationLogger {

    private static final Logger SLF4J_LOGGER = LoggerFactory.getLogger("GloomLib-Config");
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
            SLF4J_LOGGER.error(message, throwable);
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
            SLF4J_LOGGER.warn(message);
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
            SLF4J_LOGGER.info(message);
        }
    }
}
