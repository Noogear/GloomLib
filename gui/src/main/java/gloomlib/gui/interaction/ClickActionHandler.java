package gloomlib.gui.interaction;

import gloomlib.gui.util.BundleUtils;
import gloomlib.gui.util.GuiItemUtils;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Click action handler implementing complete Minecraft vanilla click interaction logic.
 * <p>
 * Supports:
 * - Left/right click (pick, place, stack, swap)
 * - Shift+click cross-inventory movement (with priority)
 * - Number key hotbar swap
 * - Double-click item collection
 * - Middle-click clone (creative mode)
 * - Offhand swap (F key)
 * - Item drop (Q key)
 * - Bundle support (MC 1.21+)
 */
public final class ClickActionHandler {

    private ClickActionHandler() {
    }

    private record PrioritizedSlot(int slot, int priority) {
    }

    /**
     * Handles left-click interaction.
     * <p>
     * Logic:
     * - Empty cursor + slot has item → pick up all
     * - Cursor has item + empty slot → place all
     * - Similar items → attempt stack
     * - Different items → swap
     * - Bundle special handling (MC 1.21+): insert/extract items
     *
     * @param player the player performing the action
     * @param slotItem the item in the slot
     * @param cursorItem the item on the cursor
     * @return the click result
     */
    @NotNull
    public static ClickResult handleLeftClick(
            @NotNull Player player,
            @Nullable ItemStack slotItem,
            @Nullable ItemStack cursorItem
    ) {
        if (BundleUtils.isBundleSupported() && BundleUtils.isBundle(slotItem) && !GuiItemUtils.isEmpty(cursorItem)) {
            BundleUtils.InsertResult bundleResult = BundleUtils.insertIntoBundle(slotItem, cursorItem);
            return new ClickResult(bundleResult.newBundle(), bundleResult.remaining(), true);
        }

        if (BundleUtils.isBundleSupported() && BundleUtils.isBundle(slotItem) && GuiItemUtils.isEmpty(cursorItem)) {
            BundleUtils.ExtractResult extractResult = BundleUtils.extractFromBundle(slotItem);
            if (extractResult.wasExtracted()) {
                return new ClickResult(extractResult.newBundle(), extractResult.extracted(), true);
            }
            return new ClickResult(null, slotItem.clone(), true);
        }

        if (GuiItemUtils.isEmpty(cursorItem)) {
            if (!GuiItemUtils.isEmpty(slotItem)) {
                return new ClickResult(null, slotItem.clone(), true);
            }
            return ClickResult.noChange();
        }

        if (GuiItemUtils.isEmpty(slotItem)) {
            return new ClickResult(cursorItem.clone(), null, true);
        }

        if (GuiItemUtils.canStackWith(slotItem, cursorItem)) {
            GuiItemUtils.AddResult result = GuiItemUtils.addItem(slotItem, cursorItem);
            return new ClickResult(result.newSlotItem(), result.remaining(), true);
        } else {
            GuiItemUtils.SwapResult swap = GuiItemUtils.swap(slotItem, cursorItem);
            return new ClickResult(swap.newA(), swap.newB(), true);
        }
    }

    /**
     * Handles right-click interaction.
     * <p>
     * Logic:
     * - Empty cursor + slot has item → pick up half (rounded up)
     * - Cursor has item + empty slot → place 1 item
     * - Similar items → add 1 to slot
     * - Different items → swap
     * - Bundle special handling (MC 1.21+): extract first item
     *
     * @param player the player performing the action
     * @param slotItem the item in the slot
     * @param cursorItem the item on the cursor
     * @return the click result
     */
    @NotNull
    public static ClickResult handleRightClick(
            @NotNull Player player,
            @Nullable ItemStack slotItem,
            @Nullable ItemStack cursorItem
    ) {
        if (BundleUtils.isBundleSupported() && BundleUtils.isBundle(slotItem) && GuiItemUtils.isEmpty(cursorItem)) {
            BundleUtils.ExtractResult extractResult = BundleUtils.extractFromBundle(slotItem);
            if (extractResult.wasExtracted()) {
                return new ClickResult(extractResult.newBundle(), extractResult.extracted(), true);
            }
            return new ClickResult(null, slotItem.clone(), true);
        }

        if (GuiItemUtils.isEmpty(cursorItem)) {
            if (!GuiItemUtils.isEmpty(slotItem)) {
                GuiItemUtils.RemoveResult result = GuiItemUtils.pickupHalf(slotItem);
                return new ClickResult(result.newSlotItem(), result.removed(), true);
            }
            return ClickResult.noChange();
        }

        if (GuiItemUtils.isEmpty(slotItem)) {
            ItemStack newSlot = cursorItem.clone();
            newSlot.setAmount(1);
            ItemStack newCursor = cursorItem.clone();
            newCursor.setAmount(cursorItem.getAmount() - 1);
            return new ClickResult(newSlot, GuiItemUtils.isEmpty(newCursor) ? null : newCursor, true);
        }

        if (GuiItemUtils.canStackWith(slotItem, cursorItem)) {
            int currentAmount = slotItem.getAmount();
            int maxStack = slotItem.getMaxStackSize();

            if (currentAmount < maxStack) {
                ItemStack newSlot = slotItem.clone();
                newSlot.setAmount(currentAmount + 1);
                ItemStack newCursor = cursorItem.clone();
                newCursor.setAmount(cursorItem.getAmount() - 1);
                return new ClickResult(newSlot, GuiItemUtils.isEmpty(newCursor) ? null : newCursor, true);
            } else {
                return ClickResult.noChange();
            }
        } else {
            GuiItemUtils.SwapResult swap = GuiItemUtils.swap(slotItem, cursorItem);
            return new ClickResult(swap.newA(), swap.newB(), true);
        }
    }

    /**
     * Handles Shift+Click (quick move to another inventory) - with priority support.
     * <p>
     * Logic:
     * 1. Sort slots by priority
     * 2. Fill high-priority existing stacks first
     * 3. Then fill high-priority empty slots
     *
     * @param slotItem the item in the slot
     * @param targetInventory the target inventory
     * @param startSlot the start slot (inclusive)
     * @param endSlot the end slot (exclusive)
     * @param priority the priority strategy (nullable, uses default if null)
     * @return the shift-click result
     */
    @NotNull
    public static ShiftClickResult handleShiftClickWithPriority(
            @Nullable ItemStack slotItem,
            @NotNull Inventory targetInventory,
            int startSlot,
            int endSlot,
            @Nullable SlotPriority priority
    ) {
        if (GuiItemUtils.isEmpty(slotItem)) {
            return new ShiftClickResult(null, false);
        }

        if (priority == null) {
            return handleShiftClick(slotItem, targetInventory, startSlot, endSlot);
        }

        ItemStack remaining = slotItem.clone();

        java.util.List<PrioritizedSlot> slots = new java.util.ArrayList<>();
        for (int i = startSlot; i < endSlot; i++) {
            int slotPriority = priority.getPriority(i, remaining);
            if (slotPriority > SlotPriority.PRIORITY_NONE) {
                slots.add(new PrioritizedSlot(i, slotPriority));
            }
        }

        slots.sort((a, b) -> Integer.compare(b.priority, a.priority));

        for (PrioritizedSlot ps : slots) {
            if (GuiItemUtils.isEmpty(remaining)) break;
            
            ItemStack targetItem = targetInventory.getItem(ps.slot);
            if (!GuiItemUtils.isEmpty(targetItem) && GuiItemUtils.canStackWith(targetItem, remaining)) {
                GuiItemUtils.AddResult result = GuiItemUtils.addItem(targetItem, remaining);
                targetInventory.setItem(ps.slot, result.newSlotItem());
                remaining = result.remaining();
            }
        }

        for (PrioritizedSlot ps : slots) {
            if (GuiItemUtils.isEmpty(remaining)) break;
            
            if (GuiItemUtils.isEmpty(targetInventory.getItem(ps.slot))) {
                targetInventory.setItem(ps.slot, remaining.clone());
                remaining = null;
                break;
            }
        }

        boolean moved = remaining == null || remaining.getAmount() < slotItem.getAmount();
        return new ShiftClickResult(remaining, moved);
    }

    /**
     * Handles Shift+Click (quick move to another inventory) - simplified version.
     * <p>
     * Logic:
     * 1. Fill existing similar stacks first
     * 2. Then find empty slots
     *
     * @param slotItem the item in the slot
     * @param targetInventory the target inventory
     * @param startSlot the start slot (inclusive)
     * @param endSlot the end slot (exclusive)
     * @return the shift-click result
     */
    @NotNull
    public static ShiftClickResult handleShiftClick(
            @Nullable ItemStack slotItem,
            @NotNull Inventory targetInventory,
            int startSlot,
            int endSlot
    ) {
        if (GuiItemUtils.isEmpty(slotItem)) {
            return new ShiftClickResult(null, false);
        }

        ItemStack remaining = slotItem.clone();

        for (int i = startSlot; i < endSlot && !GuiItemUtils.isEmpty(remaining); i++) {
            ItemStack targetItem = targetInventory.getItem(i);
            if (!GuiItemUtils.isEmpty(targetItem) && GuiItemUtils.canStackWith(targetItem, remaining)) {
                GuiItemUtils.AddResult result = GuiItemUtils.addItem(targetItem, remaining);
                targetInventory.setItem(i, result.newSlotItem());
                remaining = result.remaining();
            }
        }

        for (int i = startSlot; i < endSlot && !GuiItemUtils.isEmpty(remaining); i++) {
            if (GuiItemUtils.isEmpty(targetInventory.getItem(i))) {
                targetInventory.setItem(i, remaining.clone());
                remaining = null;
                break;
            }
        }

        boolean moved = remaining == null || remaining.getAmount() < slotItem.getAmount();
        return new ShiftClickResult(remaining, moved);
    }

    /**
     * Handles number key (1-9) hotbar swap.
     * <p>
     * Swaps the clicked slot with the corresponding hotbar slot item.
     *
     * @param slotItem the item in the slot
     * @param hotbarSlot the hotbar slot (0-8)
     * @param playerInventory the player's inventory
     * @return the number key result
     */
    @NotNull
    public static HotbarSwapResult handleHotbarSwap(
            @Nullable ItemStack slotItem,
            int hotbarSlot,
            @NotNull PlayerInventory playerInventory
    ) {
        if (hotbarSlot < 0 || hotbarSlot > 8) {
            return new HotbarSwapResult(slotItem, false);
        }

        ItemStack hotbarItem = playerInventory.getItem(hotbarSlot);
        GuiItemUtils.SwapResult swap = GuiItemUtils.swap(slotItem, hotbarItem);

        playerInventory.setItem(hotbarSlot, swap.newB());
        return new HotbarSwapResult(swap.newA(), true);
    }

    /**
     * Handles double-click to collect similar items.
     * <p>
     * Collects similar items from all accessible inventories to the cursor.
     *
     * @param cursorItem the cursor item (as template)
     * @param sourceInventory the source inventory (GUI or player inventory)
     * @param startSlot the start slot (inclusive)
     * @param endSlot the end slot (exclusive)
     * @return the double-click result
     */
    @NotNull
    public static DoubleClickResult handleDoubleClick(
            @Nullable ItemStack cursorItem,
            @NotNull Inventory sourceInventory,
            int startSlot,
            int endSlot
    ) {
        if (GuiItemUtils.isEmpty(cursorItem)) {
            return new DoubleClickResult(null, false);
        }

        ItemStack collected = cursorItem.clone();
        int maxStack = collected.getMaxStackSize();
        boolean changed = false;

        for (int i = startSlot; i < endSlot && collected.getAmount() < maxStack; i++) {
            ItemStack sourceItem = sourceInventory.getItem(i);
            if (!GuiItemUtils.isEmpty(sourceItem) && GuiItemUtils.canStackWith(sourceItem, collected)) {
                int needed = maxStack - collected.getAmount();
                int available = sourceItem.getAmount();
                int toTake = Math.min(needed, available);

                collected.setAmount(collected.getAmount() + toTake);

                if (toTake >= available) {
                    sourceInventory.setItem(i, null);
                } else {
                    sourceItem.setAmount(available - toTake);
                    sourceInventory.setItem(i, sourceItem);
                }

                changed = true;
            }
        }

        return new DoubleClickResult(collected, changed);
    }

    /**
     * Handles middle-click (creative mode clone).
     * <p>
     * Only in creative mode, clones the slot item to cursor (full stack).
     *
     * @param player the player
     * @param slotItem the item in the slot
     * @return the middle-click result
     */
    @NotNull
    public static MiddleClickResult handleMiddleClick(
            @NotNull Player player,
            @Nullable ItemStack slotItem
    ) {
        if (player.getGameMode() != GameMode.CREATIVE || GuiItemUtils.isEmpty(slotItem)) {
            return new MiddleClickResult(null, false);
        }

        ItemStack cloned = slotItem.clone();
        cloned.setAmount(cloned.getMaxStackSize());
        return new MiddleClickResult(cloned, true);
    }

    /**
     * Handles offhand swap (F key).
     * <p>
     * Swaps the slot item with the player's offhand item.
     *
     * @param slotItem the item in the slot
     * @param playerInventory the player's inventory
     * @return the offhand swap result
     */
    @NotNull
    public static OffhandSwapResult handleOffhandSwap(
            @Nullable ItemStack slotItem,
            @NotNull PlayerInventory playerInventory
    ) {
        ItemStack offhandItem = playerInventory.getItemInOffHand();
        GuiItemUtils.SwapResult swap = GuiItemUtils.swap(slotItem, offhandItem);

        playerInventory.setItemInOffHand(swap.newB());
        return new OffhandSwapResult(swap.newA(), true);
    }

    /**
     * Handles item drop (Q key or Ctrl+Q).
     * <p>
     * Q key drops 1 item, Ctrl+Q drops all.
     *
     * @param slotItem the item in the slot
     * @param dropAll whether to drop all items
     * @param player the player
     * @return the drop result
     */
    @NotNull
    public static DropResult handleDrop(
            @Nullable ItemStack slotItem,
            boolean dropAll,
            @NotNull Player player
    ) {
        if (GuiItemUtils.isEmpty(slotItem)) {
            return new DropResult(null, false);
        }

        int amount = dropAll ? slotItem.getAmount() : 1;
        GuiItemUtils.RemoveResult result = GuiItemUtils.removeItem(slotItem, amount);

        if (result.wasRemoved()) {
            player.getWorld().dropItemNaturally(player.getLocation(), result.removed());
            return new DropResult(result.newSlotItem(), true);
        }

        return new DropResult(slotItem, false);
    }

    // ==================== Result Classes ====================

    /**
     * Click result (left-click/right-click).
     *
     * @param newSlotItem the new item in the slot
     * @param newCursorItem the new item on the cursor
     * @param changed whether the interaction caused a change
     */
    public record ClickResult(
            @Nullable ItemStack newSlotItem,
            @Nullable ItemStack newCursorItem,
            boolean changed
    ) {
        /**
         * Creates a no-change result (nothing happened).
         *
         * @return a ClickResult with no changes
         */
        public static ClickResult noChange() {
            return new ClickResult(null, null, false);
        }
    }

    /**
     * Shift-click result.
     *
     * @param remaining the remaining item after transfer
     * @param moved whether any items were moved
     */
    public record ShiftClickResult(
            @Nullable ItemStack remaining,
            boolean moved
    ) {
    }

    /**
     * Number key result.
     *
     * @param newSlotItem the new item in the clicked slot
     * @param swapped whether a swap occurred
     */
    public record HotbarSwapResult(
            @Nullable ItemStack newSlotItem,
            boolean swapped
    ) {
    }

    /**
     * Double-click result.
     *
     * @param newCursorItem the new cursor item after collection
     * @param collected whether any items were collected
     */
    public record DoubleClickResult(
            @Nullable ItemStack newCursorItem,
            boolean collected
    ) {
    }

    /**
     * Middle-click result.
     *
     * @param newCursorItem the cloned item (full stack)
     * @param cloned whether the item was cloned
     */
    public record MiddleClickResult(
            @Nullable ItemStack newCursorItem,
            boolean cloned
    ) {
    }

    /**
     * Offhand swap result.
     *
     * @param newSlotItem the new item in the slot
     * @param swapped whether a swap occurred
     */
    public record OffhandSwapResult(
            @Nullable ItemStack newSlotItem,
            boolean swapped
    ) {
    }

    /**
     * Drop result.
     *
     * @param newSlotItem the remaining item in the slot
     * @param dropped whether any items were dropped
     */
    public record DropResult(
            @Nullable ItemStack newSlotItem,
            boolean dropped
    ) {
    }
}
