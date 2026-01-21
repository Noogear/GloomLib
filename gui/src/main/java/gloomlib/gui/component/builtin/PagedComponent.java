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

public class PagedComponent<T> implements GloomComponent {

    private final ReactiveState<List<T>> dataState;
    private final ReactiveState<Integer> pageState;
    private final Function<T, ItemStack> itemRenderer;
    private final BiConsumer<InteractionContext, T> clickHandler;
    private final int pageSize;
    private final Consumer<List<T>> dataListener;
    private final Consumer<Integer> pageListener;
    private Paginator<T> paginator;
    private List<T> currentItems;

    // 性能优化：增量更新标记
    private boolean dirty = true;
    private int cachedPage = -1;
    private final java.util.BitSet dirtyIndices;
    private final ItemStack[] renderedCache;

    public PagedComponent(ReactiveState<List<T>> dataState,
                          int pageSize,
                          Function<T, ItemStack> itemRenderer,
                          BiConsumer<InteractionContext, T> clickHandler) {
        this.dataState = dataState;
        this.pageState = new ReactiveState<>(0);
        this.pageSize = pageSize;
        this.itemRenderer = itemRenderer;
        this.clickHandler = clickHandler;
        
        // 初始化缓存
        this.dirtyIndices = new java.util.BitSet(pageSize);
        this.renderedCache = new ItemStack[pageSize];

        this.dataListener = (list) -> {
            this.paginator = new Paginator<>(list, pageSize);
            if (paginator.getTotalPages() > 0 && pageState.get() >= paginator.getTotalPages()) {
                pageState.set(0);
            } else {
                this.dirty = true;
                // 数据变化时标记所有槽位为脏
                this.dirtyIndices.set(0, pageSize);
            }
        };
        this.dataState.subscribe(this.dataListener);

        if (dataState.get() != null) {
            this.dataListener.accept(dataState.get());
        }

        this.pageListener = (page) -> {
            this.dirty = true;
            // 页面变化时标记所有槽位为脏
            this.dirtyIndices.set(0, pageSize);
        };
        this.pageState.subscribe(this.pageListener);
    }

    public PagedComponent(List<T> staticData,
                          int pageSize,
                          Function<T, ItemStack> itemRenderer,
                          BiConsumer<InteractionContext, T> clickHandler) {
        this(ReactiveState.of(staticData), pageSize, itemRenderer, clickHandler);
    }

    @Override
    public @NotNull ItemStack render(int index) {
        if (index < 0 || index >= pageSize) {
            return new ItemStack(Material.AIR);
        }

        int currentPage = pageState.get();

        // 页面切换：重新加载当前页数据
        if (dirty || currentItems == null || currentPage != cachedPage) {
            if (paginator != null) {
                currentItems = paginator.getPage(currentPage);
                cachedPage = currentPage;
            }
            dirty = false;
        }

        // 增量更新：只重新渲染脏槽位
        if (dirtyIndices.get(index)) {
            if (currentItems != null && index < currentItems.size()) {
                renderedCache[index] = itemRenderer.apply(currentItems.get(index));
            } else {
                renderedCache[index] = new ItemStack(Material.AIR);
            }
            dirtyIndices.clear(index);
        }

        // 返回缓存的渲染结果
        ItemStack cached = renderedCache[index];
        return cached != null ? cached : new ItemStack(Material.AIR);
    }

    @Override
    public void onClick(InteractionContext context) {
        int index = context.componentIndex();
        if (currentItems != null && index < currentItems.size()) {
            if (clickHandler != null) {
                clickHandler.accept(context, currentItems.get(index));
            }
        }
    }

    @Override
    public boolean onTick() {
        // 返回脏标志状态，如果需要更新则返回 true
        // 不在这里重置 dirty，让 render() 方法处理
        return dirty;
    }

    public boolean hasNext() {
        return paginator != null && paginator.hasNext(pageState.get());
    }

    public boolean hasPrev() {
        return paginator != null && paginator.hasPrev(pageState.get());
    }

    public void nextPage() {
        if (hasNext()) pageState.set(pageState.get() + 1);
    }

    public void prevPage() {
        if (hasPrev()) pageState.set(pageState.get() - 1);
    }

    public ReactiveState<Integer> getPageState() {
        return pageState;
    }

    public ReactiveState<List<T>> getDataState() {
        return dataState;
    }

    @Override
    public void dispose() {
        dataState.unsubscribe(dataListener);
        pageState.unsubscribe(pageListener);
    }

    @Override
    public PagedComponent<T> clone() {
        try {
            @SuppressWarnings("unchecked")
            PagedComponent<T> cloned = (PagedComponent<T>) super.clone();
            cloned.dirty = true;
            cloned.currentItems = null;
            cloned.cachedPage = -1;
            cloned.dirtyIndices.set(0, pageSize); // 标记所有为脏
            return cloned;
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}