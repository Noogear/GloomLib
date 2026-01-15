package gloomlib.gui;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.listener.GloomGuiListener;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局管理器。
 * 負責註冊 Bukkit 監聽器，管理全局 GUI 狀態，並處理線程調度。
 */
public class GloomGuiManager {

    private static JavaPlugin plugin;
    private static final Set<GloomGui> activeGuis = Collections.newSetFromMap(new ConcurrentHashMap<>());

    private GloomGuiManager() {}

    /**
     * 初始化框架。必須在插件啟動時調用。
     */
    public static void register(JavaPlugin javaPlugin) {
        if (plugin != null) return;
        plugin = javaPlugin;

        Bukkit.getPluginManager().registerEvents(new GloomGuiListener(), plugin);

        // 啟動全局動畫 Ticker (每 1 tick 運行一次)
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (GloomGui gui : activeGuis) {
                gui.tick();
            }
        }, 1L, 1L);
    }

    public static JavaPlugin getPlugin() {
        if (plugin == null) {
            throw new IllegalStateException("GloomGui 未初始化！請在 onEnable 中調用 GloomGuiManager.register(this);");
        }
        return plugin;
    }

    public static void track(GloomGui gui) {
        activeGuis.add(gui);
    }

    public static void untrack(GloomGui gui) {
        activeGuis.remove(gui);
    }

    public static void closeAll() {
        for (GloomGui gui : activeGuis) {
            Player viewer = gui.getViewer();
            if (viewer != null && viewer.isOnline()) {
                viewer.closeInventory();
            }
        }
        activeGuis.clear();
    }
}