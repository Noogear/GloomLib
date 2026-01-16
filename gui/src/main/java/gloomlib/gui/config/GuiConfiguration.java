package gloomlib.gui.config;

public record GuiConfiguration(
        UpdateStrategy updateStrategy,
        int tickRate,
        boolean enableAnimations
) {
    public static final GuiConfiguration DEFAULT = new GuiConfiguration(UpdateStrategy.REACTIVE, -1, false);

    public static final GuiConfiguration REACTIVE = DEFAULT;

    public static final GuiConfiguration ANIMATED = new GuiConfiguration(UpdateStrategy.PERIODIC, 1, true);

    public enum UpdateStrategy {
        REACTIVE,

        PERIODIC
    }
}