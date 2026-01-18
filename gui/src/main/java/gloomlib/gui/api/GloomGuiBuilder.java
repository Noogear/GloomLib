package gloomlib.gui.api;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.config.GuiConfiguration;
import gloomlib.gui.template.GuiStructure;
import net.kyori.adventure.text.Component;
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

    public GloomGui create(org.bukkit.entity.Player player) {
        if (title == null) {
            title = Component.text("GloomGui");
        }

        GuiConfiguration config;

        if (manualAnimationEnable != null && manualAnimationEnable) {
            config = new GuiConfiguration(GuiConfiguration.UpdateStrategy.PERIODIC, manualTickRate, true);
        } else {
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
                config = new GuiConfiguration(GuiConfiguration.UpdateStrategy.PERIODIC, minDetectedRate, true);
            } else {
                config = GuiConfiguration.REACTIVE;
            }
        }

        Map<Integer, GloomComponent> layout = new HashMap<>();
        Map<Integer, Integer> indices = new HashMap<>();
        Map<GloomComponent, Integer> counters = new HashMap<>();

        int width = 9;
        if (type == InventoryType.HOPPER) width = 5;
        else if (type == InventoryType.DISPENSER || type == InventoryType.DROPPER || type == InventoryType.WORKBENCH)
            width = 3;

        if (structure != null) {
            for (int r = 0; r < structure.length; r++) {
                String rowStr = structure[r].replace(" ", "");
                for (int c = 0; c < rowStr.length() && c < width; c++) {
                    char key = rowStr.charAt(c);
                    if (key == '.') {
                        continue;
                    }

                    GloomComponent comp = charComponents.get(key);
                    if (comp != null) {
                        int slot = r * width + c;

                        int idx = counters.getOrDefault(comp, 0);

                        layout.put(slot, comp);
                        indices.put(slot, idx);

                        counters.put(comp, idx + 1);
                    }
                }
            }
        }

        slotComponents.forEach((slot, comp) -> {
            layout.put(slot, comp);
            indices.put(slot, 0);
        });

        int size = (type == InventoryType.CHEST) ? rows * 9 : type.getDefaultSize();

        return new GloomGui(
                player,
                title,
                size,
                config,
                closeAction,
                layout,
                indices
        );
    }
}