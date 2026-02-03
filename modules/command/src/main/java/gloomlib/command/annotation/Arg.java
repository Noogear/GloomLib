package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义命令参数。
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>{@code
 * public void execute(Player sender, @Arg("mode") GameMode mode) {
 *     // 参数名为 "mode"
 * }
 *
 * public void execute(Player sender, @Arg Player target) {
 *     // 参数名使用变量名 "target"
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Arg {

    /**
     * 参数名称。
     * 如果为空，将使用方法参数的变量名。
     *
     * @return 参数名称
     */
    String value() default "";
}
