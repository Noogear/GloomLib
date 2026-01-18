package gloomlib.gui.template;

import gloomlib.gui.api.GloomGuiBuilder;
import gloomlib.gui.component.GloomComponent;

import java.util.HashMap;
import java.util.Map;

public class GuiStructure {

    private final String[] structure;
    private final Map<Character, GloomComponent> definitions = new HashMap<>();

    public GuiStructure(String... structure) {
        this.structure = structure;
    }

    public GuiStructure define(char symbol, GloomComponent component) {
        definitions.put(symbol, component);
        return this;
    }

    public void apply(GloomGuiBuilder builder) {
        builder.structure(structure);
        definitions.forEach(builder::define);
    }
}