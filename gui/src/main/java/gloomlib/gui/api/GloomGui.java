package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.component.builtin.PagedComponent;
import gloomlib.gui.holder.GuiHolder;
import gloomlib.gui.interaction.InteractionContext;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The core GUI implementation.
 * Handles rendering, interaction dispatching, and thread safety.
 */
public class GloomGui implements GuiHolder {

    private final Inventory inventory;
    private final Map<Integer, GloomComponent> slotMap = new ConcurrentHashMap<>();
    private final Plugin plugin;
    private final Player player;
    private BukkitTask tickerTask;

    public GloomGui(Plugin plugin, Player player, String title, int rows) {
        this.plugin = plugin;
        this.player = player;
        // Since GloomGui implements GuiHolder (which extends InventoryHolder),
        // we pass 'this' as the owner.
        this.inventory = Bukkit.createInventory(this, rows * 9, net.kyori.adventure.text.Component.text(title));
    }

    public void open() {
        player.openInventory(inventory);
        startTicker();
        redraw();
    }

    public void close() {
        if (tickerTask != null && !tickerTask.isCancelled()) {
            tickerTask.cancel();
        }
    }

    private void startTicker() {
        // Run every tick for animations
        this.tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            boolean needsRedraw = false;
            for (GloomComponent comp : slotMap.values()) {
                comp.tick();
                // Check if component needs update (simplified for brevity)
                if (comp instanceof gloomlib.gui.component.builtin.AnimatedComponent) {
                    needsRedraw = true;
                }
            }
            if (needsRedraw) redraw();
        }, 1L, 1L);
    }

    /**
     * Thread-safe redraw method.
     */
    public void redraw() {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, this::redraw);
            return;
        }

        // Clear only GUI slots
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, null);
        }

        slotMap.forEach((slot, component) -> {
            if (slot >= inventory.getSize()) return;

            if (component instanceof PagedComponent) {
                // Simplified rendering for PagedComponent.
                inventory.setItem(slot, component.render());
            } else {
                inventory.setItem(slot, component.render());
            }
        });

        player.updateInventory();
    }

    public void setComponent(int slot, GloomComponent component) {
        component.setParent(this);
        slotMap.put(slot, component);
    }

    public void handleInteraction(InventoryClickEvent event) {
        if (event.getClickedInventory() != inventory) {
            return;
        }

        event.setCancelled(true); // Default deny

        int slot = event.getSlot();
        if (slotMap.containsKey(slot)) {
            GloomComponent component = slotMap.get(slot);
            InteractionContext context = new InteractionContext(event, player);

            try {
                component.handleClick(context);
            } catch (Exception e) {
                plugin.getLogger().severe("Error handling GUI click at slot " + slot);
                e.printStackTrace();
            }
        }
    }

    public void handleDrag(InventoryDragEvent event) {
        // Smart Dragging Protection
        boolean involvesGui = event.getRawSlots().stream()
                .anyMatch(slot -> slot < inventory.getSize());

        if (involvesGui) {
            event.setCancelled(true);
        }
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}