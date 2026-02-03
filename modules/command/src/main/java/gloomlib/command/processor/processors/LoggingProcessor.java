package gloomlib.command.processor.processors;

import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.processor.PreProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Logger;

/**
 * 日志记录处理器。
 *
 * <p>
 * 记录命令执行日志。
 * </p>
 */
public class LoggingProcessor implements PreProcessor {

    private final Logger logger;

    /**
     * 创建日志处理器。
     *
     * @param plugin 插件实例
     */
    public LoggingProcessor(JavaPlugin plugin) {
        this.logger = plugin.getLogger();
    }

    /**
     * 创建日志处理器（使用自定义 Logger）。
     *
     * @param logger Logger 实例
     */
    public LoggingProcessor(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Result preProcess(GloomCommandContext context) {
        logger.info(String.format(
                "[命令] %s 执行了命令",
                context.getSender().getName()));
        return Result.CONTINUE;
    }

    @Override
    public int getPriority() {
        return -1000; // 最高优先级，最先执行
    }
}
