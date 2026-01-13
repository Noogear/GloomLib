package gloomlib.gui.component.builtin;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * A component that cycles state on click (e.g., ON/OFF button).
 */
public class CycleComponent implements GloomComponent {

    private final List<ItemStack> states;
    private final ReactiveState<Integer> currentIndex;
    private Consumer<Integer> onStateChange;
    private GloomGui parent;

    public CycleComponent(List<ItemStack> states, int initialIndex) {
        this.states = new ArrayList<>(states);
        this.currentIndex = new ReactiveState<>(initialIndex);
    }

    public void setOnStateChange(Consumer<Integer> callback) {
        this.onStateChange = callback;
        // Listen to internal state changes to trigger redraws
        this.currentIndex.subscribe(newVal -> {
            if (parent != null) parent.redraw();
        });
    }

    @Override
    public @NotNull ItemStack render() {
        int idx = currentIndex.get() % states.size();
        return states.get(idx);
    }

    @Override
    public void handleClick(@NotNull InteractionContext context) {
        int next = (currentIndex.get() + 1) % states.size();
        currentIndex.set(next);

        if (onStateChange != null) {
            onStateChange.accept(next);
        }
    }

    @Override
    public void setParent(@Nullable GloomGui gui) {
        this.parent = gui;
    }

    @Override
    public @NotNull CycleComponent clone() {
        try {
            CycleComponent cloned = (CycleComponent) super.clone();

            // CRITICAL: Deep clone the state.
            // We want a FRESH state starting at 0 (or original initial), not a shared reference.
            // Using reflection here implies we treat 'currentIndex' as a mutable field in the clone logic.
            var field = CycleComponent.class.getDeclaredField("currentIndex");
            field.setAccessible(true);
            field.set(cloned, new ReactiveState<>(0));

            cloned.parent = null;

            // Re-hook the internal redraw listener for the NEW parent
            cloned.currentIndex.subscribe(val -> {
                if (cloned.parent != null) cloned.parent.redraw();
            });

            return cloned;
        } catch (Exception e) {
            throw new RuntimeException("Failed to clone CycleComponent", e);
        }
    }
}