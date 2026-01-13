package gloomlib.gui.interaction;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

/**
 * Wraps context about a click interaction.
 */
public record InteractionContext(InventoryClickEvent event, Player player) {

    public boolean isLeftClick() {
        return event.isLeftClick();
    }

    public boolean isRightClick() {
        return event.isRightClick();
    }

    public boolean isShiftClick() {
        return event.isShiftClick();
    }

    public void setCancelled(boolean cancelled) {
        event.setCancelled(cancelled);
    }

    /**
     * Updates the GUI if necessary (wrapper around standard Bukkit behavior)
     */
    public void refresh() {
        // GloomGui typically handles auto-refresh via ReactiveState,
        // but explicit refresh might be needed for non-reactive logic.
        // This would require holding a reference to GloomGui here or firing an event.
    }
}