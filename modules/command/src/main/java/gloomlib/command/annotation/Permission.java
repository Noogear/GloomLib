package gloomlib.command.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 定义命令或子命令所需的权限。
 *
 * <p>
 * 用法示例：
 * </p>
 * 
 * <pre>
 * {@code
 * &#64;Command("gamemode")
 * &#64;Permission("server.gamemode")
 * public class GameModeCommand {
 *     // 所有子命令继承此权限检查
 * }
 *
 * &#64;SubCommand("set")
 * @Permission(value = "server.gamemode.set", mode = PermissionMode.REQUIRE)
 * public void setMode(Player player, @Arg GameMode mode) {
 *     // 需要额外权限
 * }
 * }
 * </pre>
 */
@Target({ ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface Permission {

    /**
     * 权限节点名称。
     *
     * @return 权限节点
     */
    String value();

    /**
     * 权限检查模式。
     *
     * @return 检查模式
     */
    PermissionMode mode() default PermissionMode.REQUIRE;

    /**
     * 权限检查模式枚举。
     */
    enum PermissionMode {
        /** 必须拥有指定权限 */
        REQUIRE,
        /** 必须是 OP */
        OP,
        /** 任何人都可以使用 */
        ANY
    }
}
