package gloomlib.gui.component.builtin;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class AnimatedComponent implements GloomComponent {

    private final List<ItemStack> frames;
    private final int tickRate;
    private final boolean repeat;
    private final Consumer<InteractionContext> clickHandler;

    private int currentFrame = 0;
    private boolean finished = false;

    public AnimatedComponent(List<ItemStack> frames, int tickRate, boolean repeat, Consumer<InteractionContext> clickHandler) {
        this.frames = new ArrayList<>(frames);
        this.tickRate = tickRate;
        this.repeat = repeat;
        this.clickHandler = clickHandler;
    }

    @Override
    public @NotNull ItemStack render(int index) {
        if (frames.isEmpty()) return null;
        return frames.get(currentFrame % frames.size());
    }

    @Override
    public void onClick(InteractionContext context) {
        if (clickHandler != null) {
            clickHandler.accept(context);
        }
    }

    @Override
    public boolean onTick() {
        if (finished && !repeat) return false;

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