package gloomlib.gui;

import gloomlib.gui.window.Window;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class GloomGuiManager {

    private static GloomGuiManager instance;
    private static JavaPlugin plugin;
    private final Map<Window, ScheduledTask> windowTasks = new ConcurrentHashMap<>();

    private GloomGuiManager() {
    }

    public static void init(JavaPlugin pl) {
        plugin = pl;
        instance = new GloomGuiManager();
        plugin.getLogger().info("GloomGuiManager initialized (Folia-compatible mode)");
    }

    public static GloomGuiManager getInstance() {
        return instance;
    }

    public static JavaPlugin getPlugin() {
        return plugin;
    }

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
                            plugin.getLogger().log(Level.WARNING, "窗口 tick 时发生错误", e);
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
            plugin.getLogger().log(Level.WARNING, "无法使用 EntityScheduler，使用 GlobalRegionScheduler", e);
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
                            plugin.getLogger().log(Level.WARNING, "窗口 tick 时发生错误", ex);
                            task.cancel();
                        }
                    },
                    tickRate,
                    tickRate
            );
        }
    }

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
        plugin.getLogger().info("GloomGuiManager 已关闭");
    }
}