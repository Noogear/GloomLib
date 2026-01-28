package gloomlib.gui.window;

import org.jetbrains.annotations.NotNull;

/**
 * Observable interface for the observer pattern in GUI contexts.
 */
public interface Observable {

    /**
     * Adds an observer to watch a specific slot.
     *
     * @param who the observer implementation
     * @param what the slot index to observe
     * @param how the notification parameter (slot hint)
     */
    void addObserver(@NotNull Observer who, int what, int how);

    /**
     * Removes an observer from a specific slot.
     *
     * @param who the observer to remove
     * @param what the slot index
     * @param how the notification parameter
     */
    void removeObserver(@NotNull Observer who, int what, int how);

    /**
     * Removes all observations by the given observer.
     *
     * @param who the observer to remove
     */
    void removeAllObservers(@NotNull Observer who);

    /**
     * Gets the update period for a slot in ticks.
     *
     * @param what the slot index
     * @return the tick interval, or -1 if no periodic updates
     */
    default int getUpdatePeriod(int what) {
        return -1;
    }
}
