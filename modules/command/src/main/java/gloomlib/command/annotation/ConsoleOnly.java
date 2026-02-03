package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限制命令仅限控制台使用。
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;SubCommand("maintenance")
 * @ConsoleOnly
 * public void maintenance(CommandSender sender) {
 *     // 仅控制台可执行的维护命令
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ConsoleOnly {

    /**
     * 玩家执行时显示的错误消息。
     * 支持 MiniMessage 格式。
     *
     * @return 错误消息
     */
    String message() default "<red><translate:commands.help.failed>";
}
