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

/**
 * Template class for creating GUI instances.
 */
public class GloomGuiTemplate {

    private final Component title;
    private final int rows;
    private final InventoryType type;
    private final GuiConfiguration config;
    private final Consumer<InventoryCloseEvent> closeAction;

    private final String[] structure;
    private final Map<Character, GloomComponent> charComponents;
    private final Map<Integer, GloomComponent> slotComponents;

    /**
     * Constructs a GUI template.
     *
     * @param title the title
     * @param rows the number of rows
     * @param type the inventory type
     * @param config the GUI configuration
     * @param closeAction the action on close
     * @param structure the layout structure
     * @param charComponents component mapping for characters
     * @param slotComponents component mapping for slots
     */
    public GloomGuiTemplate(Component title,
                            int rows,
                            InventoryType type,
                            GuiConfiguration config,
                            Consumer<InventoryCloseEvent> closeAction,
                            String[] structure,
                            Map<Character, GloomComponent> charComponents,
                            Map<Integer, GloomComponent> slotComponents) {
        this.title = title;
        this.rows = rows;
        this.type = type;
        this.config = config;
        this.closeAction = closeAction;
        this.structure = structure;
        this.charComponents = charComponents;
        this.slotComponents = slotComponents;
    }

    /**
     * Creates a GUI instance for a player from this template.
     *
     * @param player the player
     * @return the GUI instance
     */
    public GloomGui create(Player player) {
        Map<GloomComponent, GloomComponent> prototypeToClone = new HashMap<>();

        Map<Character, GloomComponent> clonedCharComponents = new HashMap<>();
        charComponents.forEach((key, prototype) -> {
            GloomComponent cloned = prototypeToClone.computeIfAbsent(prototype, GloomComponent::clone);
            clonedCharComponents.put(key, cloned);
        });

        Map<Integer, GloomComponent> clonedSlotComponents = new HashMap<>();
        slotComponents.forEach((slot, prototype) -> {
            GloomComponent cloned = prototypeToClone.computeIfAbsent(prototype, GloomComponent::clone);
            clonedSlotComponents.put(slot, cloned);
        });

        return new GloomGui(
                player,
                title,
                rows,
                type,
                config,
                closeAction,
                structure,
                clonedCharComponents,
                clonedSlotComponents
        );
    }
}
