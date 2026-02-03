package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记参数为可选参数。
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>{@code
 * public void execute(Player sender, @Arg @Optional Player target) {
 *     // target 参数是可选的
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Optional {
    // 标记注解，无需属性
}
