package gloomlib.command.processor.processors;

import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.processor.PreProcessor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * Logging Processor.
 *
 * <p>
 * Logs command execution details.
 * </p>
 */
public class LoggingProcessor implements PreProcessor {

    private final Logger logger;

    /**
     * Creates a logging processor.
     *
     * @param plugin Plugin instance
     */
    public LoggingProcessor(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
    }

    /**
     * Creates a logging processor (using custom Logger).
     *
     * @param logger Logger instance
     */
    public LoggingProcessor(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Result preProcess(GloomCommandContext context) {
        logger.info(String.format(
                "[Command] %s executed command",
                context.getSender().getName()));
        return Result.CONTINUE;
    }

    @Override
    public int getPriority() {
        return -1000; // Highest priority, executes first
    }
}
