package gloomlib.gui.component.builtin;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Component that displays a scrollable list of data.
 *
 * @param <T> the data type
 */
public class ScrollComponent<T> implements GloomComponent {

    private final ReactiveState<List<T>> dataState;
    private final ReactiveState<Integer> scrollState;
    private final int viewportSize;
    private final Function<T, ItemStack> renderer;
    private final BiConsumer<InteractionContext, T> clickHandler;
    private List<T> currentData;

    /**
     * Constructs a scroll component.
     *
     * @param dataState the reactive data state
     * @param viewportSize the number of visible items
     * @param renderer the function to render each item
     * @param clickHandler the callback for item clicks
     */
    public ScrollComponent(ReactiveState<List<T>> dataState,
                           int viewportSize,
                           Function<T, ItemStack> renderer,
                           BiConsumer<InteractionContext, T> clickHandler) {
        this.dataState = dataState;
        this.scrollState = new ReactiveState<>(0);
        this.viewportSize = viewportSize;
        this.renderer = renderer;
        this.clickHandler = clickHandler;
        this.currentData = dataState.get() != null ? dataState.get() : Collections.emptyList();

        this.dataState.subscribe(newData -> {
            this.currentData = newData != null ? newData : Collections.emptyList();
            validateScroll();
        });
        this.scrollState.subscribe(offset -> {
        });
    }

    /**
     * Constructs a scroll component with static data.
     *
     * @param staticData the data list
     * @param viewportSize the number of visible items
     * @param renderer the function to render each item
     * @param clickHandler the callback for item clicks
     */
    public ScrollComponent(List<T> staticData,
                           int viewportSize,
                           Function<T, ItemStack> renderer,
                           BiConsumer<InteractionContext, T> clickHandler) {
        this(ReactiveState.of(staticData), viewportSize, renderer, clickHandler);
    }

    @Override
    public @NotNull ItemStack render(int index) {
        int dataIndex = scrollState.get() + index;
        if (dataIndex >= 0 && dataIndex < currentData.size()) {
            T data = currentData.get(dataIndex);
            if (data != null) return renderer.apply(data);
        }
        return new ItemStack(Material.AIR);
    }

    @Override
    public void onClick(InteractionContext context) {
        int index = context.componentIndex();
        int dataIndex = scrollState.get() + index;
        if (dataIndex >= 0 && dataIndex < currentData.size()) {
            T data = currentData.get(dataIndex);
            if (clickHandler != null) clickHandler.accept(context, data);
        }
    }

    @Override
    public boolean onTick() {
        return false;
    }

    public void scrollDown(int amount) {
        int maxOffset = Math.max(0, currentData.size() - viewportSize);
        int current = scrollState.get();
        if (current < maxOffset) scrollState.set(Math.min(current + amount, maxOffset));
    }

    public void scrollUp(int amount) {
        int current = scrollState.get();
        if (current > 0) scrollState.set(Math.max(0, current - amount));
    }

    public boolean canScrollUp() {
        return scrollState.get() > 0;
    }

    public boolean canScrollDown() {
        int maxOffset = Math.max(0, currentData.size() - viewportSize);
        return scrollState.get() < maxOffset;
    }

    private void validateScroll() {
        int maxOffset = Math.max(0, currentData.size() - viewportSize);
        if (scrollState.get() > maxOffset) scrollState.set(maxOffset);
    }

    public ReactiveState<Integer> getScrollState() {
        return scrollState;
    }

    @Override
    public GloomComponent clone() {
        return new ScrollComponent<>(dataState, viewportSize, renderer, clickHandler);
    }
}
