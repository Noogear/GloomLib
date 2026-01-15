package gloomlib.gui.component.builtin;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import gloomlib.gui.util.Paginator;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 滾動列表組件。
 */
public class ScrollComponent<T> implements GloomComponent {

    private final ReactiveState<List<T>> dataState;
    private final ReactiveState<Integer> pageState;
    private final Function<T, ItemStack> itemRenderer;
    private final BiConsumer<InteractionContext, T> clickHandler;

    private final int pageSize;

    private Paginator<T> paginator;
    private List<T> currentItems;
    private boolean dirty = true;

    // [Fix] 類型修正
    private final Consumer<List<T>> dataListener;
    private final Consumer<Integer> pageListener;

    public ScrollComponent(ReactiveState<List<T>> dataState,
                           int pageSize,
                           Function<T, ItemStack> itemRenderer,
                           BiConsumer<InteractionContext, T> clickHandler) {
        this.dataState = dataState;
        this.pageState = new ReactiveState<>(0);
        this.pageSize = pageSize;
        this.itemRenderer = itemRenderer;
        this.clickHandler = clickHandler;

        // [Fix] Lambda 參數類型推斷正確
        this.dataListener = (list) -> {
            this.paginator = new Paginator<>(list, pageSize);
            if (pageState.get() >= paginator.getTotalPages()) {
                pageState.set(0);
            }
            this.dirty = true;
        };
        this.dataState.subscribe(this.dataListener);

        // 初始加載
        if (dataState.get() != null) {
            this.dataListener.accept(dataState.get());
        }

        this.pageListener = (page) -> this.dirty = true;
        this.pageState.subscribe(this.pageListener);
    }

    @Override
    public @NotNull ItemStack render(int index) {
        if (dirty || currentItems == null) {
            if (paginator != null) {
                currentItems = paginator.getPage(pageState.get());
            }
            dirty = false;
        }

        if (currentItems != null && index < currentItems.size()) {
            return itemRenderer.apply(currentItems.get(index));
        }
        return new ItemStack(Material.AIR);
    }

    @Override
    public void onClick(InteractionContext context) {
        int index = (int) context.componentState();

        if (currentItems != null && index < currentItems.size()) {
            T data = currentItems.get(index);
            if (clickHandler != null) {
                clickHandler.accept(context, data);
            }
        }
    }

    @Override
    public boolean onTick() {
        return false;
    }

    public void nextPage() {
        if (paginator != null && paginator.hasNext(pageState.get())) {
            pageState.set(pageState.get() + 1);
        }
    }

    public void prevPage() {
        if (paginator != null && paginator.hasPrev(pageState.get())) {
            pageState.set(pageState.get() - 1);
        }
    }

    public ReactiveState<Integer> getPageState() {
        return pageState;
    }

    @Override
    public void dispose() {
        dataState.unsubscribe(dataListener);
        pageState.unsubscribe(pageListener);
    }

    @Override
    public ScrollComponent<T> clone() {
        try {
            ScrollComponent<T> clone = (ScrollComponent<T>) super.clone();
            clone.dirty = true;
            clone.currentItems = null;
            return clone;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}