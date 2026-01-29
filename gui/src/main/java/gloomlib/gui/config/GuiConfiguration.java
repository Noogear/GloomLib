package gloomlib.gui.config;

/**
 * Configuration for GUI behavior and updates.
 *
 * @param updateStrategy the strategy for updating slots
 * @param tickRate the tick rate for periodic updates
 * @param enableAnimations whether animations are enabled
 */
public record GuiConfiguration(
        UpdateStrategy updateStrategy,
        int tickRate,
        boolean enableAnimations
) {
    public static final GuiConfiguration DEFAULT = new GuiConfiguration(UpdateStrategy.REACTIVE, -1, false);

    public static final GuiConfiguration REACTIVE = DEFAULT;

    public static final GuiConfiguration ANIMATED = new GuiConfiguration(UpdateStrategy.PERIODIC, 1, true);

    /**
     * GUI update strategies.
     */
    public enum UpdateStrategy {
        /**
         * Update only when data changes.
         */
        REACTIVE,

        /**
         * Update periodically.
         */
        PERIODIC
    }
}