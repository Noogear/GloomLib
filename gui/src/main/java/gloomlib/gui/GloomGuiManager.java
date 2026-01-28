package gloomlib.gui;

import gloomlib.gui.window.Window;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages GUI window tick scheduling with Folia compatibility.
 * <p>
 * Singleton that coordinates periodic updates for GUI windows using entity schedulers.
 */
public class GloomGuiManager {

    private static GloomGuiManager instance;
    private static JavaPlugin plugin;
    private final Map<Window, ScheduledTask> windowTasks = new ConcurrentHashMap<>();

    private GloomGuiManager() {
    }

    /**
     * Initializes the GUI manager with a plugin instance.
     *
     * @param pl the plugin
     */
    public static void init(JavaPlugin pl) {
        plugin = pl;
        instance = new GloomGuiManager();
        plugin.getLogger().info("GloomGuiManager initialized (Folia-compatible mode)");
    }

    /**
     * Gets the singleton instance.
     *
     * @return the manager instance
     */
    public static GloomGuiManager getInstance() {
        return instance;
    }

    /**
     * Gets the associated plugin.
     *
     * @return the plugin
     */
    public static JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * Registers a window for periodic tick updates.
     *
     * @param window the window to register
     * @param tickRate the tick interval
     */
    public static void register(Window window, int tickRate) {
        if (instance == null || tickRate <= 0) {
            return;
        }

        unregister(window);

        Player player = window.getViewer();
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            ScheduledTask task = player.getScheduler().runAtFixedRate(
                    plugin,
                    scheduledTask -> {
                        try {
                            if (!player.isOnline() || window.isClosed()) {
                                scheduledTask.cancel();
                                instance.windowTasks.remove(window);
                                return;
                            }
                            window.tick();
                        } catch (Exception e) {
                            plugin.getLogger().log(Level.WARNING, "Error occurred during window tick", e);
                            scheduledTask.cancel();
                            instance.windowTasks.remove(window);
                        }
                    },
                    null,
                    tickRate,
                    tickRate
            );

            instance.windowTasks.put(window, task);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "Cannot use EntityScheduler, falling back to GlobalRegionScheduler", e);
            Bukkit.getServer().getGlobalRegionScheduler().runAtFixedRate(
                    plugin,
                    task -> {
                        try {
                            if (!player.isOnline() || window.isClosed()) {
                                task.cancel();
                                return;
                            }
                            window.tick();
                        } catch (Exception ex) {
                            plugin.getLogger().log(Level.WARNING, "Error occurred during window tick", ex);
                            task.cancel();
                        }
                    },
                    tickRate,
                    tickRate
            );
        }
    }

    /**
     * Unregisters a window from periodic updates.
     *
     * @param window the window to unregister
     */
    public static void unregister(Window window) {
        if (instance == null) {
            return;
        }

        ScheduledTask task = instance.windowTasks.remove(window);
        if (task != null) {
            try {
                task.cancel();
            } catch (Exception e) {
            }
        }
    }

    /**
     * Shuts down the GUI manager and cancels all running window tasks.
     */
    public static void shutdown() {
        if (instance == null) {
            return;
        }

        instance.windowTasks.values().forEach(task -> {
            try {
                task.cancel();
            } catch (Exception e) {
            }
        });

        instance.windowTasks.clear();
        plugin.getLogger().info("GloomGuiManager has been shut down");
    }
}