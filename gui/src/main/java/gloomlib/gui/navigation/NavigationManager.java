package gloomlib.gui.navigation;

import gloomlib.gui.window.Window;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Global navigation manager handling GUI navigation history for all players.
 */
public final class NavigationManager {

    private static final NavigationManager INSTANCE = new NavigationManager();

    private final Map<UUID, NavigationHistory> histories = new ConcurrentHashMap<>();

    private NavigationManager() {
    }

    /**
     * Gets the singleton instance of the navigation manager.
     *
     * @return the navigation manager instance
     */
    public static NavigationManager getInstance() {
        return INSTANCE;
    }

    /**
     * Gets or creates the navigation history for a player.
     *
     * @param player the player
     * @return the player's navigation history
     */
    public @NotNull NavigationHistory getHistory(@NotNull Player player) {
        return histories.computeIfAbsent(player.getUniqueId(), uuid -> new NavigationHistory(player));
    }

    /**
     * Pushes a window onto the player's navigation stack.
     *
     * @param player the player
     * @param window the window to push
     */
    public void push(@NotNull Player player, @NotNull Window window) {
        if (window == null || player == null) {
            return;
        }
        getHistory(player).push(window);
    }

    /**
     * Navigates back to the previous window in the player's history.
     *
     * @param player the player
     * @return true if navigated back successfully
     */
    public boolean back(@NotNull Player player) {
        return getHistory(player).back();
    }

    /**
     * Gets the previous window without removing it from the stack.
     *
     * @param player the player
     * @return the previous window
     */
    public @Nullable Window peek(@NotNull Player player) {
        return getHistory(player).peek();
    }

    /**
     * Clears the navigation history for a player.
     *
     * @param player the player
     */
    public void clear(@NotNull Player player) {
        if (player == null) {
            return;
        }

        NavigationHistory history = histories.remove(player.getUniqueId());
        if (history != null) {
            history.clear();
        }
    }

    /**
     * Clears all navigation histories.
     */
    public void clearAll() {
        for (NavigationHistory history : histories.values()) {
            history.clear();
        }
        histories.clear();
    }

    /**
     * Checks if the player has any navigation history.
     *
     * @param player the player
     * @return true if the player can navigate back
     */
    public boolean hasHistory(@NotNull Player player) {
        return getHistory(player).hasHistory();
    }

    /**
     * Gets the current depth of the navigation stack for a player.
     *
     * @param player the player
     * @return the stack depth
     */
    public int getDepth(@NotNull Player player) {
        return getHistory(player).getDepth();
    }
}
