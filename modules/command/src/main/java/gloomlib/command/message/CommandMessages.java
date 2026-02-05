package gloomlib.command.message;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Arrays;

/**
 * Command framework messages with i18n support.
 *
 * <p>
 * This enum provides centralized message management with the following features:
 * <ul>
 * <li><b>Framework messages</b>: Custom messages defined by GloomLib with English fallbacks</li>
 * <li><b>Vanilla messages</b>: Minecraft built-in translation keys (no fallback needed)</li>
 * <li><b>Color theming</b>: Consistent color scheme across all messages</li>
 * <li><b>Component generation</b>: Easy conversion to Adventure Component API</li>
 * </ul>
 * </p>
 */
public enum CommandMessages {
    // ===== Framework Messages (Custom with fallback) =====
    HELP_NO_COMMANDS("command.help.no_commands", "No commands available.", NamedTextColor.GRAY, false),
    HELP_CLICK_HINT("command.help.click_hint", "Click to fill command", NamedTextColor.GRAY, false),
    HELP_PAGE("command.help.page", "Page {0}/{1}", NamedTextColor.YELLOW, false),
    HELP_PREV("command.help.prev", "[Prev]", NamedTextColor.GRAY, false),
    HELP_NEXT("command.help.next", "[Next]", NamedTextColor.GRAY, false),

    // Validation messages
    VALIDATION_EMPTY("command.validation.empty", "Argument {0} cannot be empty!", NamedTextColor.RED, false),
    VALIDATION_NULL("command.validation.null", "Argument {0} cannot be null!", NamedTextColor.RED, false),
    VALIDATION_LENGTH_MIN("command.validation.length_min", "Argument {0} min length: {1}", NamedTextColor.RED, false),
    VALIDATION_LENGTH_MAX("command.validation.length_max", "Argument {0} max length: {1}", NamedTextColor.RED, false),
    VALIDATION_ENUM_INVALID("command.validation.enum_invalid", "Invalid value '{0}'. Allowed: {1}", NamedTextColor.RED, false),
    VALIDATION_DURATION_EMPTY("command.validation.duration_empty", "Duration cannot be empty!", NamedTextColor.RED, false),
    VALIDATION_DURATION_INVALID("command.validation.duration_invalid", "Invalid duration: {0} (e.g. 1d2h30m)", NamedTextColor.RED, false),
    VALIDATION_DURATION_POSITIVE("command.validation.duration_positive", "Duration must be greater than 0!", NamedTextColor.RED, false),

    // Cooldown
    COOLDOWN_WAIT("command.cooldown.wait", "Please wait {0}!", NamedTextColor.RED, false),

    // ===== Vanilla Minecraft Messages (Built-in translation keys) =====
    // Argument errors
    ARG_GAMEMODE_INVALID("argument.gamemode.invalid", null, NamedTextColor.RED, true),
    ARG_COLOR_INVALID("argument.color.invalid", null, NamedTextColor.RED, true),
    ARG_MATERIAL_INVALID("argument.item.id.invalid", null, NamedTextColor.RED, true),
    PLAYER_NOT_FOUND("argument.entity.notfound.player", null, NamedTextColor.RED, true),
    SELECTOR_UNKNOWN("argument.entity.selector.unknown", null, NamedTextColor.RED, true),
    PLAYER_UNKNOWN("argument.player.unknown", null, NamedTextColor.RED, true),
    POS_MISSING("argument.pos.missing.double", null, NamedTextColor.RED, true),

    // Number range errors
    INTEGER_TOO_LOW("argument.integer.low", null, NamedTextColor.RED, true),
    INTEGER_TOO_HIGH("argument.integer.big", null, NamedTextColor.RED, true),
    DOUBLE_TOO_LOW("argument.double.low", null, NamedTextColor.RED, true),
    DOUBLE_TOO_HIGH("argument.double.big", null, NamedTextColor.RED, true),
    FLOAT_TOO_LOW("argument.float.low", null, NamedTextColor.RED, true),
    FLOAT_TOO_HIGH("argument.float.big", null, NamedTextColor.RED, true),

    // Command errors
    COMMAND_FAILED("command.failed", null, NamedTextColor.RED, true),
    COMMAND_UNKNOWN_ARG("command.unknown.argument", null, NamedTextColor.RED, true),

    // Permission errors
    NO_PERMISSION("commands.help.failed", null, NamedTextColor.RED, true),
    REQUIRES_PLAYER("permissions.requires.player", null, NamedTextColor.RED, true);

    private final String key;
    private final String fallback;
    private final NamedTextColor color;
    private final boolean vanilla;

    /**
     * Cached Component for parameterless messages (lazy initialization).
     * Using transient to avoid serialization issues.
     */
    private transient volatile Component cachedComponent;

    CommandMessages(String key, String fallback, NamedTextColor color, boolean vanilla) {
        this.key = key;
        this.fallback = fallback;
        this.color = color;
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
     * Clears all cached Components.
     * Should be called when:
     * <ul>
     * <li>Framework is reloaded</li>
     * <li>Language pack is changed</li>
     * <li>Resource pack is updated</li>
     * </ul>
     *
     * <p>
     * This forces all messages to be recreated on next access,
     * ensuring they reflect the latest translations.
     * </p>
     */
    public static void clearAllCaches() {
        for (CommandMessages message : values()) {
            message.clearCache();
        }
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
     * Gets the English fallback text.
     *
     * @return Fallback text, or null for vanilla messages
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
     * Uses object pool for better performance (caches the result).
     *
     * @return Translatable component with color applied
     */
    public Component get() {
        // Double-checked locking for lazy initialization of cached component
        Component cached = cachedComponent;
        if (cached == null) {
            synchronized (this) {
                cached = cachedComponent;
                if (cached == null) {
                    cachedComponent = cached = applyColor(createComponent());
                }
            }
        }
        return cached;
    }

    /**
     * Clears the cached Component for this message.
     * Call this when the language pack is changed or framework is reloaded.
     */
    public void clearCache() {
        cachedComponent = null;
    }

    /**
     * Creates a Component with this message (with arguments).
     * Cannot be cached due to dynamic arguments.
     *
     * @param args Placeholder arguments (use Component.text() for values)
     * @return Translatable component with color applied
     */
    public Component get(Component... args) {
        // Cannot cache when arguments are provided
        return applyColor(createComponent(args));
    }

    /**
     * Creates base translatable component without color.
     */
    private Component createComponent() {
        return fallback != null
                ? Component.translatable(key).fallback(fallback)
                : Component.translatable(key);
    }

    /**
     * Creates base translatable component with arguments.
     */
    private Component createComponent(Component... args) {
        return fallback != null
                ? Component.translatable(key, args).fallback(fallback)
                : Component.translatable(key, args);
    }

    /**
     * Applies color to component if defined.
     */
    private Component applyColor(Component component) {
        return color != null ? component.color(color) : component;
    }
}
