package gloomlib.gui.component.builtin;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * A component that cycles through a list of items every X ticks.
 */
public class AnimatedComponent implements GloomComponent {

    private final List<ItemStack> frames;
    private final int speed; // Ticks per frame
    private int currentTick = 0;
    private int currentFrameIndex = 0;
    private GloomGui parent;

    public AnimatedComponent(List<ItemStack> frames, int speed) {
        this.frames = new ArrayList<>(frames);
        this.speed = speed;
    }

    @Override
    public void tick() {
        currentTick++;
        if (currentTick >= speed) {
            currentTick = 0;
            currentFrameIndex = (currentFrameIndex + 1) % frames.size();

            // Trigger a redraw if the frame changed
            if (parent != null) {
                // In an optimized system, we would ask to redraw only this slot.
                // For now, full redraw is safe.
                parent.redraw();
            }
        }
    }

    @Override
    public @NotNull ItemStack render() {
        if (frames.isEmpty()) return null;
        return frames.get(currentFrameIndex);
    }

    @Override
    public void handleClick(@NotNull InteractionContext context) {
        // Animation typically passes clicks through or does nothing
    }

    @Override
    public void setParent(@Nullable GloomGui gui) {
        this.parent = gui;
    }

    @Override
    public @NotNull AnimatedComponent clone() {
        try {
            AnimatedComponent cloned = (AnimatedComponent) super.clone();
            // Reset state for the new instance
            cloned.currentTick = 0;
            cloned.currentFrameIndex = 0;
            cloned.parent = null;
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}