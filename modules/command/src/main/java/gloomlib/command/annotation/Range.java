package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义数值参数的范围约束。
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
 *                  @Arg @Range(min = 1, max = 64) int amount) {
 *     // amount 必须在 1 到 64 之间
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Range {

    /**
     * 最小值（包含）。
     *
     * @return 最小值
     */
    double min() default Double.MIN_VALUE;

    /**
     * 最大值（包含）。
     *
     * @return 最大值
     */
    double max() default Double.MAX_VALUE;
}
