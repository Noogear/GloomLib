package gloomlib.gui.observable;

import org.jetbrains.annotations.NotNull;

/**
 * An object that can be observed for changes.
 * <p>
 * Observables can notify observers when specific aspects (identified by "what") change.
 * Additionally, observables can specify an update period for each aspect, enabling
 * automatic periodic updates without explicit change notifications.
 * <p>
 * Inspired by InvUI's Observable pattern with update period support.
 * 
 * @see Observer
 * @see <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui-core/src/main/java/xyz/xenondevs/invui/gui/AbstractGui.java#L100-120">InvUI AbstractWindow.java#L100-120</a>
 */
public interface Observable {

    /**
     * Adds an observer to this observable.
     * 
     * @param who the observer to add
     * @param what identifier for what aspect to observe
     * @param how identifier for how to observe (used by observer)
     */
    void addObserver(@NotNull Observer who, int what, int how);

    /**
     * Removes a specific observer registration.
     * 
     * @param who the observer to remove
     * @param what the aspect identifier
     * @param how the observation mode identifier
     */
    void removeObserver(@NotNull Observer who, int what, int how);

    /**
     * Removes all registrations of an observer from this observable.
     * 
     * @param who the observer to remove completely
     */
    void removeAllObservers(@NotNull Observer who);

    /**
     * Gets the automatic update period (in ticks) for a specific aspect.
     * <p>
     * If this returns a positive value, observers will be notified automatically
     * every N ticks, even without explicit change notifications. This is useful
     * for time-dependent displays or animations.
     * <p>
     * <b>Examples:</b>
     * <ul>
     *     <li>Return 20 for updates every second (20 ticks)</li>
     *     <li>Return 1 for updates every tick</li>
     *     <li>Return -1 for no automatic updates (default)</li>
     * </ul>
     * 
     * @param what identifier for what aspect to check
     * @return the update period in ticks, or -1 if no automatic updates
     */
    default int getUpdatePeriod(int what) {
        return -1; // No automatic updates by default
    }
}
