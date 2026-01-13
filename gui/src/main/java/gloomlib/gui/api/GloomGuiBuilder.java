package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.template.GloomGuiTemplate;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Builder for creating GloomGuiTemplates.
 */
public class GloomGuiBuilder {

    private final Plugin plugin;
    private final Map<Integer, GloomComponent> components = new HashMap<>();
    private String title = "Gloom GUI";
    private int rows = 3;

    public GloomGuiBuilder(Plugin plugin) {
        this.plugin = plugin;
    }

    public GloomGuiBuilder title(String title) {
        this.title = title;
        return this;
    }

    public GloomGuiBuilder rows(int rows) {
        if (rows < 1 || rows > 6) throw new IllegalArgumentException("Rows must be between 1 and 6");
        this.rows = rows;
        return this;
    }

    public GloomGuiBuilder setItem(int slot, @NotNull GloomComponent component) {
        components.put(slot, component);
        return this;
    }

    /**
     * Fills specific slots with a component.
     */
    public GloomGuiBuilder fill(GloomComponent component, int... slots) {
        for (int slot : slots) {
            // We store the same prototype reference for multiple slots in the Builder.
            // The Template will clone it multiple times when creating the GUI.
            setItem(slot, component);
        }
        return this;
    }

    public GloomGuiTemplate build() {
        return new GloomGuiTemplate(plugin, title, rows, components);
    }
}