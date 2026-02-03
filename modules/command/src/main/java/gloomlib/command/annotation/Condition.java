package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义命令执行条件。
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>{@code
 * @SubCommand("reward")
 * @Condition("dailyReward")
 * public void claimReward(Player player) {
 *     // 仅当 dailyReward 条件满足时执行
 * }
 * }</pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Condition {

    /**
     * 条件名称（在 CommandConditionRegistry 中注册）。
     *
     * @return 条件名称
     */
    String value();
}
