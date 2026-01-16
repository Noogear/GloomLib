package gloomlib.gui.interaction;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.DragType;
import org.bukkit.inventory.ItemStack;

import java.util.Map;

public record DragContext(
        Player player,
        ItemStack cursor,
        ItemStack oldCursor,
        DragType type,
        Map<Integer, ItemStack> newItems
) {
}