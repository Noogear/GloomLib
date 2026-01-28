package gloomlib.gui.window;

/**
 * Observer pattern interface for window state updates.
 * <p>
 * Implemented by windows to receive notifications when GUI components change.
 */
public interface Observer {

    /**
     * Notifies the observer of a state change.
     *
     * @param how the slot index that changed
     */
    void notifyUpdate(int how);
}
