package gloomlib.gui.template;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * A reusable template for creating GloomGui instances.
 * Stores the layout and components as prototypes.
 */
public class GloomGuiTemplate {

    private final Plugin plugin;
    private final String title;
    private final int rows;
    private final Map<Integer, GloomComponent> prototypeComponents;

    public GloomGuiTemplate(Plugin plugin, String title, int rows, Map<Integer, GloomComponent> components) {
        this.plugin = plugin;
        this.title = title;
        this.rows = rows;
        this.prototypeComponents = components;
    }

    /**
     * Creates a unique GUI session for a player based on this template.
     */
    public GloomGui create(@NotNull Player player) {
        GloomGui gui = new GloomGui(plugin, player, title, rows);

        // Apply components using Deep Copy (Clone)
        prototypeComponents.forEach((slot, componentPrototype) -> {
            // CRITICAL: Must clone to ensure thread safety and state isolation per player.
            // If we didn't clone, one player clicking a button would affect all other players' GUIs.
            GloomComponent instance = componentPrototype.clone();
            gui.setComponent(slot, instance);
        });

        return gui;
    }
}