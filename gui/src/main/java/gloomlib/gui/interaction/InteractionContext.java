package gloomlib.gui.interaction;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public record InteractionContext(
        @NotNull Player player,
        @NotNull ClickType clickType,
        @NotNull InventoryAction action,
        int slot,
        @Nullable ItemStack item,
        int componentIndex
) {

    public boolean isLeftClick() {
        return clickType.isLeftClick();
    }

    public boolean isRightClick() {
        return clickType.isRightClick();
    }

    public boolean isShiftClick() {
        return clickType.isShiftClick();
    }

    public boolean isNumberKey() {
        return clickType == ClickType.NUMBER_KEY;
    }

    public boolean isDrop() {
        return clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP;
    }
}