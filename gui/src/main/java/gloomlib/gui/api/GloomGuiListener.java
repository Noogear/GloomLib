package gloomlib.gui.api;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/**
 * Listens for Bukkit inventory events and delegates them to GloomGui.
 */
public class GloomGuiListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder(false);

        if (holder instanceof GloomGui gui) {

            gui.handleInteraction(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder(false);

        if (holder instanceof GloomGui gui) {
            gui.handleDrag(event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder(false);

        if (holder instanceof GloomGui gui) {
            // Stop animations/tickers when closed to save resources
            gui.close();
        }
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        // Optional: Hooks for open events if needed in the future
    }
}