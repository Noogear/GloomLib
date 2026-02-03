package gloomlib.command.processor.processors;

import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.processor.PreProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;

/**
 * 权限处理器。
 *
 * <p>
 * 检查命令执行者是否具有所需权限。
 * 支持多权限检查（任意一个匹配即可）和否定权限。
 * </p>
 *
 * <h2>权限格式</h2>
 * <ul>
 * <li>{@code plugin.command} — 标准权限节点</li>
 * <li>{@code plugin.command.*} — 通配符权限</li>
 * <li>{@code !plugin.command} — 否定权限（必须没有此权限）</li>
 * </ul>
 */
public class PermissionProcessor implements PreProcessor {

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /** 默认无权限消息 */
    private Component noPermissionMessage = Component.text("你没有权限执行此命令！", NamedTextColor.RED);

    /**
     * 设置无权限消息。
     *
     * @param message Adventure 组件消息
     */
    public void setNoPermissionMessage(Component message) {
        this.noPermissionMessage = message;
    }

    /**
     * 设置无权限消息（MiniMessage 格式）。
     *
     * @param miniMessageFormat MiniMessage 格式字符串
     */
    public void setNoPermissionMessage(String miniMessageFormat) {
        this.noPermissionMessage = miniMessage.deserialize(miniMessageFormat);
    }

    /**
     * 检查权限。
     *
     * @param context     命令上下文
     * @param permissions 权限列表（任意一个匹配即可）
     * @return 是否具有权限
     */
    public boolean checkPermission(GloomCommandContext context, String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return true;
        }

        var sender = context.getSender();

        // OP 始终有权限
        if (sender.isOp()) {
            return true;
        }

        for (String permission : permissions) {
            if (permission == null || permission.isEmpty()) {
                continue;
            }

            // 处理否定权限
            if (permission.startsWith("!")) {
                String negatedPerm = permission.substring(1);
                if (sender.hasPermission(negatedPerm)) {
                    return false; // 有否定权限，拒绝
                }
            } else {
                // 标准权限检查
                if (sender.hasPermission(permission)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * 检查权限，如果没有权限则发送消息。
     *
     * @param context     命令上下文
     * @param permissions 权限列表
     * @return 如果有权限返回 CONTINUE，否则返回 ABORT
     */
    public Result checkAndNotify(GloomCommandContext context, String... permissions) {
        if (checkPermission(context, permissions)) {
            return Result.CONTINUE;
        }

        context.sendMessage(noPermissionMessage);
        return Result.HANDLED;
    }

    @Override
    public Result preProcess(GloomCommandContext context) {
        // 默认实现不做任何检查，权限检查在命令注册时通过 Brigadier 处理
        // 这个处理器主要用于自定义权限检查场景
        return Result.CONTINUE;
    }

    @Override
    public int getPriority() {
        return 0; // 权限检查应该最先执行
    }

    /**
     * 获取无权限消息。
     *
     * @return 无权限消息组件
     */
    public Component getNoPermissionMessage() {
        return noPermissionMessage;
    }
}
