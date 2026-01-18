package gloomlib.gui.component.builtin;

import gloomlib.gui.animation.Animation;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class AnimatedComponent implements GloomComponent {

    private final Animation<ItemStack> animation;
    private final int updateInterval;
    private long startTick = -1;

    public AnimatedComponent(Animation<ItemStack> animation, int updateInterval) {
        this.animation = animation;
        this.updateInterval = Math.max(1, updateInterval);
    }

    public AnimatedComponent(Animation<ItemStack> animation) {
        this(animation, 1);
    }

    @Override
    public @NotNull ItemStack render(int index) {
        if (startTick == -1) {
            startTick = System.currentTimeMillis() / 50;
        }

        long currentTick = (System.currentTimeMillis() / 50);
        return animation.getFrame(currentTick - startTick);
    }

    @Override
    public void onClick(InteractionContext context) {
    }

    @Override
    public boolean onTick() {
        long currentTick = (System.currentTimeMillis() / 50);

        return currentTick % updateInterval == 0;
    }

    @Override
    public int getTickRate() {
        return updateInterval;
    }

    @Override
    public GloomComponent clone() {
        return new AnimatedComponent(animation, updateInterval);
    }
}