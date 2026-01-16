package gloomlib.gui;

import gloomlib.gui.window.Window;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GloomGuiManager {

    private static GloomGuiManager instance;
    private static JavaPlugin plugin;

    private final Map<Window, Integer> tickingWindows = new ConcurrentHashMap<>();

    private BukkitTask globalTickTask;
    private long currentTick = 0;

    private GloomGuiManager() {
    }

    public static void init(JavaPlugin pl) {
        plugin = pl;
        instance = new GloomGuiManager();
        instance.startGlobalTask();
    }

    public static GloomGuiManager getInstance() {
        return instance;
    }

    public static JavaPlugin getPlugin() {
        return plugin;
    }

    public static void register(Window window, int tickRate) {
        if (instance != null && tickRate > 0) {
            instance.tickingWindows.put(window, tickRate);
        }
    }

    public static void unregister(Window window) {
        if (instance != null) {
            instance.tickingWindows.remove(window);
        }
    }

    public static void shutdown() {
        if (instance != null) {
            if (instance.globalTickTask != null && !instance.globalTickTask.isCancelled()) {
                instance.globalTickTask.cancel();
            }
            instance.tickingWindows.clear();
        }
    }

    private void startGlobalTask() {
        globalTickTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            currentTick++;

            tickingWindows.forEach((window, rate) -> {
                if (currentTick % rate == 0) {
                    try {
                        window.tick();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            });
        }, 1L, 1L);
    }
}