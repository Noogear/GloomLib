package gloomlib.gui.component.builtin;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * 動畫組件。
 */
public class AnimatedComponent implements GloomComponent {

    private final List<ItemStack> frames;
    private final long tickRate;
    private final Consumer<InteractionContext> clickHandler;

    private int currentFrameIndex = 0;
    private long tickCounter = 0;

    public AnimatedComponent(List<ItemStack> frames, long tickRate, Consumer<InteractionContext> clickHandler) {
        this.frames = new ArrayList<>(frames);
        this.tickRate = Math.max(1, tickRate);
        this.clickHandler = clickHandler;
    }

    @Override
    public @NotNull ItemStack render(int index) {
        if (frames.isEmpty()) return null;
        return frames.get(currentFrameIndex);
    }

    @Override
    public boolean onTick() {
        tickCounter++;
        if (tickCounter >= tickRate) {
            tickCounter = 0;
            currentFrameIndex = (currentFrameIndex + 1) % frames.size();
            return true;
        }
        return false;
    }

    @Override
    public void onClick(InteractionContext context) {
        if (clickHandler != null) {
            clickHandler.accept(context);
        }
    }

    @Override
    public AnimatedComponent clone() {
        try {
            AnimatedComponent clone = (AnimatedComponent) super.clone();
            clone.currentFrameIndex = 0;
            clone.tickCounter = 0;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private final List<ItemStack> frames = new ArrayList<>();
        private long tickRate = 20;
        private Consumer<InteractionContext> clickHandler;

        public Builder addFrame(ItemStack item) {
            frames.add(item);
            return this;
        }

        public Builder speed(long ticks) {
            this.tickRate = ticks;
            return this;
        }

        public Builder onClick(Consumer<InteractionContext> handler) {
            this.clickHandler = handler;
            return this;
        }

        public AnimatedComponent build() {
            return new AnimatedComponent(frames, tickRate, clickHandler);
        }
    }
}