package gloomlib.command.message;

import gloomlib.command.util.MessageUtils;
import net.kyori.adventure.text.Component;

import java.text.MessageFormat;
import java.util.Arrays;

/**
 * Command framework messages with MiniMessage support.
 *
 * <p>
 * This enum provides centralized message management using modern MiniMessage formatting:
 * <ul>
 * <li><b>MiniMessage format</b>: All messages use MiniMessage tags for colors and formatting</li>
 * <li><b>Framework messages</b>: Custom messages with MiniMessage fallbacks</li>
 * <li><b>Vanilla messages</b>: Minecraft built-in translation keys (no fallback)</li>
 * <li><b>Component generation</b>: Direct MiniMessage deserialization</li>
 * </ul>
 * </p>
 *
 * <h2>MiniMessage Format Examples</h2>
 * <pre>
 * {@code
 * <red>Error!</red>
 * <yellow>Warning: {0}</yellow>
 * <gradient:green:blue>Gradient text</gradient>
 * <hover:show_text:'Tooltip'>Hover me</hover>
 * }
 * </pre>
 */
public enum CommandMessages {
    // ===== Framework Messages (MiniMessage format) =====
    // Help system
    HELP_NO_COMMANDS("command.help.no_commands", "<gray>No commands available.</gray>", false),
    HELP_HEADER("command.help.header", "<gold><bold>{0} HELP {1}</bold></gold>", false),
    HELP_SEPARATOR("command.help.separator", "<gold>{0}</gold>", false),
    HELP_USAGE_PREFIX("command.help.usage_prefix", "<dark_gray>  ▸ </dark_gray>", false),
    HELP_DESC_PREFIX("command.help.desc_prefix", "<gray>     ↳ {0}</gray>", false),
    HELP_NO_DESC("command.help.no_desc", "No description", false),
    HELP_HOVER_COMMAND("command.help.hover.command", "<gold>Command: </gold><white>{0}</white>", false),
    HELP_HOVER_DESCRIPTION("command.help.hover.description", "<gold>Description: </gold><gray>{0}</gray>", false),
    HELP_HOVER_CLICK("command.help.hover.click", "<aqua><italic>✦ Click to fill command</italic></aqua>", false),
    HELP_PAGE_INFO("command.help.page_info", "<yellow>Page {0}/{1}</yellow>", false),
    HELP_PREV_BUTTON("command.help.prev_button", "<green><bold>[← Prev]</bold></green>", false),
    HELP_NEXT_BUTTON("command.help.next_button", "<green><bold>[Next →]</bold></green>", false),
    HELP_PREV_DISABLED("command.help.prev_disabled", "<dark_gray>[← Prev]</dark_gray>", false),
    HELP_NEXT_DISABLED("command.help.next_disabled", "<dark_gray>[Next →]</dark_gray>", false),
    HELP_GOTO_PAGE("command.help.goto_page", "Go to page {0}", false),
    HELP_PAGE_SEPARATOR("command.help.page_separator", "<dark_gray>  |  </dark_gray>", false),

    // Validation messages
    VALIDATION_EMPTY("command.validation.empty", "<red>Argument {0} cannot be empty!</red>", false),
    VALIDATION_NULL("command.validation.null", "<red>Argument {0} cannot be null!</red>", false),
    VALIDATION_LENGTH_MIN("command.validation.length_min", "<red>Argument {0} min length: {1}</red>", false),
    VALIDATION_LENGTH_MAX("command.validation.length_max", "<red>Argument {0} max length: {1}</red>", false),
    VALIDATION_ENUM_INVALID("command.validation.enum_invalid", "<red>Invalid value '{0}'. Allowed: {1}</red>", false),
    VALIDATION_DURATION_EMPTY("command.validation.duration_empty", "<red>Duration cannot be empty!</red>", false),
    VALIDATION_DURATION_INVALID("command.validation.duration_invalid", "<red>Invalid duration: {0} (e.g. 1d2h30m)</red>", false),
    VALIDATION_DURATION_POSITIVE("command.validation.duration_positive", "<red>Duration must be greater than 0!</red>", false),

    // Cooldown
    COOLDOWN_WAIT("command.cooldown.wait", "<red>Please wait {0}!</red>", false),

    // ===== Vanilla Minecraft Messages (Built-in translation keys) =====
    // Argument errors
    ARG_GAMEMODE_INVALID("argument.gamemode.invalid", null, true),
    ARG_COLOR_INVALID("argument.color.invalid", null, true),
    ARG_MATERIAL_INVALID("argument.item.id.invalid", null, true),
    PLAYER_NOT_FOUND("argument.entity.notfound.player", null, true),
    SELECTOR_UNKNOWN("argument.entity.selector.unknown", null, true),
    PLAYER_UNKNOWN("argument.player.unknown", null, true),
    POS_MISSING("argument.pos.missing.double", null, true),

    // Number range errors
    INTEGER_TOO_LOW("argument.integer.low", null, true),
    INTEGER_TOO_HIGH("argument.integer.big", null, true),
    DOUBLE_TOO_LOW("argument.double.low", null, true),
    DOUBLE_TOO_HIGH("argument.double.big", null, true),
    FLOAT_TOO_LOW("argument.float.low", null, true),
    FLOAT_TOO_HIGH("argument.float.big", null, true),

    // Command errors
    COMMAND_FAILED("command.failed", null, true),
    COMMAND_UNKNOWN_ARG("command.unknown.argument", null, true),

    // Permission errors
    NO_PERMISSION("commands.help.failed", null, true),
    REQUIRES_PLAYER("permissions.requires.player", null, true);

    private final String key;
    private final String fallback;
    private final boolean vanilla;

    CommandMessages(String key, String fallback, boolean vanilla) {
        this.key = key;
        this.fallback = fallback;
        this.vanilla = vanilla;
    }

    /**
     * Gets all translation keys used by this framework.
     *
     * @return Array of all translation keys
     */
    public static String[] getAllKeys() {
        return Arrays.stream(values())
                .map(CommandMessages::key)
                .toArray(String[]::new);
    }

    /**
     * Gets all vanilla message keys.
     *
     * @return Array of vanilla Minecraft translation keys
     */
    public static String[] getVanillaKeys() {
        return Arrays.stream(values())
                .filter(CommandMessages::isVanilla)
                .map(CommandMessages::key)
                .toArray(String[]::new);
    }

    /**
     * Gets all custom framework message keys.
     *
     * @return Array of custom translation keys
     */
    public static String[] getCustomKeys() {
        return Arrays.stream(values())
                .filter(msg -> !msg.isVanilla())
                .map(CommandMessages::key)
                .toArray(String[]::new);
    }

    /**
     * Gets the translation key.
     *
     * @return Translation key used in resource packs
     */
    public String key() {
        return key;
    }

    /**
     * Gets the MiniMessage-formatted fallback text.
     *
     * @return Fallback text with MiniMessage tags, or null for vanilla messages
     */
    public String fallback() {
        return fallback;
    }

    /**
     * Checks if this is a vanilla Minecraft message.
     *
     * @return true if this uses Minecraft's built-in translation
     */
    public boolean isVanilla() {
        return vanilla;
    }

    /**
     * Checks if this message has a custom fallback.
     *
     * @return true if fallback is defined
     */
    public boolean hasFallback() {
        return fallback != null;
    }

    /**
     * Creates a Component with this message (no arguments).
     * Uses MiniMessage for formatting if fallback is available.
     *
     * @return Component with MiniMessage formatting applied
     */
    public Component get() {
        if (fallback != null) {
            // Custom message with MiniMessage formatting
            return MessageUtils.MINI_MESSAGE.deserialize(fallback);
        } else {
            // Vanilla translatable component
            return Component.translatable(key);
        }
    }

    /**
     * Creates a Component with this message (with arguments).
     * Arguments are substituted using MessageFormat pattern matching.
     *
     * @param args Argument values for {0}, {1}, etc placeholders
     * @return Component with arguments substituted
     */
    public Component get(Object... args) {
        if (fallback != null) {
            // Format the message with arguments
            String formatted = MessageFormat.format(fallback, args);
            return MessageUtils.MINI_MESSAGE.deserialize(formatted);
        } else {
            // Vanilla translatable with Component arguments
            Component[] componentArgs = new Component[args.length];
            for (int i = 0; i < args.length; i++) {
                componentArgs[i] = args[i] instanceof Component comp ? comp : Component.text(String.valueOf(args[i]));
            }
            return Component.translatable(key, componentArgs);
        }
    }
}
