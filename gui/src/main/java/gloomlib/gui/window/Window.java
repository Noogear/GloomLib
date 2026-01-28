package gloomlib.gui.window;

import gloomlib.gui.api.GloomGui;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * Core window interface for GUI inventory management.
 * <p>
 * Represents an inventory window that can be opened, closed, and ticked.
 * Manages the lifecycle of GUI interactions with players.
 */
public interface Window {
    /**
     * Opens the window for the viewer.
     * <p>
     * Creates the inventory, binds components, registers with the manager,
     * and displays the inventory to the player.
     */
    void open();

    /**
     * Closes the window.
     * <p>
     * Triggers the close event and cleanup process.
     */
    void close();

    /**
     * Gets the associated GUI instance.
     *
     * @return the GUI managing this window's components
     */
    GloomGui getGui();

    /**
     * Gets the player viewing this window.
     *
     * @return the viewer player
     */
    Player getViewer();

    /**
     * Checks if the window is closed.
     *
     * @return {@code true} if closed, {@code false} otherwise
     */
    boolean isClosed();

    /**
     * Handles the inventory close event.
     * <p>
     * Unregisters the window, removes observers, and performs cleanup.
     *
     * @param event the inventory close event
     */
    void handleClose(InventoryCloseEvent event);

    /**
     * Called every tick while the window is open.
     * <p>
     * Updates components and performs periodic tasks.
     */
    void tick();
}