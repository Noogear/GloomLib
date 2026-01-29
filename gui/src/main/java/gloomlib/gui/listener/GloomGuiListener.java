package gloomlib.gui.listener;

import gloomlib.gui.GloomGuiManager;
import gloomlib.gui.window.AbstractWindow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/**
 * Listener for Bukkit inventory events to delegate to GUI windows.
 */
public class GloomGuiListener implements Listener {

    private final GloomGuiManager manager;

    /**
     * Constructs the listener.
     *
     * @param manager the GUI manager
     */
    public GloomGuiListener(GloomGuiManager manager) {
        this.manager = manager;
    }

    /**
     * Handles inventory click events.
     *
     * @param event the click event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder(false) instanceof AbstractWindow window) {
            window.getGui().handleClick(event);
        }
    }

    /**
     * Handles inventory drag events.
     *
     * @param event the drag event
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder(false) instanceof AbstractWindow window) {
            window.getGui().handleDrag(event);
        }
    }

    /**
     * Handles inventory close events.
     *
     * @param event the close event
     */
    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder(false) instanceof AbstractWindow window) {
            window.handleClose(event);
        }
    }
}