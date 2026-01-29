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
     * @param player         the player context
     * @param title          the GUI title
     * @param rows           the number of rows
     * @param type           the inventory type
     * @param configuration  the GUI configuration
     * @param closeAction    the action on close
     * @param structure      the layout structure
     * @param charComponents the character components
     * @param slotComponents the slot components
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

    /**
     * Gets the Bukkit inventory.
     *
     * @return the inventory
     */
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

        buildSlotArrays();

        this.tickingSlots.clear();
        this.components.forEach((slot, component) -> {
            if (component.getTickRate() > 0) {
                this.tickingSlots.add(slot);
            }
        });
    }

    private void buildSlotArrays() {
        this.slotToComponent = new GloomComponent[size];
        this.slotToComponentIndex = new int[size];

        for (int i = 0; i < size; i++) {
            this.slotToComponent[i] = components.get(i);
            this.slotToComponentIndex[i] = componentIndices.getOrDefault(i, 0);
        }
    }

    /**
     * Binds the GUI to a window.
     *
     * @param window the window to bind
     */
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

    /**
     * Redraws the entire GUI.
     */
    @Override
    public void redraw() {
        if (inventory == null) return;

        markAllDirty();
        flushUpdates();
    }

    /**
     * Cycles the GUI state.
     */
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

    /**
     * Marks a slot as dirty.
     *
     * @param slot the slot index
     */
    public void markDirty(int slot) {
        if (slot >= 0 && slot < size) {
            dirtySlots.set(slot);
        }
    }

    /**
     * Marks all slots as dirty.
     */
    public void markAllDirty() {
        dirtySlots.set(0, size);
    }

    /**
     * Flushes all pending updates to the inventory.
     */
    public void flushUpdates() {
        if (inventory == null || batchUpdateMode) return;

        for (int slot = dirtySlots.nextSetBit(0); slot >= 0; slot = dirtySlots.nextSetBit(slot + 1)) {
            ItemStack newItem = renderSlot(slot);
            updateInventoryItem(slot, newItem);
        }

        dirtySlots.clear();
    }

    /**
     * Begins a batch update.
     */
    public void beginBatchUpdate() {
        batchUpdateMode = true;
    }

    /**
     * Ends a batch update.
     */
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

    /**
     * Handles the close event.
     *
     * @param event the close event
     */
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

    /**
     * Handles click events.
     *
     * @param event the click event
     */
    public void handleClick(InventoryClickEvent event) {
        if (frozen.get()) {
            event.setCancelled(true);
            return;
        }

        Player clicker = event.getWhoClicked() instanceof Player ? (Player) event.getWhoClicked() : player;
        ClickType clickType = event.getClick();
        int slot = event.getSlot();

        if (event.getClickedInventory() == event.getInventory()) {
            handleGuiClick(event, clicker, clickType, slot);
        } else if (event.getClickedInventory() != null) {
            handlePlayerInventoryClick(event, clicker, clickType);
        }
    }

    private void handleGuiClick(InventoryClickEvent event, Player clicker, ClickType clickType, int slot) {
        GloomComponent component = getComponent(slot);
        ItemStack slotItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        if (component instanceof InventoryLinkComponent link) {
            if (!link.allowInteraction()) {
                event.setCancelled(true);
            }
            return;
        }

        event.setCancelled(true);

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
            }
        }

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
        if (clickType.isShiftClick() && event.getAction().toString().contains("MOVE_TO")) {
            event.setCancelled(true);
        }
    }

    /**
     * Handles drag events.
     *
     * @param event the drag event
     */
    public void handleDrag(InventoryDragEvent event) {
        Set<Integer> rawSlots = event.getRawSlots();
        Set<Integer> guiSlots = new java.util.HashSet<>();
        Set<Integer> linkSlots = new java.util.HashSet<>();

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

        if (!guiSlots.isEmpty()) {
            event.setCancelled(true);
            return;
        }

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

    /**
     * Gets the player.
     *
     * @return the player
     */
    @Override
    @NotNull
    public Player getPlayer() {
        return player;
    }

    /**
     * Gets the title.
     *
     * @return the title
     */
    @Override
    @NotNull
    public Component getTitle() {
        return title;
    }

    /**
     * Gets the size.
     *
     * @return the size
     */
    @Override
    public int getSize() {
        return size;
    }

    /**
     * Gets the configuration.
     *
     * @return the configuration
     */
    public GuiConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Gets a component at a slot.
     *
     * @param slot the slot
     * @return the component
     */
    @Override
    @Nullable
    public GloomComponent getComponent(int slot) {
        if (slot < 0 || slot >= size) {
            return null;
        }
        return slotToComponent[slot];
    }

    /**
     * Gets the component index at a slot.
     *
     * @param slot the slot
     * @return the index
     */
    @Override
    public int getComponentIndex(int slot) {
        if (slot < 0 || slot >= size) {
            return 0;
        }
        return slotToComponentIndex[slot];
    }

    /**
     * Gets the layout map.
     *
     * @return the layout
     */
    @Override
    @NotNull
    public Map<Integer, GloomComponent> getLayout() {
        return Collections.unmodifiableMap(components);
    }

    /**
     * Gets the frozen property.
     *
     * @return the property
     */
    public Property<Boolean> getFrozen() {
        return frozen;
    }

    /**
     * Checks if the GUI is frozen.
     *
     * @return true if frozen
     */
    @Override
    public boolean isFrozen() {
        return frozen.get();
    }

    /**
     * Sets the frozen state.
     *
     * @param frozen the state
     */
    @Override
    public void setFrozen(boolean frozen) {
        this.frozen.set(frozen);
    }

    /**
     * Gets the background item.
     *
     * @return the background
     */
    @Override
    @Nullable
    public ItemStack getBackground() {
        return background.get();
    }

    /**
     * Sets the background item.
     *
     * @param background the background
     */
    @Override
    public void setBackground(@Nullable ItemStack background) {
        this.background.set(background);
    }

    /**
     * Gets the slot priority.
     *
     * @return the priority
     */
    @NotNull
    public SlotPriority getSlotPriority() {
        return slotPriority;
    }

    /**
     * Sets the slot priority.
     *
     * @param priority the priority
     */
    public void setSlotPriority(@NotNull SlotPriority priority) {
        this.slotPriority = priority;
    }

    /**
     * Sets a slot element.
     *
     * @param slot    the slot
     * @param element the element
     */
    @Override
    public void setSlotElement(int slot, @NotNull SlotElement element) {
        slotElements.put(slot, element);
        markDirty(slot);
    }

    /**
     * Gets a slot element.
     *
     * @param slot the slot
     * @return the element
     */
    @Override
    @Nullable
    public SlotElement getSlotElement(int slot) {
        return slotElements.get(slot);
    }

    /**
     * Removes a slot element.
     *
     * @param slot the slot
     */
    @Override
    public void removeSlotElement(int slot) {
        slotElements.remove(slot);
        markDirty(slot);
    }

    /**
     * Renders a slot.
     *
     * @param slot the slot
     * @return the item stack
     */
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

    /**
     * Adds an observer.
     *
     * @param who  the observer
     * @param what the subject
     * @param how  notification hint
     */
    @Override
    public void addObserver(@NotNull Observer who, int what, int how) {
        observers.computeIfAbsent(what, k -> ConcurrentHashMap.newKeySet())
                .add(new ObserverEntry(who, how));
    }

    /**
     * Removes an observer.
     *
     * @param who  the observer
     * @param what the subject
     * @param how  notification hint
     */
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

    /**
     * Removes all observers for an object.
     *
     * @param who the observer
     */
    @Override
    public void removeAllObservers(@NotNull Observer who) {
        observers.values().forEach(set ->
                set.removeIf(entry -> entry.observer() == who)
        );
        observers.entrySet().removeIf(entry -> entry.getValue().isEmpty());
    }

    /**
     * Notifies observers of a slot change.
     *
     * @param slot the slot
     */
    public void notifySlotObservers(int slot) {
        Set<ObserverEntry> slotObservers = observers.get(slot);
        if (slotObservers != null) {
            slotObservers.forEach(entry -> entry.observer().notifyUpdate(entry.how()));
        }
    }

    /**
     * Notifies all observers.
     */
    public void notifyAllObservers() {
        for (int slot = 0; slot < size; slot++) {
            notifySlotObservers(slot);
        }
    }

    /**
     * Gets the update period for a subject.
     *
     * @param what the subject
     * @return the period
     */
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
