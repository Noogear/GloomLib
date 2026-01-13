package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.template.GloomGuiTemplate;
import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class GloomGuiBuilder {

    private Component title;
    private int rows = 3;
    private InventoryType type = InventoryType.CHEST;
    private String[] structure;

    private final Map<Character, GloomComponent> charComponents = new HashMap<>();
    private final Map<Integer, GloomComponent> slotComponents = new HashMap<>();

    private GloomGuiBuilder() {}

    public static GloomGuiBuilder create() {
        return new GloomGuiBuilder();
    }

    public GloomGuiBuilder title(Component title) {
        this.title = title;
        return this;
    }

    public GloomGuiBuilder rows(int rows) {
        this.rows = rows;
        return this;
    }

    public GloomGuiBuilder type(InventoryType type) {
        this.type = type;
        return this;
    }

    public GloomGuiBuilder structure(String... pattern) {
        this.structure = pattern;
        this.rows = pattern.length;
        return this;
    }

    public GloomGuiBuilder setComponent(char key, GloomComponent component) {
        this.charComponents.put(key, component);
        return this;
    }

    public GloomGuiBuilder setIngredient(char key, ItemStack item) {
        return setComponent(key, GloomComponent.builder().icon(item).build());
    }

    public GloomGuiBuilder setItem(int slot, GloomComponent component) {
        this.slotComponents.put(slot, component);
        return this;
    }

    public GloomGuiBuilder setItem(int slot, ItemStack item) {
        return setItem(slot, GloomComponent.builder().icon(item).build());
    }

    public GloomGuiTemplate buildTemplate() {
        if (title == null) {
            title = Component.text("GloomGui");
        }
        return new GloomGuiTemplate(title, rows, type, structure, charComponents, slotComponents);
    }
}