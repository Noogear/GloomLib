package gloomlib.gui.window;

import gloomlib.gui.api.GloomGui;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Core window interface for GUI inventory management.
 */
public interface Window {
    /**
     * Opens the window for the viewer.
     */
    void open();

    /**
     * Closes the window.
     */
    void close();

    /**
     * Gets the associated GUI instance.
     *
     * @return the GUI
     */
    GloomGui getGui();

    /**
     * Gets the player viewing this window.
     *
     * @return the viewer
     */
    Player getViewer();

    /**
     * Checks if the window is closed.
     *
     * @return true if closed
     */
    boolean isClosed();

    /**
     * Handles the inventory close event.
     *
     * @param event the close event
     */
    void handleClose(InventoryCloseEvent event);

    /**
     * Performs periodic updates while the window is open.
     */
    void tick();
}