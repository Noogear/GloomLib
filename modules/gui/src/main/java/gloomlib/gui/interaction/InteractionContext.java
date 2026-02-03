package gloomlib.gui.interaction;

import gloomlib.gui.navigation.NavigationManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interaction context record containing click interaction information.
 *
 * @param player         the player who triggered the interaction
 * @param clickType      the click type
 * @param action         the inventory action
 * @param slot           the clicked slot
 * @param item           the current item
 * @param componentIndex the component index
 */
public record InteractionContext(
        @NotNull Player player,
        @NotNull ClickType clickType,
        @NotNull InventoryAction action,
        int slot,
        @Nullable ItemStack item,
        int componentIndex
) {

    /**
     * Checks if this is a left click.
     *
     * @return true if left click
     */
    public boolean isLeftClick() {
        return clickType.isLeftClick();
    }

    /**
     * Checks if this is a right click.
     *
     * @return true if right click
     */
    public boolean isRightClick() {
        return clickType.isRightClick();
    }

    /**
     * Checks if this is a shift click.
     *
     * @return true if shift click
     */
    public boolean isShiftClick() {
        return clickType.isShiftClick();
    }

    /**
     * Checks if this is a number key click.
     *
     * @return true if number key click
     */
    public boolean isNumberKey() {
        return clickType == ClickType.NUMBER_KEY;
    }

    /**
     * Checks if this is a drop action.
     *
     * @return true if drop action
     */
    public boolean isDrop() {
        return clickType == ClickType.DROP || clickType == ClickType.CONTROL_DROP;
    }

    /**
     * Checks if this is a control drop.
     *
     * @return true if control drop
     */
    public boolean isControlDrop() {
        return clickType == ClickType.CONTROL_DROP;
    }

    /**
     * Checks if this is a double click.
     *
     * @return true if double click
     */
    public boolean isDoubleClick() {
        return clickType == ClickType.DOUBLE_CLICK;
    }

    /**
     * Checks if this is a middle click.
     *
     * @return true if middle click
     */
    public boolean isMiddleClick() {
        return clickType == ClickType.MIDDLE;
    }

    /**
     * Checks if this is an offhand swap.
     *
     * @return true if offhand swap
     */
    public boolean isOffhandSwap() {
        return clickType == ClickType.SWAP_OFFHAND;
    }

    /**
     * Checks if this is a click outside the window boundary.
     *
     * @return true if click is outside window
     */
    public boolean isOutsideClick() {
        return clickType == ClickType.WINDOW_BORDER_LEFT || clickType == ClickType.WINDOW_BORDER_RIGHT;
    }

    /**
     * Checks if the action matches any of the specified types.
     *
     * @param actions the action types to check
     * @return true if action matches
     */
    public boolean isAction(InventoryAction... actions) {
        for (InventoryAction a : actions) {
            if (action == a) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if this is a place item action.
     *
     * @return true if placing item
     */
    public boolean isPlaceAction() {
        return isAction(
                InventoryAction.PLACE_ALL,
                InventoryAction.PLACE_ONE,
                InventoryAction.PLACE_SOME
        );
    }

    /**
     * Checks if this is a pickup item action.
     *
     * @return true if picking up item
     */
    public boolean isPickupAction() {
        return isAction(
                InventoryAction.PICKUP_ALL,
                InventoryAction.PICKUP_HALF,
                InventoryAction.PICKUP_ONE,
                InventoryAction.PICKUP_SOME
        );
    }

    /**
     * Checks if this is a move to other inventory action.
     *
     * @return true if moving to other inventory
     */
    public boolean isMoveToOtherInventory() {
        return action == InventoryAction.MOVE_TO_OTHER_INVENTORY;
    }

    /**
     * Checks if this is a swap action.
     *
     * @return true if swapping slots
     */
    public boolean isSwapAction() {
        return isAction(
                InventoryAction.HOTBAR_SWAP,
                InventoryAction.SWAP_WITH_CURSOR
        );
    }

    /**
     * Checks if this is a clone action.
     *
     * @return true if cloning
     */
    public boolean isCloneAction() {
        return action == InventoryAction.CLONE_STACK;
    }

    /**
     * Checks if this action involves the cursor item.
     *
     * @return true if involves cursor
     */
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

    /**
     * Gets the interaction description.
     *
     * @return the interaction description string
     */
    public String getDescription() {
        return String.format("InteractionContext{player=%s, click=%s, action=%s, slot=%d, index=%d}",
                player.getName(), clickType, action, slot, componentIndex);
    }

    /**
     * Navigates back to the previous window.
     *
     * @return true if successfully navigated back
     */
    public boolean navigateBack() {
        return NavigationManager.getInstance().back(player);
    }

    /**
     * Checks if navigation back is possible.
     *
     * @return true if navigation history exists
     */
    public boolean canNavigateBack() {
        return NavigationManager.getInstance().hasHistory(player);
    }

    /**
     * Gets the current navigation history depth.
     *
     * @return the number of windows in history
     */
    public int getNavigationDepth() {
        return NavigationManager.getInstance().getDepth(player);
    }

    /**
     * Clears all navigation history.
     */
    public void clearNavigationHistory() {
        NavigationManager.getInstance().clear(player);
    }

    /**
     * Navigates back or closes the current window.
     */
    public void navigateBackOrClose() {
        if (!navigateBack()) {
            player.closeInventory();
        }
    }
}
