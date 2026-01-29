package gloomlib.gui.interaction;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Slot priority interface for controlling where items are placed during Shift+click.
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
     * Creates a normal priority strategy.
     *
     * @return a normal priority strategy
     */
    @NotNull
    static SlotPriority normal() {
        return (slot, item) -> PRIORITY_NORMAL;
    }

    /**
     * Creates a priority strategy that rejects all items.
     *
     * @return a none priority strategy
     */
    @NotNull
    static SlotPriority none() {
        return (slot, item) -> PRIORITY_NONE;
    }

    /**
     * Creates a high priority strategy.
     *
     * @return a high priority strategy
     */
    @NotNull
    static SlotPriority high() {
        return (slot, item) -> PRIORITY_HIGH;
    }

    /**
     * Gets the priority of accepting the specified item in a slot.
     *
     * @param slot the slot index
     * @param item the item to place
     * @return the priority value
     */
    int getPriority(int slot, @Nullable ItemStack item);

    /**
     * Checks if the slot accepts the specified item.
     *
     * @param slot the slot index
     * @param item the item to place
     * @return true if the item is accepted
     */
    default boolean acceptsItem(int slot, @Nullable ItemStack item) {
        return getPriority(slot, item) > PRIORITY_NONE;
    }
}
