package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.config.GuiConfiguration;
import gloomlib.gui.template.GuiStructure;
import gloomlib.gui.window.SimpleWindow;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class GloomGuiBuilder {

    private final Map<Character, GloomComponent> charComponents = new HashMap<>();
    private final Map<Integer, GloomComponent> slotComponents = new HashMap<>();
    private Component title;
    private int rows = 3;
    private InventoryType type = InventoryType.CHEST;
    private String[] structure;
    private Consumer<InventoryCloseEvent> closeAction;
    private Boolean manualAnimationEnable = null;
    private int manualTickRate = -1;

    private GloomGuiBuilder() {
    }

    public static GloomGuiBuilder create() {
        return new GloomGuiBuilder();
    }

    public static GloomGuiBuilder chest() {
        return create().type(InventoryType.CHEST);
    }

    public static GloomGuiBuilder hopper() {
        return create().type(InventoryType.HOPPER).rows(1);
    }

    public static GloomGuiBuilder dispenser() {
        return create().type(InventoryType.DISPENSER).rows(3);
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

    public GloomGuiBuilder enableAnimations(int tickRate) {
        this.manualAnimationEnable = true;
        this.manualTickRate = tickRate;
        return this;
    }

    public GloomGuiBuilder enableAnimations() {
        return enableAnimations(1);
    }

    public GloomGuiBuilder structure(String... pattern) {
        this.structure = pattern;
        this.rows = Math.max(this.rows, pattern.length);
        return this;
    }

    public GloomGuiBuilder applyStructure(GuiStructure guiStructure) {
        guiStructure.apply(this);
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

    public GloomGuiBuilder define(char key, Consumer<GloomComponent.Builder> config) {
        GloomComponent.Builder builder = GloomComponent.builder();
        config.accept(builder);
        return setComponent(key, builder.build());
    }

    public GloomGuiBuilder define(char key, GloomComponent component) {
        return setComponent(key, component);
    }

    public GloomGuiBuilder onClose(Consumer<InventoryCloseEvent> closeAction) {
        this.closeAction = closeAction;
        return this;
    }

    public void open(Player player) {
        GloomGui gui = build(player);
        int size = (type == InventoryType.CHEST) ? rows * 9 : type.getDefaultSize();
        SimpleWindow window = new SimpleWindow(player, title, gui, type, size);
        window.open();
    }

    public GloomGui build(Player player) {
        if (title == null) {
            title = Component.text("GloomGui");
        }

        GuiConfiguration config = resolveConfiguration();

        return new GloomGui(
                player,
                title,
                rows,
                type,
                config,
                closeAction,
                structure,
                charComponents,
                slotComponents
        );
    }

    private GuiConfiguration resolveConfiguration() {
        if (manualAnimationEnable != null && manualAnimationEnable) {
            return new GuiConfiguration(GuiConfiguration.UpdateStrategy.PERIODIC, manualTickRate, true);
        }

        int minDetectedRate = Integer.MAX_VALUE;
        boolean hasAnimatedComponents = false;

        for (GloomComponent comp : charComponents.values()) {
            int r = comp.getTickRate();
            if (r > 0) {
                hasAnimatedComponents = true;
                minDetectedRate = Math.min(minDetectedRate, r);
            }
        }
        for (GloomComponent comp : slotComponents.values()) {
            int r = comp.getTickRate();
            if (r > 0) {
                hasAnimatedComponents = true;
                minDetectedRate = Math.min(minDetectedRate, r);
            }
        }

        if (hasAnimatedComponents) {
            return new GuiConfiguration(GuiConfiguration.UpdateStrategy.PERIODIC, minDetectedRate, true);
        } else {
            return GuiConfiguration.REACTIVE;
        }
    }
}