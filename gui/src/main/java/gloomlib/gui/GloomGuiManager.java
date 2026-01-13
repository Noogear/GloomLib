package gloomlib.gui;

import gloomlib.gui.api.GloomGuiListener;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * The entry point for the GloomGui framework.
 * Handles listener registration and global cleanup.
 */
public class GloomGuiManager {

    private static GloomGuiManager instance;
    private final Plugin plugin;
    private final GloomGuiListener listener;

    private GloomGuiManager(Plugin plugin) {
        this.plugin = plugin;
        this.listener = new GloomGuiListener();
    }

    /**
     * Initializes the GUI framework.
     * Must be called in your plugin's onEnable().
     *
     * @param plugin The hosting plugin.
     */
    public static void init(@NotNull Plugin plugin) {
        if (instance != null) {
            throw new IllegalStateException("GloomGuiManager is already initialized!");
        }
        instance = new GloomGuiManager(plugin);
        Bukkit.getPluginManager().registerEvents(instance.listener, plugin);
    }

    /**
     * Gets the singleton instance.
     */
    public static GloomGuiManager getInstance() {
        if (instance == null) {
            throw new IllegalStateException("GloomGuiManager is not initialized! Call init() first.");
        }
        return instance;
    }

    /**
     * Disables the framework and unregisters listeners.
     */
    public void disable() {
        HandlerList.unregisterAll(listener);
        instance = null;
    }

    public Plugin getPlugin() {
        return plugin;
    }
}