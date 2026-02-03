package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 限制命令仅限玩家使用。
 *
 * <p>
 * 如果控制台或其他非玩家实体尝试执行，将显示错误消息。
 * </p>
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;Usage
 * @PlayerOnly(message = "<red>此命令仅限玩家使用！</red>")
 * public void execute(Player player) {
 *     // 第一个参数类型为 Player，表示仅玩家可执行
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface PlayerOnly {

    /**
     * 非玩家执行时显示的错误消息。
     * 支持 MiniMessage 格式。
     *
     * @return 错误消息
     */
    String message() default "<red><translate:permissions.requires.player>";
}
