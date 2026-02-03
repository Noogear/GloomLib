package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记参数为贪婪参数，消耗所有剩余输入。
 *
 * <p>
 * 贪婪参数必须是方法的最后一个参数。
 * </p>
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>{@code
 * @SubCommand("broadcast")
 * public void broadcast(CommandSender sender, @Arg @Greedy String message) {
 *     // /broadcast Hello world, this is a long message
 *     // message = "Hello world, this is a long message"
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Greedy {
    // 标记注解，无需属性
}
