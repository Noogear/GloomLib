package gloomlib.gui.listener;

import gloomlib.gui.GloomGuiManager;
import gloomlib.gui.window.AbstractWindow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public class GloomGuiListener implements Listener {

    private final GloomGuiManager manager;

    public GloomGuiListener(GloomGuiManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder(false) instanceof AbstractWindow window) {
            window.getGui().handleClick(event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof AbstractWindow window) {
            window.getGui().handleDrag(event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof AbstractWindow window) {
            window.handleClose(event);
        }
    }
}