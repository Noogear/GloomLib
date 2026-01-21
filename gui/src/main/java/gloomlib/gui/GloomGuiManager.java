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
 * GloomGui 管理器，负责管理所有 GUI 窗口的生命周期和 tick 调度
 * <p>
 * 本管理器已针对 Folia 多线程环境进行优化，使用 Paper 的 EntityScheduler API
 * 为每个窗口创建独立的调度任务，而不是使用全局 tick 任务
 * 
 * @author GloomLib
 * @since 2.0
 */
public class GloomGuiManager {

    private static GloomGuiManager instance;
    private static JavaPlugin plugin;

    // 存储每个窗口的调度任务句柄，用于取消任务
    private final Map<Window, ScheduledTask> windowTasks = new ConcurrentHashMap<>();

    private GloomGuiManager() {
    }

    /**
     * 初始化 GUI 管理器
     * 
     * @param pl 插件实例
     */
    public static void init(JavaPlugin pl) {
        plugin = pl;
        instance = new GloomGuiManager();
        plugin.getLogger().info("GloomGuiManager 已初始化 (Folia 兼容模式)");
    }

    public static GloomGuiManager getInstance() {
        return instance;
    }

    public static JavaPlugin getPlugin() {
        return plugin;
    }

    /**
     * 为窗口注册 tick 任务（使用 Paper EntityScheduler 以支持 Folia）
     * 
     * @param window   要注册的窗口
     * @param tickRate tick 间隔（单位：ticks）
     */
    public static void register(Window window, int tickRate) {
        if (instance == null || tickRate <= 0) {
            return;
        }

        // 如果已经有任务在运行，先取消
        unregister(window);

        Player player = window.getViewer();
        if (player == null || !player.isOnline()) {
            return;
        }

        try {
            // 使用 Paper 的 EntityScheduler API（Folia 兼容）
            // 如果在非 Folia 服务器上运行，这仍然可以正常工作
            ScheduledTask task = player.getScheduler().runAtFixedRate(
                plugin,
                scheduledTask -> {
                    try {
                        // 检查窗口是否已关闭
                        if (!player.isOnline() || window.isClosed()) {
                            scheduledTask.cancel();
                            instance.windowTasks.remove(window);
                            return;
                        }
                        window.tick();
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "窗口 tick 时发生错误", e);
                        // 发生错误时取消任务避免持续报错
                        scheduledTask.cancel();
                        instance.windowTasks.remove(window);
                    }
                },
                null,
                (long) tickRate,
                (long) tickRate
            );

            instance.windowTasks.put(window, task);
        } catch (Exception e) {
            // 如果不支持 EntityScheduler（旧版本 Paper），回退到传统调度器
            plugin.getLogger().log(Level.WARNING, "无法使用 EntityScheduler，尝试使用传统调度器", e);
            Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                try {
                    if (!player.isOnline()) {
                        return;
                    }
                    window.tick();
                } catch (Exception ex) {
                    plugin.getLogger().log(Level.WARNING, "窗口 tick 时发生错误", ex);
                }
            }, 0L, tickRate);
        }
    }

    /**
     * 取消窗口的 tick 任务
     * 
     * @param window 要取消的窗口
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
                // 忽略取消失败的错误
            }
        }
    }

    /**
     * 关闭所有窗口并清理资源
     */
    public static void shutdown() {
        if (instance == null) {
            return;
        }

        // 取消所有调度任务
        instance.windowTasks.values().forEach(task -> {
            try {
                task.cancel();
            } catch (Exception e) {
                // 忽略取消失败的错误
            }
        });

        instance.windowTasks.clear();
        plugin.getLogger().info("GloomGuiManager 已关闭");
    }
}