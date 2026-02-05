package gloomlib.command.processor.processors;

import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.message.CommandMessages;
import gloomlib.command.processor.PreProcessor;
import gloomlib.command.util.MessageUtils;
import net.kyori.adventure.text.Component;

/**
 * Permission Processor.
 *
 * <p>
 * Checks if the command sender has the required permissions.
 * Supports multiple permission checks (any match) and negated permissions.
 * </p>
 *
 * <h2>Permission Format</h2>
 * <ul>
 * <li>{@code plugin.command} — Standard permission node</li>
 * <li>{@code plugin.command.*} — Wildcard permission</li>
 * <li>{@code !plugin.command} — Negated permission (must NOT have this
 * permission)</li>
 * </ul>
 */
public class PermissionProcessor implements PreProcessor {

    /** Default no permission message */
    /**
     * Default no permission message
     */
    private Component noPermissionMessage = CommandMessages.NO_PERMISSION.get();

    /**
     * Checks permission.
     *
     * @param context     Command context
     * @param permissions Permission list (any match)
     * @return True if has permission
     */
    public boolean checkPermission(GloomCommandContext context, String... permissions) {
        if (permissions == null || permissions.length == 0) {
            return true;
        }

        var sender = context.getSender();

        // OP always has permission
        if (sender.isOp()) {
            return true;
        }

        for (String permission : permissions) {
            if (permission == null || permission.isEmpty()) {
                continue;
            }

            // Handle negated permission
            if (permission.startsWith("!")) {
                String negatedPerm = permission.substring(1);
                if (sender.hasPermission(negatedPerm)) {
                    return false; // Has negated permission, deny
                }
            } else {
                // Standard permission check
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
     * @return CONTINUE if allowed, HANDLED if denied
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
        // Default implementation does no checks, permission checks are handled by
        // Brigadier during registration
        // This processor is mainly for custom permission check scenarios
        return Result.CONTINUE;
    }

    @Override
    public int getPriority() {
        return 0; // Permission check should run first
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
