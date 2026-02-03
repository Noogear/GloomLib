package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义可选参数的默认值。
 *
 * <p>
 * 支持的特殊值：
 * </p>
 * <ul>
 * <li>{@code "self"} - 当前执行者（仅限玩家参数）</li>
 * <li>{@code "console"} - 控制台</li>
 * <li>字面量值 - 如 {@code "0"}, {@code "true"}</li>
 * <li>配置表达式 - 如 {@code "${config.default.value}"}</li>
 * </ul>
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>{@code
 * public void execute(Player sender,
 *         @Arg @Optional @Default("self") Player target) {
 *     // 如果未指定 target，默认为执行者自己
 * }
 * }</pre>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface Default {

    /**
     * 默认值表达式。
     *
     * @return 默认值
     */
    String value();
}
