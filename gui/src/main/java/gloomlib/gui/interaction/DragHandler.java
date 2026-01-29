package gloomlib.gui.interaction;

import org.bukkit.Material;
import org.bukkit.event.inventory.DragType;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Handler for drag interactions across multiple inventory slots.
 */
public final class DragHandler {

    private DragHandler() {
    }

    /**
     * Handles a drag interaction.
     *
     * @param dragType the drag type
     * @param draggedItem the item being dragged
     * @param slots the set of slot indices
     * @param getSlotItem function to retrieve current slot items
     * @return the drag result
     */
    @NotNull
    public static DragResult handleDrag(
            @NotNull DragType dragType,
            @NotNull ItemStack draggedItem,
            @NotNull Set<Integer> slots,
            @NotNull java.util.function.IntFunction<ItemStack> getSlotItem
    ) {
        if (draggedItem.isEmpty() || slots.isEmpty()) {
            return new DragResult(draggedItem, Map.of());
        }

        return switch (dragType) {
            case EVEN -> distributeEvenly(draggedItem, slots, getSlotItem);
            case SINGLE -> distributeSingle(draggedItem, slots, getSlotItem);
        };
    }

    private static DragResult distributeEvenly(
            ItemStack draggedItem,
            Set<Integer> slots,
            java.util.function.IntFunction<ItemStack> getSlotItem
    ) {
        Map<Integer, ItemStack> updated = new HashMap<>();
        int totalAmount = draggedItem.getAmount();
        int slotCount = slots.size();
        int perSlot = Math.max(1, totalAmount / slotCount);
        int remaining = totalAmount;

        for (int slot : slots) {
            if (remaining <= 0) break;

            ItemStack current = getSlotItem.apply(slot);
            int toAdd = Math.min(perSlot, remaining);

            if (current == null || current.getType() == Material.AIR) {
                ItemStack newStack = draggedItem.clone();
                newStack.setAmount(toAdd);
                updated.put(slot, newStack);
                remaining -= toAdd;
            } else if (current.isSimilar(draggedItem)) {
                int maxStack = current.getMaxStackSize();
                int currentAmount = current.getAmount();
                int canAdd = Math.min(toAdd, maxStack - currentAmount);

                if (canAdd > 0) {
                    ItemStack newStack = current.clone();
                    newStack.setAmount(currentAmount + canAdd);
                    updated.put(slot, newStack);
                    remaining -= canAdd;
                }
            }
        }

        ItemStack remainingStack = draggedItem.clone();
        remainingStack.setAmount(remaining);

        return new DragResult(remainingStack, updated);
    }

    private static DragResult distributeSingle(
            ItemStack draggedItem,
            Set<Integer> slots,
            java.util.function.IntFunction<ItemStack> getSlotItem
    ) {
        Map<Integer, ItemStack> updated = new HashMap<>();
        int remaining = draggedItem.getAmount();

        for (int slot : slots) {
            if (remaining <= 0) break;

            ItemStack current = getSlotItem.apply(slot);

            if (current == null || current.getType() == Material.AIR) {
                ItemStack newStack = draggedItem.clone();
                newStack.setAmount(1);
                updated.put(slot, newStack);
                remaining--;
            } else if (current.isSimilar(draggedItem)) {
                int maxStack = current.getMaxStackSize();
                int currentAmount = current.getAmount();

                if (currentAmount < maxStack) {
                    ItemStack newStack = current.clone();
                    newStack.setAmount(currentAmount + 1);
                    updated.put(slot, newStack);
                    remaining--;
                }
            }
        }

        ItemStack remainingStack = draggedItem.clone();
        remainingStack.setAmount(remaining);

        return new DragResult(remainingStack, updated);
    }

    /**
     * Calculates a preview of the distribution.
     *
     * @param dragType the drag type
     * @param totalAmount the total amount
     * @param slots the set of slot indices
     * @return a map of distributions
     */
    @NotNull
    public static Map<Integer, Integer> calculatePreview(
            @NotNull DragType dragType,
            int totalAmount,
            @NotNull Set<Integer> slots
    ) {
        if (totalAmount <= 0 || slots.isEmpty()) {
            return Map.of();
        }

        Map<Integer, Integer> preview = new HashMap<>();

        return switch (dragType) {
            case EVEN -> {
                int perSlot = Math.max(1, totalAmount / slots.size());
                int remaining = totalAmount;
                for (int slot : slots) {
                    int amount = Math.min(perSlot, remaining);
                    preview.put(slot, amount);
                    remaining -= amount;
                    if (remaining <= 0) break;
                }
                yield preview;
            }
            case SINGLE -> {
                int remaining = totalAmount;
                for (int slot : slots) {
                    preview.put(slot, 1);
                    remaining--;
                    if (remaining <= 0) break;
                }
                yield preview;
            }
        };
    }

    /**
     * Result of a drag operation.
     *
     * @param remaining the remaining item on the cursor
     * @param updatedSlots the map of updated slots
     */
    public record DragResult(
            @NotNull ItemStack remaining,
            @NotNull Map<Integer, ItemStack> updatedSlots
    ) {
    }
}
