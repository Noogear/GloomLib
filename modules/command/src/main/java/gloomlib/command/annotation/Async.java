package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记命令方法在异步线程中执行。
 *
 * <p>
 * 适用于需要执行数据库查询、网络请求等 IO 操作的命令。
 * </p>
 *
 * <p>
 * <b>注意：</b>
 * </p>
 * <ul>
 * <li>异步方法中不能直接调用大部分 Bukkit API（非线程安全）</li>
 * <li>消息发送会自动切回主线程</li>
 * <li>使用 Paper AsyncScheduler 执行</li>
 * </ul>
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;SubCommand("stats")
 * @Async
 * public void showStats(Player player) {
 *     // 在异步线程执行数据库查询
 *     Map<String, Object> stats = database.queryPlayerStats(player);
 *
 *     // 发送消息会自动切回主线程
 *     player.sendMessage(Component.text("查询完成！"));
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Async {
    // 标记注解，无需属性
}
