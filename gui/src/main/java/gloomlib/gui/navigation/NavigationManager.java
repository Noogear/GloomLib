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
 * <p>
 * Thread-safe implementation using concurrent data structures for Folia compatibility.
 * <p>
 * Reference: Inspired by InvUI's window management system and Triumph GUI's view lifecycle.
 * 
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
     * <p>
     * This should be called when opening a new GUI that should be part of the navigation chain.
     * <p>
     * <b>Edge Case Prevention:</b>
     * <ul>
     *   <li>Prevents circular navigation patterns</li>
     *   <li>Skips duplicate consecutive windows</li>
     *   <li>Validates window state before pushing</li>
     * </ul>
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
     * <p>
     * Returns true if navigation was successful, false if there's no history to go back to.
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
     * @return the previous window, or null if none exists
     */
    public @Nullable Window peek(@NotNull Player player) {
        return getHistory(player).peek();
    }

    /**
     * Clears the navigation history for a player.
     * <p>
     * This should be called when the player logs out or when you want to reset their navigation.
     * <p>
     * <b>Performance Note:</b> This also cleans up the player's entry from the global history map.
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
     * Clears all navigation histories (useful for plugin reload/shutdown).
     * <p>
     * <b>Warning:</b> This clears histories for all players. Use with caution.
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
