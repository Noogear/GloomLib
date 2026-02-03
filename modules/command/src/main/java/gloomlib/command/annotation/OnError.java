package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义方法级异常处理器。
 *
 * <p>
 * 当命令执行过程中抛出指定类型的异常时，将调用此方法进行处理。
 * </p>
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>
 * {
 *     &#64;code
 *     &#64;Command("rank")
 *     public class RankCommand {
 *
 *         &#64;OnError(RankNotFoundException.class)
 *         public void handleRankNotFound(CommandContext context, RankNotFoundException e) {
 *             context.getSender().sendMessage(Component.text("等级 " + e.getRankName() + " 不存在！"));
 *         }
 *
 *         @OnError({ IllegalArgumentException.class, NumberFormatException.class })
 *         public void handleInvalidArgument(CommandContext context, Exception e) {
 *             context.getSender().sendMessage(Component.text("参数无效：" + e.getMessage()));
 *         }
 *     }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OnError {

    /**
     * 要处理的异常类型。
     *
     * @return 异常类型数组
     */
    Class<? extends Throwable>[] value();
}
