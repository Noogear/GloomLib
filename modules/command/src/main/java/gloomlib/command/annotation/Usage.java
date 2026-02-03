package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记方法为命令的主要执行入口（无子命令）。
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>{@code
 * @Usage
 * public void execute(Player player, @Arg GameMode mode) {
 *     // /gamemode <mode>
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Usage {
    // 标记注解，无需属性
}
