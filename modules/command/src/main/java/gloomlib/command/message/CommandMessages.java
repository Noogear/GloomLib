package gloomlib.command.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;

/**
 * Command framework messages with i18n support.
 */
public enum CommandMessages {
    // Framework
    HELP_NO_COMMANDS("command.help.no_commands", "No commands available.", NamedTextColor.GRAY),
    HELP_CLICK_HINT("command.help.click_hint", "Click to fill command", NamedTextColor.GRAY),
    HELP_PAGE("command.help.page", "Page {0}/{1}", NamedTextColor.YELLOW),
    HELP_PREV("command.help.prev", "[Prev]", null),
    HELP_NEXT("command.help.next", "[Next]", null),
    VALIDATION_EMPTY("command.validation.empty", "Argument {0} cannot be empty!", NamedTextColor.RED),
    VALIDATION_NULL("command.validation.null", "Argument {0} cannot be null!", NamedTextColor.RED),
    VALIDATION_LENGTH_MIN("command.validation.length_min", "Argument {0} min length: {1}", NamedTextColor.RED),
    VALIDATION_LENGTH_MAX("command.validation.length_max", "Argument {0} max length: {1}", NamedTextColor.RED),
    VALIDATION_ENUM_INVALID("command.validation.enum_invalid", "Invalid value '{0}'. Allowed: {1}", NamedTextColor.RED),
    VALIDATION_DURATION_EMPTY("command.validation.duration_empty", "Duration cannot be empty!", NamedTextColor.RED),
    VALIDATION_DURATION_INVALID("command.validation.duration_invalid", "Invalid duration: {0} (e.g. 1d2h30m)", NamedTextColor.RED),
    VALIDATION_DURATION_POSITIVE("command.validation.duration_positive", "Duration must be greater than 0!", NamedTextColor.RED),
    ARG_GAMEMODE_INVALID("argument.gamemode.invalid", null, NamedTextColor.RED),
    ARG_COLOR_INVALID("argument.color.invalid", null, NamedTextColor.RED),
    COOLDOWN_WAIT("command.cooldown.wait", "Please wait {0}!", NamedTextColor.RED),

    // Vanilla (No fallback)
    PLAYER_NOT_FOUND("argument.entity.notfound.player", null, NamedTextColor.RED),
    SELECTOR_UNKNOWN("argument.entity.selector.unknown", null, NamedTextColor.RED),
    PLAYER_UNKNOWN("argument.player.unknown", null, NamedTextColor.RED),
    POS_MISSING("argument.pos.missing.double", null, NamedTextColor.RED),
    COMMAND_FAILED("command.failed", null, NamedTextColor.RED),
    COMMAND_UNKNOWN_ARG("command.unknown.argument", null, NamedTextColor.RED),
    NO_PERMISSION("commands.help.failed", null, NamedTextColor.RED),
    INTEGER_TOO_LOW("argument.integer.low", null, NamedTextColor.RED),
    INTEGER_TOO_HIGH("argument.integer.big", null, NamedTextColor.RED),
    DOUBLE_TOO_LOW("argument.double.low", null, NamedTextColor.RED),
    DOUBLE_TOO_HIGH("argument.double.big", null, NamedTextColor.RED),
    FLOAT_TOO_LOW("argument.float.low", null, NamedTextColor.RED),
    FLOAT_TOO_HIGH("argument.float.big", null, NamedTextColor.RED),
    REQUIRES_PLAYER("permissions.requires.player", null, NamedTextColor.RED),
    ARG_MATERIAL_INVALID("argument.item.id.invalid", null, NamedTextColor.RED);

    private final String key;
    private final String fallback;
    private final NamedTextColor color;

    CommandMessages(String key, String fallback, NamedTextColor color) {
        this.key = key;
        this.fallback = fallback;
        this.color = color;
    }

    public String key() { return key; }
    public String fallback() { return fallback; }
    public boolean isVanilla() { return fallback == null; }

    public Component get() {
        var c = fallback != null ? Component.translatable(key).fallback(fallback) : Component.translatable(key);
        return color != null ? c.color(color) : c;
    }

    public Component get(Component... args) {
        var c = fallback != null ? Component.translatable(key, args).fallback(fallback) : Component.translatable(key, args);
        return color != null ? c.color(color) : c;
    }

    public static String[] getAllKeys() {
        return Arrays.stream(values()).map(CommandMessages::key).toArray(String[]::new);
    }
}
