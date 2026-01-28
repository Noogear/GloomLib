package gloomlib.gui.component.builtin;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Component that cycles through a list of values on click.
 * <p>
 * Displays the current value using a renderer function and advances
 * to the next value when clicked.
 *
 * @param <T> the value type
 */
public class CycleComponent<T> implements GloomComponent {

    private final ReactiveState<T> state;
    private final List<T> possibleValues;
    private final Function<T, ItemStack> renderer;
    private final BiConsumer<InteractionContext, T> onStateChange;

    /**
     * Constructs a cycle component.
     *
     * @param initialState  the reactive state holding the current value
     * @param values        the list of possible values to cycle through
     * @param renderer      the function to render each value
     * @param onStateChange the callback when value changes (nullable)
     */
    public CycleComponent(ReactiveState<T> initialState,
                          List<T> values,
                          Function<T, ItemStack> renderer,
                          BiConsumer<InteractionContext, T> onStateChange) {
        this.state = initialState;
        this.possibleValues = new ArrayList<>(values);
        this.renderer = renderer;
        this.onStateChange = onStateChange;

        if (possibleValues.isEmpty()) {
            throw new IllegalArgumentException("CycleComponent must have at least one possible value.");
        }

        if (state.get() == null || !possibleValues.contains(state.get())) {
            state.set(possibleValues.getFirst());
        }
    }

    /**
     * Constructs a cycle component with varargs.
     *
     * @param initialState the reactive state holding the current value
     * @param renderer     the function to render each value
     * @param values       the possible values to cycle through
     */
    @SafeVarargs
    public CycleComponent(ReactiveState<T> initialState,
                          Function<T, ItemStack> renderer,
                          T... values) {
        this(initialState, Arrays.asList(values), renderer, null);
    }

    @Override
    public @NotNull ItemStack render(int index) {
        T current = state.get();
        if (current == null && !possibleValues.isEmpty()) {
            current = possibleValues.getFirst();
        }

        if (current != null) {
            return renderer.apply(current);
        }

        return new ItemStack(Material.BARRIER);
    }

    @Override
    public void onClick(InteractionContext context) {
        cycle();

        if (onStateChange != null) {
            onStateChange.accept(context, state.get());
        }
    }

    /**
     * Advances to the next value in the cycle.
     */
    public void cycle() {
        T current = state.get();
        int index = possibleValues.indexOf(current);

        if (index == -1) {
            if (!possibleValues.isEmpty()) state.set(possibleValues.getFirst());
        } else {
            int nextIndex = (index + 1) % possibleValues.size();
            state.set(possibleValues.get(nextIndex));
        }
    }

    @Override
    public boolean onTick() {
        return false;
    }

    @Override
    public int getTickRate() {
        return GloomComponent.super.getTickRate();
    }

    /**
     * Gets the reactive state holding the current value.
     *
     * @return the reactive state
     */
    public ReactiveState<T> getState() {
        return state;
    }

    @Override
    public GloomComponent clone() {
        return new CycleComponent<>(state, possibleValues, renderer, onStateChange);
    }
}