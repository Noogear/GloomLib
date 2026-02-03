package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义命令开关参数（--switch）。
 *
 * <p>
 * 开关参数是布尔类型，只需指定名称即表示为 true，不存在则为 false。
 * </p>
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;SubCommand("delete")
 * public void delete(CommandSender sender,
 *                    @Arg String name,
 *                    &#64;Switch("confirm") boolean confirm) {
 *     if (!confirm) {
 *         sender.sendMessage("请添加 --confirm 以确认删除");
 *         return;
 *     }
 *     // 执行删除
 * }
 * }
 * </pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Switch {

    /**
     * 开关名称（格式：--name）。
     *
     * @return 开关名称
     */
    String value();

    /**
     * 短开关名称（格式：-n）。
     * 如果为空，则只能使用长格式。
     *
     * @return 短开关名称
     */
    String shorthand() default "";
}
