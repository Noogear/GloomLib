package gloomlib.gui.template;

import gloomlib.gui.api.GloomGuiBuilder;
import gloomlib.gui.component.GloomComponent;

import java.util.*;
import java.util.function.Function;

public class GuiStructure {

    private final String[] structure;
    private final Map<Character, GloomComponent> definitions = new HashMap<>();

    private GuiStructure(String[] structure, Map<Character, GloomComponent> definitions) {
        this.structure = structure;
        this.definitions.putAll(definitions);
    }

    public GuiStructure(String... structure) {
        this.structure = structure;
    }

    public static Builder builder() {
        return new Builder();
    }

    public GuiStructure define(char symbol, GloomComponent component) {
        definitions.put(symbol, component);
        return this;
    }

    public void apply(GloomGuiBuilder builder) {
        builder.structure(structure);
        definitions.forEach(builder::define);
    }

    public static class Builder {
        private final Map<Character, GloomComponent> definitions = new HashMap<>();
        private List<String> gridRows = new ArrayList<>();
        private int width = 9;
        private int height = 3;

        public Builder grid(String... rows) {
            this.gridRows = Arrays.asList(rows);
            this.height = rows.length;
            if (rows.length > 0) {
                this.width = rows[0].replace(" ", "").length();
            }
            return this;
        }

        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        public Builder define(char symbol, GloomComponent component) {
            definitions.put(symbol, component);
            return this;
        }

        public Builder border(char symbol, GloomComponent component) {
            definitions.put(symbol, component);

            StringBuilder[] rows = new StringBuilder[height];
            for (int i = 0; i < height; i++) {
                rows[i] = new StringBuilder();
            }

            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    if (y == 0 || y == height - 1 || x == 0 || x == width - 1) {
                        rows[y].append(symbol);
                    } else {
                        if (y < gridRows.size() && x < gridRows.get(y).replace(" ", "").length()) {
                            String existing = gridRows.get(y).replace(" ", "");
                            rows[y].append(existing.charAt(x));
                        } else {
                            rows[y].append('.');
                        }
                    }
                }
            }

            this.gridRows = Arrays.stream(rows).map(StringBuilder::toString).toList();
            return this;
        }

        public Builder fillRemaining(char symbol, GloomComponent component) {
            definitions.put(symbol, component);

            this.gridRows = gridRows.stream()
                    .map(row -> row.replace('.', symbol))
                    .toList();
            return this;
        }

        public Builder region(char symbol, GloomComponent component, int startX, int startY, int width, int height) {
            definitions.put(symbol, component);

            while (gridRows.size() < this.height) {
                gridRows.add(".".repeat(this.width));
            }

            for (int y = startY; y < startY + height && y < this.height; y++) {
                StringBuilder row = new StringBuilder(gridRows.get(y));
                for (int x = startX; x < startX + width && x < this.width; x++) {
                    row.setCharAt(x, symbol);
                }
                gridRows.set(y, row.toString());
            }
            return this;
        }

        public Builder rowPattern(int rowIndex, String pattern) {
            while (gridRows.size() <= rowIndex) {
                gridRows.add(".".repeat(width));
            }

            StringBuilder row = new StringBuilder();
            int patternIndex = 0;
            for (int x = 0; x < width; x++) {
                row.append(pattern.charAt(patternIndex));
                patternIndex = (patternIndex + 1) % pattern.length();
            }
            gridRows.set(rowIndex, row.toString());
            return this;
        }

        public Builder generate(Function<int[], Character> generator) {
            gridRows.clear();
            for (int y = 0; y < height; y++) {
                StringBuilder row = new StringBuilder();
                for (int x = 0; x < width; x++) {
                    char symbol = generator.apply(new int[]{x, y});
                    row.append(symbol);
                }
                gridRows.add(row.toString());
            }
            return this;
        }

        public GuiStructure build() {
            String[] structureArray = gridRows.toArray(new String[0]);
            return new GuiStructure(structureArray, definitions);
        }
    }
}
