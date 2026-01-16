package gloomlib.gui.template;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.config.GuiConfiguration;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class GloomGuiTemplate {

    private final Component title;
    private final int rows;
    private final InventoryType type;
    private final GuiConfiguration config;
    private final Consumer<InventoryCloseEvent> closeAction;

    private final Map<Integer, GloomComponent> layoutPrototypes;
    private final Map<Integer, Integer> slotIndices;

    public GloomGuiTemplate(Component title, int rows, InventoryType type,
                            GuiConfiguration config,
                            Consumer<InventoryCloseEvent> closeAction,
                            Map<Integer, GloomComponent> layoutPrototypes,
                            Map<Integer, Integer> slotIndices) {
        this.title = title;
        this.rows = rows;
        this.type = type;
        this.config = config;
        this.closeAction = closeAction;
        this.layoutPrototypes = layoutPrototypes;
        this.slotIndices = slotIndices;
    }

    public GloomGui create(Player player) {
        Map<Integer, GloomComponent> clonedLayout = new HashMap<>();

        Map<GloomComponent, GloomComponent> prototypeToClone = new HashMap<>();

        layoutPrototypes.forEach((slot, prototype) -> {
            GloomComponent cloned = prototypeToClone.computeIfAbsent(prototype, GloomComponent::clone);
            clonedLayout.put(slot, cloned);
        });

        int size = (type == InventoryType.CHEST) ? rows * 9 : type.getDefaultSize();

        return new GloomGui(
                player,
                title,
                size,
                config,
                closeAction,
                clonedLayout,
                slotIndices
        );
    }
}