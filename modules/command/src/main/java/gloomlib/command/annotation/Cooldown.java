package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 定义命令冷却时间。
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;SubCommand("teleport")
 * @Cooldown(value = 30, unit = TimeUnit.SECONDS, bypassPermission = "server.tp.bypass")
 * public void teleport(Player player, @Arg Player target) {
 *     // 30秒冷却，拥有 server.tp.bypass 权限可绕过
 * }
 * }
 * </pre>
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Cooldown {

    /**
     * 冷却时长。
     *
     * @return 冷却时长
     */
    long value();

    /**
     * 时间单位。
     *
     * @return 时间单位，默认为秒
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * 绕过冷却所需的权限。
     * 如果为空，则无法绕过。
     *
     * @return 绕过权限节点
     */
    String bypassPermission() default "";

    /**
     * 冷却中时显示的消息。
     * 支持占位符：{remaining} - 剩余时间
     *
     * @return 冷却消息
     */
    String message() default "<red>请等待 <yellow>{remaining}</yellow> 后再使用此命令！</red>";
}
