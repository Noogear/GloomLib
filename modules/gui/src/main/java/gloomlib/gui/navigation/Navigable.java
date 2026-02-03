package gloomlib.gui.navigation;

import gloomlib.gui.window.Window;
import org.jetbrains.annotations.NotNull;

/**
 * Interface for windows that support navigation.
 */
public interface Navigable {

    /**
     * Gets the window instance for this navigable GUI.
     *
     * @return the window
     */
    @NotNull Window getWindow();

    /**
     * Checks if navigation is enabled for this window.
     *
     * @return true if navigation is enabled
     */
    default boolean isNavigationEnabled() {
        return true;
    }

    /**
     * Enables or disables navigation tracking for this window.
     *
     * @param enabled true to enable navigation
     */
    default void setNavigationEnabled(boolean enabled) {
    }

    /**
     * Navigates back to the previous window.
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
     * @return true if history exists
     */
    default boolean canNavigateBack() {
        Window window = getWindow();
        if (window != null && window.getViewer() != null) {
            return NavigationManager.getInstance().hasHistory(window.getViewer());
        }
        return false;
    }
}
