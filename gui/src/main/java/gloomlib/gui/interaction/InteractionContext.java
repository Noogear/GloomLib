package gloomlib.gui.interaction;

import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 交互上下文 (Record)。
 * 封裝了點擊事件的相關信息。
 */
public record InteractionContext(
        Player player,
        ClickType clickType,
        InventoryAction action,
        int slot,
        @Nullable ItemStack clickedItem,
        @Nullable Object componentState // 組件內部狀態 (如列表索引)
) {

    public boolean isLeftClick() { return clickType.isLeftClick(); }
    public boolean isRightClick() { return clickType.isRightClick(); }
    public boolean isShiftClick() { return clickType.isShiftClick(); }

    public void reply(Component message) {
        player.sendMessage(message);
    }
}