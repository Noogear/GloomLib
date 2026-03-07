package gloomlib.gui.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Utility class for GUI item operations.
 */
public final class GuiItemUtils {

    private GuiItemUtils() {
    }

    /**
     * Checks if an item is empty.
     *
     * @param item the item to check
     * @return true if empty
     */
    public static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    /**
     * Checks if two items can stack together.
     *
     * @param a the first item
     * @param b the second item
     * @return true if stackable
     */
    public static boolean canStackWith(@Nullable ItemStack a, @Nullable ItemStack b) {
        if (isEmpty(a) || isEmpty(b)) {
            return false;
        }
        return a.isSimilar(b);
    }

    /**
     * Gets the maximum stack size of an item.
     *
     * @param item the item
     * @return the max stack size
     */
    public static int getMaxStackSize(@NotNull ItemStack item) {
        return item.getMaxStackSize();
    }

    /**
     * Creates an empty item.
     *
     * @return an empty ItemStack
     */
    @NotNull
    public static ItemStack createEmpty() {
        return new ItemStack(Material.AIR);
    }

    /**
     * Safely clones an item.
     *
     * @param item the item to clone
     * @return the cloned item
     */
    @Nullable
    public static ItemStack cloneSafe(@Nullable ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    /**
     * Attempts to add an item to a slot.
     *
     * @param slotItem the item in the slot
     * @param toAdd    the item to add
     * @return the add result
     */
    @NotNull
    public static AddResult addItem(@Nullable ItemStack slotItem, @NotNull ItemStack toAdd) {
        if (isEmpty(toAdd)) {
            return new AddResult(slotItem, null);
        }

        if (isEmpty(slotItem)) {
            return new AddResult(toAdd.clone(), null);
        }

        if (!canStackWith(slotItem, toAdd)) {
            return new AddResult(slotItem, toAdd);
        }

        int maxStack = getMaxStackSize(slotItem);
        int currentAmount = slotItem.getAmount();
        int toAddAmount = toAdd.getAmount();
        int totalAmount = currentAmount + toAddAmount;

        if (totalAmount <= maxStack) {
            ItemStack newSlot = slotItem.clone();
            newSlot.setAmount(totalAmount);
            return new AddResult(newSlot, null);
        } else {
            ItemStack newSlot = slotItem.clone();
            newSlot.setAmount(maxStack);
            ItemStack remaining = toAdd.clone();
            remaining.setAmount(totalAmount - maxStack);
            return new AddResult(newSlot, remaining);
        }
    }

    /**
     * Removes items from a slot.
     *
     * @param slotItem the item in the slot
     * @param amount   the amount to remove
     * @return the remove result
     */
    @NotNull
    public static RemoveResult removeItem(@Nullable ItemStack slotItem, int amount) {
        if (isEmpty(slotItem) || amount <= 0) {
            return new RemoveResult(slotItem, null);
        }

        int currentAmount = slotItem.getAmount();

        if (amount >= currentAmount) {
            ItemStack removed = slotItem.clone();
            return new RemoveResult(null, removed);
        } else {
            ItemStack newSlot = slotItem.clone();
            newSlot.setAmount(currentAmount - amount);
            ItemStack removed = slotItem.clone();
            removed.setAmount(amount);
            return new RemoveResult(newSlot, removed);
        }
    }

    /**
     * Picks up half of the items.
     *
     * @param slotItem the item in the slot
     * @return the remove result
     */
    @NotNull
    public static RemoveResult pickupHalf(@Nullable ItemStack slotItem) {
        if (isEmpty(slotItem)) {
            return new RemoveResult(null, null);
        }

        int amount = slotItem.getAmount();
        int half = (amount + 1) / 2;

        return removeItem(slotItem, half);
    }

    /**
     * Swaps two items.
     *
     * @param a the first item
     * @param b the second item
     * @return the swap result
     */
    @NotNull
    public static SwapResult swap(@Nullable ItemStack a, @Nullable ItemStack b) {
        return new SwapResult(cloneSafe(b), cloneSafe(a));
    }

    /**
     * Finds the first stackable slot in an inventory.
     *
     * @param inventory the inventory
     * @param item      the item to stack
     * @param start     the start slot
     * @param end       the end slot
     * @return the slot index
     */
    public static int findFirstStackableSlot(@NotNull Inventory inventory, @NotNull ItemStack item, int start, int end) {
        if (isEmpty(item)) {
            return -1;
        }

        for (int i = start; i < end; i++) {
            ItemStack slotItem = inventory.getItem(i);
            if (!isEmpty(slotItem) && canStackWith(slotItem, item) && slotItem.getAmount() < slotItem.getMaxStackSize()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Finds the first empty slot in an inventory.
     *
     * @param inventory the inventory
     * @param start     the start slot
     * @param end       the end slot
     * @return the slot index
     */
    public static int findFirstEmptySlot(@NotNull Inventory inventory, int start, int end) {
        for (int i = start; i < end; i++) {
            if (isEmpty(inventory.getItem(i))) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Result of an add operation.
     *
     * @param newSlotItem the new item in the slot
     * @param remaining   the remaining item
     */
    public record AddResult(@Nullable ItemStack newSlotItem, @Nullable ItemStack remaining) {
        /**
         * Checks if there are remaining items.
         *
         * @return true if items remain
         */
        public boolean hasRemaining() {
            return !isEmpty(remaining);
        }
    }

    /**
     * Result of a remove operation.
     *
     * @param newSlotItem the new item in the slot
     * @param removed     the removed item
     */
    public record RemoveResult(@Nullable ItemStack newSlotItem, @Nullable ItemStack removed) {
        /**
         * Checks if items were removed.
         *
         * @return true if removed
         */
        public boolean wasRemoved() {
            return !isEmpty(removed);
        }
    }

    /**
     * Result of a swap operation.
     *
     * @param newA the new item for A
     * @param newB the new item for B
     */
    public record SwapResult(@Nullable ItemStack newA, @Nullable ItemStack newB) {
    }
}
