package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.component.builtin.InventoryLinkComponent;
import gloomlib.gui.config.GuiConfiguration;
import gloomlib.gui.interaction.ClickActionHandler;
import gloomlib.gui.interaction.DragHandler;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.interaction.SlotPriority;
import gloomlib.gui.slot.SlotElement;
import gloomlib.gui.state.MutableProperty;
import gloomlib.gui.state.Property;
import gloomlib.gui.window.AbstractWindow;
import gloomlib.gui.window.Observer;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Core GUI implementation with component management and reactive state.
 * <p>
 * Optimized with array-based slot lookups and batch update support.
 */
public final class GloomGui implements Gui.Normal {

    private final Player player;
    private final Component title;
    private final int size;
    private final GuiConfiguration configuration;
    private final Consumer<InventoryCloseEvent> closeAction;

    private final Map<Integer, GloomComponent> components = new HashMap<>();
    private final Map<Integer, Integer> componentIndices = new HashMap<>();
    private final Map<Integer, SlotElement> slotElements = new HashMap<>();
    private final List<Integer> tickingSlots = new ArrayList<>();
    private final Map<Integer, Set<ObserverEntry>> observers = new ConcurrentHashMap<>();
    private final MutableProperty<Boolean> frozen = MutableProperty.of(false);
    private final MutableProperty<ItemStack> background = MutableProperty.of(null);
    private final java.util.BitSet dirtySlots;
    private GloomComponent[] slotToComponent;
    private int[] slotToComponentIndex;
    private SlotPriority slotPriority = SlotPriority.normal();
    private Inventory inventory;
    private boolean batchUpdateMode = false;

    /**
     * Constructs a new GloomGui.
     *
     * @param player         the player for scheduler context
     * @param title          the GUI title
     * @param rows           the number of rows (for chest types)
     * @param type           the inventory type
     * @param configuration  the GUI configuration
     * @param closeAction    the close event handler (nullable)
     * @param structure      the structure pattern (nullable)
     * @param charComponents the character-to-component mapping
     * @param slotComponents the slot-to-component mapping
     */
    public GloomGui(Player player,
                    Component title,
                    int rows,
                    InventoryType type,
                    GuiConfiguration configuration,
                    Consumer<InventoryCloseEvent> closeAction,
                    String[] structure,
                    Map<Character, GloomComponent> charComponents,
                    Map<Integer, GloomComponent> slotComponents) {
        this.player = player;
        this.title = title;
        this.configuration = configuration;
        this.closeAction = closeAction;
        this.size = (type == InventoryType.CHEST) ? rows * 9 : type.getDefaultSize();
        this.dirtySlots = new java.util.BitSet(size);

        calculateLayout(type, structure, charComponents, slotComponents);
    }

    @Override
    @Nullable
    public Inventory getInventory() {
        return inventory;
    }

    private void calculateLayout(InventoryType type,
                                 String[] structure,
                                 Map<Character, GloomComponent> charComponents,
                                 Map<Integer, GloomComponent> slotComponents) {
        int width = 9;
        if (type == InventoryType.HOPPER) width = 5;
        else if (type == InventoryType.DISPENSER || type == InventoryType.DROPPER || type == InventoryType.WORKBENCH)
            width = 3;

        Map<GloomComponent, Integer> counters = new HashMap<>();

        if (structure != null) {
            for (int r = 0; r < structure.length; r++) {
                String rowStr = structure[r].replace(" ", "");
                for (int c = 0; c < rowStr.length() && c < width; c++) {
                    char key = rowStr.charAt(c);
                    if (key == '.') continue;

                    GloomComponent comp = charComponents.get(key);
                    if (comp != null) {
                        int slot = r * width + c;
                        int idx = counters.getOrDefault(comp, 0);

                        this.components.put(slot, comp);
                        this.componentIndices.put(slot, idx);

                        counters.put(comp, idx + 1);
                    }
                }
            }
        }

        slotComponents.forEach((slot, comp) -> {
            this.components.put(slot, comp);
            this.componentIndices.put(slot, 0);
        });

        // 性能优化：构建数组索引
        buildSlotArrays();

        this.tickingSlots.clear();
        this.components.forEach((slot, component) -> {
            if (component.getTickRate() > 0) {
                this.tickingSlots.add(slot);
            }
        });
    }

    /**
     * 构建槽位数组索引，提供 O(1) 访问性能
     */
    private void buildSlotArrays() {
        this.slotToComponent = new GloomComponent[size];
        this.slotToComponentIndex = new int[size];

        for (int i = 0; i < size; i++) {
            this.slotToComponent[i] = components.get(i);
            this.slotToComponentIndex[i] = componentIndices.getOrDefault(i, 0);
        }
    }

    @Override
    public void bindToWindow(@NotNull AbstractWindow window) {
        this.inventory = window.getInventory();

        background.observeWeak(bg -> {
            if (inventory != null) {
                applyBackground();
            }
        });

        redraw();
    }

    private void applyBackground() {
        ItemStack bg = background.get();
        if (bg == null) {
            return;
        }

        for (int i = 0; i < size; i++) {
            if (!slotElements.containsKey(i) && !components.containsKey(i)) {
                inventory.setItem(i, bg.clone());
                notifySlotObservers(i);
            }
        }
    }

    @Override
    public void redraw() {
        if (inventory == null) return;

        markAllDirty();
        flushUpdates();
    }

    @Override
    public void tick() {
        if (inventory == null) return;

        for (Integer slot : tickingSlots) {
            GloomComponent component = components.get(slot);
            if (component.onTick()) {
                markDirty(slot);
            }
        }

        flushUpdates();
    }

    public void markDirty(int slot) {
        if (slot >= 0 && slot < size) {
            dirtySlots.set(slot);
        }
    }

    public void markAllDirty() {
        dirtySlots.set(0, size);
    }

    public void flushUpdates() {
        if (inventory == null || batchUpdateMode) return;

        for (int slot = dirtySlots.nextSetBit(0); slot >= 0; slot = dirtySlots.nextSetBit(slot + 1)) {
            ItemStack newItem = renderSlot(slot);
            updateInventoryItem(slot, newItem);
        }

        dirtySlots.clear();
    }

    public void beginBatchUpdate() {
        batchUpdateMode = true;
    }

    public void endBatchUpdate() {
        batchUpdateMode = false;
        flushUpdates();
    }

    private void updateInventoryItem(int slot, ItemStack newItem) {
        ItemStack currentItem = inventory.getItem(slot);

        if (shouldUpdateSlot(currentItem, newItem)) {
            inventory.setItem(slot, newItem);
            notifySlotObservers(slot);
        }
    }

    private boolean shouldUpdateSlot(ItemStack current, ItemStack newItem) {
        if (current == null && newItem == null) {
            return false;
        }

        if (current == null || newItem == null) {
            return true;
        }

        if (!current.isSimilar(newItem)) {
            return true;
        }

        return current.getAmount() != newItem.getAmount();
    }

    public void handleClose(InventoryCloseEvent event) {
        if (closeAction != null) {
            closeAction.accept(event);
        }
        dispose();
    }

    private void dispose() {
        Set<GloomComponent> distinctComponents = new HashSet<>(components.values());
        for (GloomComponent component : distinctComponents) {
            try {
                component.dispose();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void handleClick(InventoryClickEvent event) {
        // 冻结状态拦截所有点击
        if (frozen.get()) {
            event.setCancelled(true);
            return;
        }

        Player clicker = event.getWhoClicked() instanceof Player ? (Player) event.getWhoClicked() : player;
        ClickType clickType = event.getClick();
        int slot = event.getSlot();

        // 点击 GUI 区域
        if (event.getClickedInventory() == event.getInventory()) {
            handleGuiClick(event, clicker, clickType, slot);
        }
        // 点击玩家背包区域
        else if (event.getClickedInventory() != null) {
            handlePlayerInventoryClick(event, clicker, clickType);
        }
    }

    private void handleGuiClick(InventoryClickEvent event, Player clicker, ClickType clickType, int slot) {
        GloomComponent component = getComponent(slot);
        ItemStack slotItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        // InventoryLinkComponent 特殊处理
        if (component instanceof InventoryLinkComponent link) {
            if (!link.allowInteraction()) {
                event.setCancelled(true);
            }
            // 允许交互的 InventoryLink 不取消事件，让 Bukkit 处理
            return;
        }

        // 默认取消 GUI 区域的所有事件
        event.setCancelled(true);

        // 使用 ClickActionHandler 处理不同类型的点击
        boolean changed = false;
        ItemStack newSlotItem = null;
        ItemStack newCursorItem = null;

        switch (clickType) {
            case LEFT -> {
                ClickActionHandler.ClickResult result = ClickActionHandler.handleLeftClick(clicker, slotItem, cursorItem);
                changed = result.changed();
                newSlotItem = result.newSlotItem();
                newCursorItem = result.newCursorItem();
            }
            case RIGHT -> {
                ClickActionHandler.ClickResult result = ClickActionHandler.handleRightClick(clicker, slotItem, cursorItem);
                changed = result.changed();
                newSlotItem = result.newSlotItem();
                newCursorItem = result.newCursorItem();
            }
            case SHIFT_LEFT, SHIFT_RIGHT -> {
                PlayerInventory playerInv = clicker.getInventory();
                // 使用优先级策略处理 Shift+点击
                ClickActionHandler.ShiftClickResult result = ClickActionHandler.handleShiftClickWithPriority(
                        slotItem, playerInv, 0, 36, slotPriority
                );
                if (result.moved()) {
                    changed = true;
                    newSlotItem = result.remaining();
                }
            }
            case NUMBER_KEY -> {
                int hotbarSlot = event.getHotbarButton();
                PlayerInventory playerInv = clicker.getInventory();
                ClickActionHandler.HotbarSwapResult result = ClickActionHandler.handleHotbarSwap(
                        slotItem, hotbarSlot, playerInv
                );
                if (result.swapped()) {
                    changed = true;
                    newSlotItem = result.newSlotItem();
                }
            }
            case DOUBLE_CLICK -> {
                ClickActionHandler.DoubleClickResult guiResult = ClickActionHandler.handleDoubleClick(
                        cursorItem, inventory, 0, size
                );
                ClickActionHandler.DoubleClickResult playerResult = ClickActionHandler.handleDoubleClick(
                        guiResult.newCursorItem(), clicker.getInventory(), 0, 36
                );
                if (guiResult.collected() || playerResult.collected()) {
                    changed = true;
                    newCursorItem = playerResult.newCursorItem();
                }
            }
            case MIDDLE -> {
                ClickActionHandler.MiddleClickResult result = ClickActionHandler.handleMiddleClick(clicker, slotItem);
                if (result.cloned()) {
                    changed = true;
                    newCursorItem = result.newCursorItem();
                }
            }
            case SWAP_OFFHAND -> {
                PlayerInventory playerInv = clicker.getInventory();
                ClickActionHandler.OffhandSwapResult result = ClickActionHandler.handleOffhandSwap(
                        slotItem, playerInv
                );
                if (result.swapped()) {
                    changed = true;
                    newSlotItem = result.newSlotItem();
                }
            }
            case DROP, CONTROL_DROP -> {
                boolean dropAll = clickType == ClickType.CONTROL_DROP;
                ClickActionHandler.DropResult result = ClickActionHandler.handleDrop(slotItem, dropAll, clicker);
                if (result.dropped()) {
                    changed = true;
                    newSlotItem = result.newSlotItem();
                }
            }
            default -> {
                // UNKNOWN, WINDOW_BORDER_LEFT, WINDOW_BORDER_RIGHT, CREATIVE 等不处理
            }
        }

        // 应用变更
        if (changed) {
            if (newSlotItem != null || clickType.isLeftClick() || clickType.isRightClick()
                    || clickType == ClickType.SWAP_OFFHAND || clickType == ClickType.DROP
                    || clickType == ClickType.CONTROL_DROP || clickType == ClickType.NUMBER_KEY) {
                inventory.setItem(slot, newSlotItem);
                markDirty(slot);
            }
            if (newCursorItem != null || clickType.isLeftClick() || clickType.isRightClick()
                    || clickType == ClickType.MIDDLE || clickType == ClickType.DOUBLE_CLICK) {
                clicker.setItemOnCursor(newCursorItem);
            }
        }

        // 调用组件的 onClick 回调
        if (component != null) {
            InteractionContext context = new InteractionContext(
                    clicker, clickType, event.getAction(), slot, slotItem, getComponentIndex(slot)
            );
            try {
                component.onClick(context);
                if (component.onTick()) {
                    updateInventoryItem(slot, component.render(getComponentIndex(slot)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void handlePlayerInventoryClick(InventoryClickEvent event, Player clicker, ClickType clickType) {
        // 阻止 Shift+点击从玩家背包移动物品到 GUI（GUI 槽位由组件控制）
        if (clickType.isShiftClick() && event.getAction().toString().contains("MOVE_TO")) {
            event.setCancelled(true);
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        Set<Integer> rawSlots = event.getRawSlots();
        Set<Integer> guiSlots = new java.util.HashSet<>();
        Set<Integer> linkSlots = new java.util.HashSet<>();

        // 分类槽位：GUI 槽位 vs InventoryLink 槽位
        for (int rawSlot : rawSlots) {
            if (rawSlot < size) {
                GloomComponent component = getComponent(rawSlot);
                if (component instanceof InventoryLinkComponent link && link.allowInteraction()) {
                    linkSlots.add(rawSlot);
                } else {
                    guiSlots.add(rawSlot);
                }
            }
        }

        // 如果涉及非InventoryLink的GUI槽位，取消事件
        if (!guiSlots.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        // 如果只涉及InventoryLink槽位，使用DragHandler处理
        if (!linkSlots.isEmpty()) {
            ItemStack draggedItem = event.getOldCursor();
            DragHandler.DragResult result = DragHandler.handleDrag(
                    event.getType(),
                    draggedItem,
                    linkSlots,
                    slot -> {
                        GloomComponent comp = getComponent(slot);
                        if (comp instanceof InventoryLinkComponent link) {
                            return link.linkedInventory().getItem(link.linkedSlot());
                        }
                        return null;
                    }
            );

            // 应用结果
            event.setCancelled(true);
            result.updatedSlots().forEach((slot, item) -> {
                GloomComponent comp = getComponent(slot);
                if (comp instanceof InventoryLinkComponent link) {
                    link.linkedInventory().setItem(link.linkedSlot(), item);
                    markDirty(slot);
                }
            });

            Player player = (Player) event.getWhoClicked();
            player.setItemOnCursor(result.remaining());
        }
    }

    @Override
    @NotNull
    public Player getPlayer() {
        return player;
    }

    @Override
    @NotNull
    public Component getTitle() {
        return title;
    }

    @Override
    public int getSize() {
        return size;
    }

    public GuiConfiguration getConfiguration() {
        return configuration;
    }

    @Override
    @Nullable
    public GloomComponent getComponent(int slot) {
        if (slot < 0 || slot >= size) {
            return null;
        }
        return slotToComponent[slot];
    }

    @Override
    public int getComponentIndex(int slot) {
        if (slot < 0 || slot >= size) {
            return 0;
        }
        return slotToComponentIndex[slot];
    }

    @Override
    @NotNull
    public Map<Integer, GloomComponent> getLayout() {
        return Collections.unmodifiableMap(components);
    }

    public Property<Boolean> getFrozen() {
        return frozen;
    }

    @Override
    public boolean isFrozen() {
        return frozen.get();
    }

    @Override
    public void setFrozen(boolean frozen) {
        this.frozen.set(frozen);
    }

    @Override
    @Nullable
    public ItemStack getBackground() {
        return background.get();
    }

    @Override
    public void setBackground(@Nullable ItemStack background) {
        this.background.set(background);
    }

    /**
     * 获取当前的槽位优先级策略
     */
    @NotNull
    public SlotPriority getSlotPriority() {
        return slotPriority;
    }

    /**
     * 设置 Shift+点击的槽位优先级策略
     *
     * @param priority 优先级策略
     */
    public void setSlotPriority(@NotNull SlotPriority priority) {
        this.slotPriority = priority;
    }

    @Override
    public void setSlotElement(int slot, @NotNull SlotElement element) {
        slotElements.put(slot, element);
        markDirty(slot);
    }

    @Override
    @Nullable
    public SlotElement getSlotElement(int slot) {
        return slotElements.get(slot);
    }

    @Override
    public void removeSlotElement(int slot) {
        slotElements.remove(slot);
        markDirty(slot);
    }

    @Override
    @Nullable
    public ItemStack renderSlot(int slot) {
        SlotElement element = slotElements.get(slot);
        if (element != null) {
            return element.render(player);
        }

        GloomComponent component = components.get(slot);
        if (component != null) {
            return component.render(componentIndices.get(slot));
        }

        return background.get();
    }

    @Override
    public void addObserver(@NotNull Observer who, int what, int how) {
        observers.computeIfAbsent(what, k -> ConcurrentHashMap.newKeySet())
                .add(new ObserverEntry(who, how));
    }

    @Override
    public void removeObserver(@NotNull Observer who, int what, int how) {
        Set<ObserverEntry> slotObservers = observers.get(what);
        if (slotObservers != null) {
            slotObservers.removeIf(entry -> entry.observer() == who);
            if (slotObservers.isEmpty()) {
                observers.remove(what);
            }
        }
    }

    @Override
    public void removeAllObservers(@NotNull Observer who) {
        observers.values().forEach(set ->
                set.removeIf(entry -> entry.observer() == who)
        );
        observers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    public void notifySlotObservers(int slot) {
        Set<ObserverEntry> slotObservers = observers.get(slot);
        if (slotObservers != null) {
            slotObservers.forEach(entry -> entry.observer().notifyUpdate(entry.how()));
        }
    }

    public void notifyAllObservers() {
        for (int slot = 0; slot < size; slot++) {
            notifySlotObservers(slot);
        }
    }

    @Override
    public int getUpdatePeriod(int what) {
        SlotElement element = slotElements.get(what);
        if (element instanceof SlotElement.ComponentSlot componentSlot) {
            GloomComponent component = componentSlot.component();
            int tickRate = component.getTickRate();
            if (tickRate > 0) {
                return tickRate;
            }
        }

        GloomComponent component = components.get(what);
        if (component != null) {
            int tickRate = component.getTickRate();
            if (tickRate > 0) {
                return tickRate;
            }
        }

        return -1;
    }

    private record ObserverEntry(@NotNull Observer observer, int how) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ObserverEntry entry)) return false;
            return observer == entry.observer;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(observer);
        }
    }
}