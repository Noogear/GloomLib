package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记一个类为命令类，或在方法上定义独立的根命令别名。
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>{@code
 * @Command("gamemode")
 * @Permission("server.gamemode")
 * public class GameModeCommand {
 *     // ...
 * }
 * }</pre>
 *
 * <p>
 * 也可以在方法上使用，创建独立的根命令别名：
 * </p>
 * 
 * <pre>{@code
 * @Command("gmc")
 * public void creative(Player player) {
 *     // 这将创建 /gmc 作为独立命令
 * }
 * }</pre>
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Command {

    /**
     * 命令名称。
     *
     * @return 命令名称（不含 / 前缀）
     */
    String value();

    /**
     * 命令别名列表。
     *
     * @return 别名数组
     */
    String[] aliases() default {};
}
