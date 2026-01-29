package gloomlib.gui.window;

/**
 * Interface for observing GUI state updates.
 */
public interface Observer {

    /**
     * Notifies the observer of a state change.
     *
     * @param how the index of the change
     */
    void notifyUpdate(int how);
}
