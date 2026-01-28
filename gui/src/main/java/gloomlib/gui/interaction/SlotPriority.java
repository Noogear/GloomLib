package gloomlib.gui.interaction;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Slot priority interface for controlling where items are placed during Shift+click.
 * References InvUI's priority system, allowing components to define item acceptance priority.
 * Higher priority slots are tried first during Shift+click operations.
 */
public interface SlotPriority {

    /**
     * Lowest priority - does not accept items.
     */
    int PRIORITY_NONE = -1;

    /**
     * Low priority - considered last.
     */
    int PRIORITY_LOW = 0;

    /**
     * Normal priority - default.
     */
    int PRIORITY_NORMAL = 50;

    /**
     * High priority - preferred slots.
     */
    int PRIORITY_HIGH = 100;

    /**
     * Highest priority - most preferred slots.
     */
    int PRIORITY_HIGHEST = 200;

    /**
     * Gets the priority of accepting the specified item in a slot.
     * 
     * @param slot slot index
     * @param item item to place
     * @return priority value; PRIORITY_NONE means not accepting
     */
    int getPriority(int slot, @Nullable ItemStack item);

    /**
     * Checks if the slot accepts the specified item.
     * 
     * @param slot slot index
     * @param item item to place
     * @return whether the item is accepted
     */
    default boolean acceptsItem(int slot, @Nullable ItemStack item) {
        return getPriority(slot, item) > PRIORITY_NONE;
    }

    /**
     * Creates the default priority strategy (all slots at normal priority).
     */
    @NotNull
    static SlotPriority normal() {
        return (slot, item) -> PRIORITY_NORMAL;
    }

    /**
     * Creates a strategy that rejects all items.
     */
    @NotNull
    static SlotPriority none() {
        return (slot, item) -> PRIORITY_NONE;
    }

    /**
     * Creates a high priority strategy.
     */
    @NotNull
    static SlotPriority high() {
        return (slot, item) -> PRIORITY_HIGH;
    }
}
