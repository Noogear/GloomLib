package gloomlib.gui.util;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * GUI item operation utility class.
 * Provides common operations like stacking, adding, and removing items for interaction handler reuse.
 */
public final class GuiItemUtils {

    private GuiItemUtils() {
    }

    /**
     * Checks if an item is empty.
     *
     * @param item the item to check
     * @return {@code true} if the item is null, AIR, or has amount &lt;= 0
     */
    public static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }

    /**
     * Checks if two items can stack together.
     *
     * @param a the first item
     * @param b the second item
     * @return true if the items can stack
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
     * @return the maximum stack size
     */
    public static int getMaxStackSize(@NotNull ItemStack item) {
        return item.getMaxStackSize();
    }

    /**
     * Creates an empty item (for clearing slots).
     *
     * @return an empty ItemStack (AIR)
     */
    @NotNull
    public static ItemStack createEmpty() {
        return new ItemStack(Material.AIR);
    }

    /**
     * Safely clones an item (handles null).
     *
     * @param item the item to clone
     * @return the cloned item, or null if the item is empty
     */
    @Nullable
    public static ItemStack cloneSafe(@Nullable ItemStack item) {
        return isEmpty(item) ? null : item.clone();
    }

    /**
     * Attempts to add an item to a slot.
     *
     * @param slotItem the current item in the slot
     * @param toAdd the item to add
     * @return the add result [new slot item, remaining item]
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
     * @param slotItem the current item in the slot
     * @param amount the amount to remove
     * @return the remove result [new slot item, removed item]
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
     * Picks up half of the items (rounds up).
     *
     * @param slotItem the current item in the slot
     * @return the remove result with half picked up
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
     * @return the swap result [b, a]
     */
    @NotNull
    public static SwapResult swap(@Nullable ItemStack a, @Nullable ItemStack b) {
        return new SwapResult(cloneSafe(b), cloneSafe(a));
    }

    /**
     * Finds the first stackable slot in an inventory.
     *
     * @param inventory the inventory
     * @param item the item to stack
     * @param start the start slot (inclusive)
     * @param end the end slot (exclusive)
     * @return the slot index, or -1 if not found
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
     * @param start the start slot (inclusive)
     * @param end the end slot (exclusive)
     * @return the slot index, or -1 if not found
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
     * Add result.
     *
     * @param newSlotItem the new item in the slot
     * @param remaining the remaining item that could not be added
     */
    public record AddResult(@Nullable ItemStack newSlotItem, @Nullable ItemStack remaining) {
        public boolean hasRemaining() {
            return !isEmpty(remaining);
        }
    }

    /**
     * Remove result.
     *
     * @param newSlotItem the new item in the slot after removal
     * @param removed the removed item
     */
    public record RemoveResult(@Nullable ItemStack newSlotItem, @Nullable ItemStack removed) {
        public boolean wasRemoved() {
            return !isEmpty(removed);
        }
    }

    /**
     * Swap result.
     *
     * @param newA the new value for a (was b)
     * @param newB the new value for b (was a)
     */
    public record SwapResult(@Nullable ItemStack newA, @Nullable ItemStack newB) {
    }
}
