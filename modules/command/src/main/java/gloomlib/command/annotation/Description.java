package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 命令描述信息，用于帮助命令显示。
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>{@code
 * @Command("gamemode")
 * @Description("更改游戏模式")
 * public class GameModeCommand {
 *     // ...
 * }
 * }</pre>
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Description {

    /**
     * 命令描述文本。
     *
     * @return 描述文本
     */
    String value();
}
