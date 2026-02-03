package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义子命令。
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>{@code
 * @SubCommand("create")
 * @Permission("rank.create")
 * public void createRank(CommandSender sender, @Arg String name) {
 *     // /rank create <name>
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface SubCommand {

    /**
     * 子命令名称。
     *
     * @return 子命令名称
     */
    String value();

    /**
     * 子命令别名列表。
     *
     * @return 别名数组
     */
    String[] aliases() default {};
}
