package gloomlib.gui.window;

import org.jetbrains.annotations.NotNull;

/**
 * Interface for observable GUI elements.
 */
public interface Observable {

    /**
     * Adds an observer to a specific slot.
     *
     * @param who the observer
     * @param what the slot index to observe
     * @param how the notification hint
     */
    void addObserver(@NotNull Observer who, int what, int how);

    /**
     * Removes an observer from a specific slot.
     *
     * @param who the observer to remove
     * @param what the slot index
     * @param how the notification hint
     */
    void removeObserver(@NotNull Observer who, int what, int how);

    /**
     * Removes all observations by an observer.
     *
     * @param who the observer to remove
     */
    void removeAllObservers(@NotNull Observer who);

    /**
     * Gets the update period for a slot.
     *
     * @param what the slot index
     * @return the tick interval
     */
    default int getUpdatePeriod(int what) {
        return -1;
    }
}
