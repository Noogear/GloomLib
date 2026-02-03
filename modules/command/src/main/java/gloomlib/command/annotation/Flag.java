package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义命令标志参数（--flag value）。
 *
 * <p>
 * 标志参数可以出现在命令参数的任意位置，格式为 {@code --name value} 或 {@code -n value}。
 * </p>
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;SubCommand("give")
 * public void give(CommandSender sender,
 *                  &#64;Arg Player target,
 *                  &#64;Arg int amount,
 *                  @Flag(value = "reason", shorthand = "r") String reason) {
 *     // /give <target> <amount> --reason "Some reason"
 *     // /give <target> <amount> -r "Some reason"
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Flag {

    /**
     * 标志名称（长格式：--name）。
     *
     * @return 标志名称
     */
    String value();

    /**
     * 短标志名称（短格式：-n）。
     * 如果为空，则只能使用长格式。
     *
     * @return 短标志名称
     */
    String shorthand() default "";
}
