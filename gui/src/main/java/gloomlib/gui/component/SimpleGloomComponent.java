package gloomlib.gui.component;

import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiFunction;
import java.util.function.Consumer;

public class SimpleGloomComponent implements GloomComponent {

    private final BiFunction<ReactiveState<?>, Integer, ItemStack> renderer;
    private final ReactiveState<?> bindState;
    private final Consumer<InteractionContext> clickHandler;
    private final Consumer<GloomComponent> tickHandler;

    private boolean dirty = true;
    private ItemStack cachedItem;

    private Consumer<Object> stateListener;

    public SimpleGloomComponent(BiFunction<ReactiveState<?>, Integer, ItemStack> renderer,
                                ReactiveState<?> bindState,
                                Consumer<InteractionContext> clickHandler,
                                Consumer<GloomComponent> tickHandler) {
        this.renderer = renderer;
        this.bindState = bindState;
        this.clickHandler = clickHandler;
        this.tickHandler = tickHandler;
        setupListener();
    }

    @SuppressWarnings("unchecked")
    private void setupListener() {
        if (this.bindState != null) {
            this.stateListener = (val) -> this.dirty = true;

            ((ReactiveState<Object>) this.bindState).subscribe(this.stateListener);
        }
    }

    @Override
    public @NotNull ItemStack render(int index) {
        if (dirty || cachedItem == null) {
            if (renderer != null) {
                cachedItem = renderer.apply(bindState, index);
            } else {
                cachedItem = new ItemStack(Material.AIR);
            }
            dirty = false;
        }
        return cachedItem;
    }

    @Override
    public void onClick(InteractionContext context) {
        if (clickHandler != null) {
            clickHandler.accept(context);
        }
    }

    @Override
    public boolean onTick() {
        if (tickHandler != null) {
            tickHandler.accept(this);
            this.dirty = true;
            return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void dispose() {
        if (bindState != null && stateListener != null) {
            ((ReactiveState<Object>) bindState).unsubscribe(stateListener);
        }
    }

    @Override
    public SimpleGloomComponent clone() {
        try {
            SimpleGloomComponent clone = (SimpleGloomComponent) super.clone();
            clone.dirty = true;
            clone.cachedItem = null;
            clone.setupListener();
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}