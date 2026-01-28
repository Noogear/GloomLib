package gloomlib.gui.interaction;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.DragType;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

/**
 * Context for drag interactions across multiple slots.
 *
 * @param player    the player performing the drag
 * @param cursor    the cursor item after the drag
 * @param oldCursor the cursor item before the drag
 * @param type      the drag type (SINGLE or EVEN)
 * @param newItems  the map of slot indices to new items
 */
public record DragContext(
        Player player,
        ItemStack cursor,
        ItemStack oldCursor,
        DragType type,
        Map<Integer, ItemStack> newItems
) {
}