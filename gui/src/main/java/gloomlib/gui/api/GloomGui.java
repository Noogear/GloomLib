package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.component.builtin.InventoryLinkComponent;
import gloomlib.gui.config.GuiConfiguration;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.window.Observer;
import gloomlib.gui.slot.SlotElement;
import gloomlib.gui.state.MutableProperty;
import gloomlib.gui.state.Property;
import gloomlib.gui.window.AbstractWindow;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

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

    private Inventory inventory;
    private boolean batchUpdateMode = false;

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

        this.tickingSlots.clear();
        this.components.forEach((slot, component) -> {
            if (component.getTickRate() > 0) {
                this.tickingSlots.add(slot);
            }
        });
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
        if (event.getClickedInventory() == event.getInventory()) {
            if (frozen.get()) {
                event.setCancelled(true);
                return;
            }

            int slot = event.getSlot();
            GloomComponent component = getComponent(slot);

            if (component instanceof InventoryLinkComponent link) {
                if (!link.allowInteraction()) {
                    event.setCancelled(true);
                }
                return;
            }

            event.setCancelled(true);

            if (component != null) {
                InteractionContext context = new InteractionContext(
                        event.getWhoClicked() instanceof Player ? (Player) event.getWhoClicked() : player,
                        event.getClick(),
                        event.getAction(),
                        slot,
                        event.getCurrentItem(),
                        getComponentIndex(slot)
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
        } else {
            if (event.isShiftClick() && event.getClickedInventory() != null) {
                int rawSlot = event.getRawSlot();
                if (rawSlot >= size && event.getAction().toString().contains("MOVE_TO")) {
                    event.setCancelled(true);
                }
            }
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        boolean involvesGui = event.getRawSlots().stream()
                .anyMatch(slot -> slot < size);
        if (involvesGui) {
            event.setCancelled(true);
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
        return components.get(slot);
    }

    @Override
    public int getComponentIndex(int slot) {
        return componentIndices.getOrDefault(slot, 0);
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