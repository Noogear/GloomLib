package gloomlib.gui.navigation;

import gloomlib.gui.window.Window;
import org.jetbrains.annotations.NotNull;

/**
 * Marker interface for windows that support navigation.
 * <p>
 * Windows implementing this interface can be part of the navigation history chain.
 * When opened, they will automatically register themselves in the navigation stack.
 * <p>
 * Reference: Inspired by InvUI's window architecture where windows can maintain parent relationships,
 * adapted to GloomLib's builder pattern and reactive system.
 */
public interface Navigable {

    /**
     * Gets the window instance for this navigable GUI.
     *
     * @return the window
     */
    @NotNull Window getWindow();

    /**
     * Enables navigation tracking for this window.
     * <p>
     * When enabled, this window will be pushed to the navigation stack when opened.
     *
     * @param enabled true to enable navigation
     */
    default void setNavigationEnabled(boolean enabled) {
    }

    /**
     * Checks if navigation is enabled for this window.
     *
     * @return true if navigation is enabled
     */
    default boolean isNavigationEnabled() {
        return true;
    }

    /**
     * Navigates back to the previous window.
     * <p>
     * Convenience method that delegates to NavigationManager.
     *
     * @return true if navigation was successful
     */
    default boolean navigateBack() {
        Window window = getWindow();
        if (window != null && window.getViewer() != null) {
            return NavigationManager.getInstance().back(window.getViewer());
        }
        return false;
    }

    /**
     * Checks if this window can navigate back.
     *
     * @return true if there is navigation history
     */
    default boolean canNavigateBack() {
        Window window = getWindow();
        if (window != null && window.getViewer() != null) {
            return NavigationManager.getInstance().hasHistory(window.getViewer());
        }
        return false;
    }
}
