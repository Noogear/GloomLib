package gloomlib.gui.api;

import gloomlib.gui.GloomGuiManager;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.config.GuiConfiguration;
import gloomlib.gui.holder.GuiHolder;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.util.DirtyTracker;
import gloomlib.gui.util.GuiSecurity;
import gloomlib.gui.window.Window;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.Consumer;

public class GloomGui implements GuiHolder {

    private final Player viewer;
    private final Component title;
    private final GuiConfiguration config;
    private final Consumer<InventoryCloseEvent> closeAction;
    private final Map<Integer, GloomComponent> activeSlots = new HashMap<>();
    private final Map<Integer, Integer> slotIndices = new HashMap<>();
    private final List<GloomComponent> tickableComponents = new ArrayList<>();
    private final DirtyTracker dirtyTracker;
    private Window window;
    private boolean isDestroyed = false;

    public GloomGui(Player viewer, Component title, int size, GuiConfiguration config,
                    Consumer<InventoryCloseEvent> closeAction,
                    Map<Integer, GloomComponent> layout,
                    Map<Integer, Integer> indices) {
        this.viewer = viewer;
        this.title = title;
        this.config = config;
        this.closeAction = closeAction;
        this.activeSlots.putAll(layout);
        this.slotIndices.putAll(indices);
        this.dirtyTracker = new DirtyTracker(size);

        if (config.enableAnimations()) {
            Set<GloomComponent> processed = new HashSet<>();
            for (GloomComponent comp : layout.values()) {
                if (processed.add(comp) && comp.getTickRate() > 0) {
                    tickableComponents.add(comp);
                }
            }
        }
    }

    public void bindToWindow(Window window) {
        this.window = window;

        dirtyTracker.markGlobal();
        performRedraw();

        if (config.updateStrategy() == GuiConfiguration.UpdateStrategy.PERIODIC) {
            GloomGuiManager.register(window, config.tickRate());
        }
    }

    public void tick() {
        if (isDestroyed || !config.enableAnimations()) return;

        boolean visualChanged = false;
        for (GloomComponent comp : tickableComponents) {
            if (comp.onTick()) {
                markComponentDirty(comp);
                visualChanged = true;
            }
        }

        if (visualChanged) {
            performRedraw();
        }
    }

    public void requestRedraw() {
        dirtyTracker.markGlobal();
        if (config.updateStrategy() == GuiConfiguration.UpdateStrategy.REACTIVE) {
            if (Bukkit.isPrimaryThread()) {
                performRedraw();
            } else {
                Bukkit.getScheduler().runTask(GloomGuiManager.getPlugin(), this::performRedraw);
            }
        }
    }

    private void performRedraw() {
        if (isDestroyed || window == null) return;

        Inventory inv = ((org.bukkit.inventory.InventoryHolder) window).getInventory();

        if (dirtyTracker.isGlobalDirty()) {
            activeSlots.forEach((slot, comp) -> updateSlot(inv, slot, comp));
            dirtyTracker.popDirtySlots();
            return;
        }

        BitSet dirty = dirtyTracker.popDirtySlots();
        if (dirty.isEmpty()) return;

        for (int slot = dirty.nextSetBit(0); slot >= 0; slot = dirty.nextSetBit(slot + 1)) {
            GloomComponent comp = activeSlots.get(slot);
            if (comp != null) {
                updateSlot(inv, slot, comp);
            } else {
                inv.setItem(slot, null);
            }
        }
    }

    private void updateSlot(Inventory inv, int slot, GloomComponent comp) {
        if (!comp.canInteract()) {
            int index = slotIndices.getOrDefault(slot, 0);
            ItemStack newItem = comp.render(index);

            if (newItem != null && !newItem.getType().isAir()) {
                GuiSecurity.markAsGuiItem(newItem);
            }

            ItemStack current = inv.getItem(slot);
            if (current == null || !current.isSimilar(newItem) || current.getAmount() != newItem.getAmount()) {
                inv.setItem(slot, newItem);
            }
        }
    }

    private void markComponentDirty(GloomComponent target) {
        activeSlots.forEach((slot, comp) -> {
            if (comp == target) {
                dirtyTracker.mark(slot);
            }
        });
    }

    public void destroy() {
        isDestroyed = true;
        if (config.updateStrategy() == GuiConfiguration.UpdateStrategy.PERIODIC && window != null) {
            GloomGuiManager.unregister(window);
        }
        activeSlots.clear();
        tickableComponents.clear();
    }

    public void handleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || window == null) return;

        Inventory guiInventory = ((org.bukkit.inventory.InventoryHolder) window).getInventory();

        if (event.getClickedInventory().equals(guiInventory)) {
            int slot = event.getSlot();
            GloomComponent comp = activeSlots.get(slot);

            if (comp != null) {
                event.setCancelled(!comp.canInteract());

                int index = slotIndices.getOrDefault(slot, 0);
                InteractionContext ctx = new InteractionContext(
                        (Player) event.getWhoClicked(),
                        event.getClick(),
                        event.getAction(),
                        slot,
                        event.getCurrentItem(),
                        index
                );

                comp.onClick(ctx);

                if (!comp.canInteract()) {
                    requestRedraw();
                }
            } else {
                event.setCancelled(true);
            }
        } else {
            event.setCancelled(event.isShiftClick());
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        if (window == null) return;
        Inventory guiInventory = ((org.bukkit.inventory.InventoryHolder) window).getInventory();
        int size = guiInventory.getSize();

        boolean involvesReadOnly = event.getRawSlots().stream()
                .filter(slot -> slot < size)
                .anyMatch(slot -> {
                    GloomComponent comp = activeSlots.get(slot);
                    return comp == null || !comp.canInteract();
                });

        if (involvesReadOnly) {
            event.setCancelled(true);
        }
    }

    public void handleClose(InventoryCloseEvent event) {
        if (closeAction != null) {
            closeAction.accept(event);
        }
        destroy();
    }

    @Override
    public @NotNull Inventory getInventory() {
        if (window instanceof org.bukkit.inventory.InventoryHolder h) {
            return h.getInventory();
        }
        throw new IllegalStateException("GloomGui is not bound to a Bukkit InventoryHolder Window");
    }
}