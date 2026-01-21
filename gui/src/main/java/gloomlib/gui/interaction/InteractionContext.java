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

    public boolean isControlDrop() {
        return clickType == ClickType.CONTROL_DROP;
    }

    public boolean isDoubleClick() {
        return clickType == ClickType.DOUBLE_CLICK;
    }

    public boolean isMiddleClick() {
        return clickType == ClickType.MIDDLE;
    }

    public boolean isOffhandSwap() {
        return clickType == ClickType.SWAP_OFFHAND;
    }

    public boolean isOutsideClick() {
        return clickType == ClickType.WINDOW_BORDER_LEFT || clickType == ClickType.WINDOW_BORDER_RIGHT;
    }

    public boolean isAction(InventoryAction... actions) {
        for (InventoryAction a : actions) {
            if (action == a) {
                return true;
            }
        }
        return false;
    }

    public boolean isPlaceAction() {
        return isAction(
                InventoryAction.PLACE_ALL,
                InventoryAction.PLACE_ONE,
                InventoryAction.PLACE_SOME
        );
    }

    public boolean isPickupAction() {
        return isAction(
                InventoryAction.PICKUP_ALL,
                InventoryAction.PICKUP_HALF,
                InventoryAction.PICKUP_ONE,
                InventoryAction.PICKUP_SOME
        );
    }

    public boolean isMoveToOtherInventory() {
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY;
    }

    public boolean isSwapAction() {
        return isAction(
                InventoryAction.HOTBAR_SWAP,
                InventoryAction.SWAP_WITH_CURSOR
        );
    }

    public boolean isCloneAction() {
        return action == InventoryAction.CLONE_STACK;
    }

    public boolean involvesCursor() {
        return isAction(
                InventoryAction.SWAP_WITH_CURSOR,
                InventoryAction.PLACE_ALL,
                InventoryAction.PLACE_ONE,
                InventoryAction.PLACE_SOME,
                InventoryAction.PICKUP_ALL,
                InventoryAction.PICKUP_HALF,
                InventoryAction.PICKUP_ONE,
                InventoryAction.PICKUP_SOME
        );
    }

    public String getDescription() {
        return String.format("InteractionContext{player=%s, click=%s, action=%s, slot=%d, index=%d}",
                player.getName(), clickType, action, slot, componentIndex);
    }

    public boolean isBundleInteraction() {
        if (item != null && isRightClick()) {
            return item.getType().name().contains("BUNDLE");
        }
        return false;
    }

    public boolean isDoubleClickCollect() {
        return isDoubleClick() && item != null && !item.getType().isAir();
    }

    public boolean isDragOperation() {
        return clickType == ClickType.LEFT || clickType == ClickType.RIGHT;
    }

    public boolean shouldPreventItemMovement() {
        return isMoveToOtherInventory() || isSwapAction() || isOffhandSwap();
    }

    public boolean isValidGuiInteraction() {
        return !isOutsideClick() && action != InventoryAction.NOTHING;
    }
}
