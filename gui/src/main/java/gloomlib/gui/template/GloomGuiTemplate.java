package gloomlib.gui.template;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.component.GloomComponent;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;

import java.util.HashMap;
import java.util.Map;

public class GloomGuiTemplate {

    private final Component title;
    private final int rows;
    private final InventoryType type;
    private final String[] structure;
    private final Map<Character, GloomComponent> charComponents;
    private final Map<Integer, GloomComponent> slotComponents;

    public GloomGuiTemplate(Component title, int rows, InventoryType type, String[] structure,
                            Map<Character, GloomComponent> charComponents,
                            Map<Integer, GloomComponent> slotComponents) {
        this.title = title;
        this.rows = rows;
        this.type = type;
        this.structure = structure;
        this.charComponents = charComponents;
        this.slotComponents = slotComponents;
    }

    public GloomGui create(Player player) {
        Map<Character, GloomComponent> clonedCharMap = new HashMap<>();
        charComponents.forEach((k, v) -> clonedCharMap.put(k, v.clone()));

        Map<Integer, GloomComponent> clonedSlotMap = new HashMap<>();
        slotComponents.forEach((k, v) -> clonedSlotMap.put(k, v.clone()));

        return new GloomGui(player, title, rows, type, structure, clonedCharMap, clonedSlotMap);
    }
}