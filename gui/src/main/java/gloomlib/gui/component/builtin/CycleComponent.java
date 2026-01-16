package gloomlib.gui.component.builtin;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

public class CycleComponent<T> implements GloomComponent {

    private final ReactiveState<T> state;
    private final List<T> values;
    private final Function<T, ItemStack> renderer;
    private final Consumer<T> changeListener;
    private final Consumer<T> stateListener;
    private boolean dirty = true;
    private ItemStack cachedItem;

    public CycleComponent(ReactiveState<T> state, List<T> values,
                          Function<T, ItemStack> renderer,
                          Consumer<T> changeListener) {
        this.state = state;
        this.values = values;
        this.renderer = renderer;
        this.changeListener = changeListener;

        this.stateListener = (val) -> dirty = true;
        this.state.subscribe(this.stateListener);
    }

    @Override
    public @NotNull ItemStack render(int index) {
        if (dirty || cachedItem == null) {
            cachedItem = renderer.apply(state.get());
            dirty = false;
        }
        return cachedItem;
    }

    @Override
    public void onClick(InteractionContext context) {
        T current = state.get();
        int index = values.indexOf(current);
        int nextIndex = (index + 1) % values.size();
        T next = values.get(nextIndex);

        state.set(next);

        if (changeListener != null) {
            changeListener.accept(next);
        }
    }

    @Override
    public boolean onTick() {
        return false;
    }

    @Override
    public void dispose() {
        state.unsubscribe(stateListener);
    }

    @Override
    public CycleComponent<T> clone() {
        try {
            CycleComponent<T> clone = (CycleComponent<T>) super.clone();
            clone.dirty = true;
            clone.cachedItem = null;
            this.state.subscribe(clone.stateListener);
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}