package gloomlib.gui.template;

import gloomlib.gui.api.GloomGuiBuilder;
import gloomlib.gui.component.GloomComponent;

import java.util.*;
import java.util.function.Function;

/**
 * Utility for defining and building GUI layouts using a grid structure.
 */
public class GuiStructure {

    private final String[] structure;
    private final Map<Character, GloomComponent> definitions = new HashMap<>();

    private GuiStructure(String[] structure, Map<Character, GloomComponent> definitions) {
        this.structure = structure;
        this.definitions.putAll(definitions);
    }

    /**
     * Constructs a GUI structure with the given layout.
     *
     * @param structure the layout rows
     */
    public GuiStructure(String... structure) {
        this.structure = structure;
    }

    /**
     * Creates a new builder for a GUI structure.
     *
     * @return the builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Defines a component for a symbol.
     *
     * @param symbol the character symbol
     * @param component the component
     * @return this structure
     */
    public GuiStructure define(char symbol, GloomComponent component) {
        definitions.put(symbol, component);
        return this;
    }

    /**
     * Applies this structure to a GUI builder.
     *
     * @param builder the GUI builder
     */
    public void apply(GloomGuiBuilder builder) {
        builder.structure(structure);
        definitions.forEach(builder::define);
    }

    /**
     * Builder class for GuiStructure.
     */
    public static class Builder {
        private final Map<Character, GloomComponent> definitions = new HashMap<>();
        private List<String> gridRows = new ArrayList<>();
        private int width = 9;
        private int height = 3;

        /**
         * Sets the grid layout.
         *
         * @param rows the layout rows
         * @return this builder
         */
        public Builder grid(String... rows) {
            this.gridRows = Arrays.asList(rows);
            this.height = rows.length;
            if (rows.length > 0) {
                this.width = rows[0].replace(" ", "").length();
            }
            return this;
        }

        /**
         * Sets the size of the grid.
         *
         * @param width the width
         * @param height the height
         * @return this builder
         */
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Defines a component for a symbol.
         *
         * @param symbol the symbol
         * @param component the component
         * @return this builder
         */
        public Builder define(char symbol, GloomComponent component) {
            definitions.put(symbol, component);
            return this;
        }

        /**
         * Adds a border with the given symbol and component.
         *
         * @param symbol the symbol
         * @param component the component
         * @return this builder
         */
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

        /**
         * Fills all remaining dots with the given symbol and component.
         *
         * @param symbol the symbol
         * @param component the component
         * @return this builder
         */
        public Builder fillRemaining(char symbol, GloomComponent component) {
            definitions.put(symbol, component);

            this.gridRows = gridRows.stream()
                    .map(row -> row.replace('.', symbol))
                    .toList();
            return this;
        }

        /**
         * Fills a rectangular region with the given symbol and component.
         *
         * @param symbol the symbol
         * @param component the component
         * @param startX the starting X
         * @param startY the starting Y
         * @param width the width
         * @param height the height
         * @return this builder
         */
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

        /**
         * Fills a row with a repeating pattern.
         *
         * @param rowIndex the row index
         * @param pattern the pattern string
         * @return this builder
         */
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

        /**
         * Generates the grid layout using a generator function.
         *
         * @param generator the generator function
         * @return this builder
         */
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

        /**
         * Builds the GUI structure.
         *
         * @return the built structure
         */
        public GuiStructure build() {
            String[] structureArray = gridRows.toArray(new String[0]);
            return new GuiStructure(structureArray, definitions);
        }
    }
}
