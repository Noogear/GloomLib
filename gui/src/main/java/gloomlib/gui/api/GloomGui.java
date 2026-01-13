package gloomlib.gui.api;

import gloomlib.gui.GloomGuiManager;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.holder.GuiHolder;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.util.GuiSecurity;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class GloomGui implements GuiHolder {

    private final Player viewer;
    private final Inventory inventory;
    private Component title;
    private final String[] structure;
    private final Map<Character, GloomComponent> charComponents;
    private final Map<Integer, GloomComponent> slotComponents;
    private final Map<Integer, Integer> slotIndices = new HashMap<>();
    private final Map<Integer, GloomComponent> activeSlots = new HashMap<>();
    private boolean isDestroyed = false;

    public GloomGui(Player viewer, Component title, int rows, InventoryType type, String[] structure,
                    Map<Character, GloomComponent> charComponents,
                    Map<Integer, GloomComponent> slotComponents) {
        this.viewer = viewer;
        this.title = title;
        this.structure = structure;
        this.charComponents = charComponents;
        this.slotComponents = slotComponents;

        if (type == InventoryType.CHEST) {
            this.inventory = Bukkit.createInventory(this, rows * 9, title);
        } else {
            this.inventory = Bukkit.createInventory(this, type, title);
        }

        computeLayout();
        redraw();
    }


    public void redraw() {
        if (isDestroyed) return;
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(GloomGuiManager.getPlugin(), this::redraw);
            return;
        }

        activeSlots.forEach((slot, comp) -> {
            int index = slotIndices.getOrDefault(slot, 0);
            ItemStack item = comp.render(index);

            if (item != null && !item.getType().isAir()) {
                GuiSecurity.markAsGuiItem(item);
            }

            inventory.setItem(slot, item);
        });
    }

    private void computeLayout() {
        activeSlots.clear();
        slotIndices.clear();

        Map<GloomComponent, Integer> componentCounters = new HashMap<>();

        int width = 9;
        InventoryType type = inventory.getType();
        if(type == InventoryType.HOPPER) width = 5;
        else if(type == InventoryType.DISPENSER || type == InventoryType.DROPPER || type == InventoryType.WORKBENCH) width = 3;

        if (structure != null) {
            for (int row = 0; row < structure.length; row++) {
                String rowStr = structure[row];
                char[] chars = rowStr.replace(" ", "").toCharArray();
                for (int col = 0; col < chars.length && col < width; col++) {
                    char key = chars[col];
                    GloomComponent comp = charComponents.get(key);
                    if (comp != null) {
                        int slot = row * width + col;
                        int index = componentCounters.getOrDefault(comp, 0);
                        activeSlots.put(slot, comp);
                        slotIndices.put(slot, index);
                        componentCounters.put(comp, index + 1);
                    }
                }
            }
        }
        slotComponents.forEach((slot, comp) -> {
            activeSlots.put(slot, comp);
            slotIndices.put(slot, 0);
        });
    }

    public void tick() {
        if (isDestroyed) return;
        boolean needsUpdate = false;
        for (GloomComponent comp : new java.util.HashSet<>(activeSlots.values())) {
            if (comp.onTick()) needsUpdate = true;
        }
        if (needsUpdate) redraw();
    }

    public void open() {
        GloomGuiManager.track(this);
        viewer.openInventory(inventory);
    }

    public void destroy() {
        if (isDestroyed) return;
        isDestroyed = true;
        GloomGuiManager.untrack(this);
        new java.util.HashSet<>(activeSlots.values()).forEach(GloomComponent::dispose);
        activeSlots.clear();
    }

    public Player getViewer() { return viewer; }

    public void handleClick(InventoryClickEvent event) {
        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;
        if (event.getClickedInventory() != inventory) {
            if (event.isShiftClick()) event.setCancelled(true);
            return;
        }

        int slot = event.getSlot();
        GloomComponent comp = activeSlots.get(slot);

        if (comp != null) {
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
            redraw();
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        boolean affectsGui = event.getRawSlots().stream()
                .anyMatch(slot -> slot < inventory.getSize());
        if (affectsGui) event.setCancelled(true);
    }

    @Override
    public @NotNull Inventory getInventory() { return inventory; }
}