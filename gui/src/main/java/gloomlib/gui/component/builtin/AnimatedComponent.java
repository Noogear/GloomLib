package gloomlib.gui.component.builtin;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Component that displays an animated sequence of item frames.
 * <p>
 * Cycles through frames at a specified tick rate, optionally repeating.
 * Frames are pre-cached for performance.
 */
public class AnimatedComponent implements GloomComponent {

    private final List<ItemStack> frames;
    private final int tickRate;
    private final boolean repeat;
    private final Consumer<InteractionContext> clickHandler;
    private final ItemStack[] frameCache;
    private int currentFrame = 0;
    private boolean finished = false;

    /**
     * Constructs an animated component.
     *
     * @param frames the animation frames
     * @param tickRate the ticks between frame changes
     * @param repeat whether to loop the animation
     * @param clickHandler the click handler (nullable)
     */
    public AnimatedComponent(List<ItemStack> frames, int tickRate, boolean repeat, Consumer<InteractionContext> clickHandler) {
        this.frames = new ArrayList<>(frames);
        this.tickRate = tickRate;
        this.repeat = repeat;
        this.clickHandler = clickHandler;
        this.frameCache = frames.stream()
                .map(ItemStack::clone)
                .toArray(ItemStack[]::new);
    }

    @Override
    public @NotNull ItemStack render(int index) {
        if (frameCache.length == 0) {
            return new ItemStack(org.bukkit.Material.AIR);
        }
        return frameCache[currentFrame % frameCache.length];
    }

    @Override
    public void onClick(InteractionContext context) {
        if (clickHandler != null) {
            clickHandler.accept(context);
        }
    }

    @Override
    public boolean onTick() {
        if (finished && !repeat) {
            return false;
        }

        currentFrame++;

        if (currentFrame >= frames.size()) {
            if (repeat) {
                currentFrame = 0;
            } else {
                currentFrame = frames.size() - 1;
                finished = true;
                return false;
            }
        }

        return true;
    }

    @Override
    public int getTickRate() {
        return tickRate;
    }

    @Override
    public GloomComponent clone() {
        return new AnimatedComponent(frames, tickRate, repeat, clickHandler);
    }
}
