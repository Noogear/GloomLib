package gloomlib.command.core.processor;

import gloomlib.command.api.context.GloomCommandContext;
import gloomlib.command.core.message.CommandMessages;
import gloomlib.command.core.util.MessageUtils;
import net.kyori.adventure.text.Component;

/**
 * Permission verification processor.
 *
 * <p>Permission formats: {@code plugin.cmd}, {@code plugin.cmd.*}, {@code !plugin.cmd} (negated)
 */
public class PermissionProcessor {

    private Component noPermissionMessage = CommandMessages.NO_PERMISSION.get();

    public boolean checkPermission(GloomCommandContext context, String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return true;
        }

        var sender = context.getSender();

        if (sender.isOp()) {
            return true;
        }

        for (String permission : permissions) {
            if (permission == null || permission.isEmpty()) {
                continue;
            }

            if (permission.startsWith("!")) {
                String negatedPerm = permission.substring(1);
                if (sender.hasPermission(negatedPerm)) {
                    return false;
                }
            } else {
                if (sender.hasPermission(permission)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Checks permission and notifies if denied.
     *
     * @param context     Command context
     * @param permissions Permission list
     * @return true if allowed, false if denied (sends no-permission message)
     */
    public boolean checkAndNotify(GloomCommandContext context, String... permissions) {
        if (checkPermission(context, permissions)) {
            return true;
        }
        context.sendMessage(noPermissionMessage);
        return false;
    }

    /**
     * Gets the no permission message.
     *
     * @return No permission message component
     */
    public Component getNoPermissionMessage() {
        return noPermissionMessage;
    }

    /**
     * Sets the no permission message.
     *
     * @param message Adventure Component message
     */
    public void setNoPermissionMessage(Component message) {
        this.noPermissionMessage = message;
    }

    /**
     * Sets the no permission message (MiniMessage format).
     *
     * @param miniMessageFormat MiniMessage format string
     */
    public void setNoPermissionMessage(String miniMessageFormat) {
        this.noPermissionMessage = MessageUtils.MINI_MESSAGE.deserialize(miniMessageFormat);
    }
}
