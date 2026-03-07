package gloomlib.command.processor.processors;

import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.processor.PreProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Command execution logging processor.
 */
public class LoggingProcessor implements PreProcessor {

    private final ComponentLogger logger;

    public LoggingProcessor(JavaPlugin plugin) {
        this.logger = plugin.getComponentLogger();
    }

    public LoggingProcessor(ComponentLogger logger) {
        this.logger = logger;
    }

    @Override
    public Result preProcess(GloomCommandContext context) {
        Component logMessage = Component.text()
                .append(Component.text("[Command] ", NamedTextColor.GRAY))
                .append(Component.text(context.getSender().getName(), NamedTextColor.YELLOW))
                .append(Component.text(" executed command", NamedTextColor.GRAY))
                .build();
        
        logger.info(logMessage);
        return Result.CONTINUE;
    }

    @Override
    public int getPriority() {
        return -1000; // Highest priority, executes first
    }
}
