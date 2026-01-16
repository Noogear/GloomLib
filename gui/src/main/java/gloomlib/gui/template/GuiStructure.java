package gloomlib.gui.template;

import gloomlib.gui.component.GloomComponent;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

public class GuiStructure {

    private final String[] structure;
    private final Map<Character, GloomComponent> ingredientMap;

    public GuiStructure(String... structure) {
        this.structure = structure;
        this.ingredientMap = new HashMap<>();
    }

    public GuiStructure addIngredient(char key, GloomComponent component) {
        ingredientMap.put(key, component);
        return this;
    }

    public GuiStructure addIngredient(char key, ItemStack item) {
        return addIngredient(key, GloomComponent.builder().icon(item).build());
    }

    public GuiStructure addIngredient(char key, Material material) {
        return addIngredient(key, new ItemStack(material));
    }

    public GuiStructure merge(GuiStructure other) {
        this.ingredientMap.putAll(other.ingredientMap);
        int maxRows = Math.min(this.structure.length, other.structure.length);

        for (int r = 0; r < maxRows; r++) {
            StringBuilder newRow = new StringBuilder(this.structure[r]);
            String otherRow = other.structure[r];
            int length = Math.min(newRow.length(), otherRow.length());

            for (int c = 0; c < length; c++) {
                char self = newRow.charAt(c);
                char target = otherRow.charAt(c);
                if ((self == ' ' || self == '.') && target != ' ') {
                    newRow.setCharAt(c, target);
                }
            }
            this.structure[r] = newRow.toString();
        }
        return this;
    }

    public void apply(gloomlib.gui.api.GloomGuiBuilder builder) {
        builder.structure(structure);
        ingredientMap.forEach(builder::setComponent);
    }
}