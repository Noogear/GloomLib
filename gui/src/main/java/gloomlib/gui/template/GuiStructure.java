package gloomlib.gui.template;

import gloomlib.gui.api.GloomGuiBuilder;
import gloomlib.gui.component.GloomComponent;

import java.util.*;
import java.util.function.Function;

/**
 * A structured layout DSL for building GUI layouts using character grids.
 * <p>
 * This class provides a declarative way to define GUI layouts using ASCII art-style
 * character grids, similar to InvUI's Structure system. Each character in the grid
 * represents a component type, allowing for visual and intuitive GUI design.
 * <p>
 * <b>Example Usage:</b>
 * <pre>{@code
 * GuiStructure structure = GuiStructure.builder()
 *     .grid(
 *         "# # # # #",
 *         "#   x   #",
 *         "# # # # #"
 *     )
 *     .define('#', borderComponent)
 *     .define('x', centerComponent)
 *     .border('B', borderComponent)
 *     .fillRemaining('F', fillerComponent)
 *     .build();
 * }</pre>
 * 
 * @see <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui-core/src/main/java/xyz/xenondevs/invui/gui/structure/Structure.java">InvUI Structure.java</a>
 */
public class GuiStructure {

    private final String[] structure;
    private final Map<Character, GloomComponent> definitions = new HashMap<>();

    private GuiStructure(String[] structure, Map<Character, GloomComponent> definitions) {
        this.structure = structure;
        this.definitions.putAll(definitions);
    }

    /**
     * Creates a simple structure from string rows.
     * 
     * @param structure the structure rows
     */
    public GuiStructure(String... structure) {
        this.structure = structure;
    }

    /**
     * Defines a component for a specific character symbol.
     * 
     * @param symbol the character symbol
     * @param component the component to place at that symbol
     * @return this structure for chaining
     */
    public GuiStructure define(char symbol, GloomComponent component) {
        definitions.put(symbol, component);
        return this;
    }

    /**
     * Applies this structure to a GUI builder.
     * 
     * @param builder the builder to apply to
     */
    public void apply(GloomGuiBuilder builder) {
        builder.structure(structure);
        definitions.forEach(builder::define);
    }

    /**
     * Creates a new structure builder.
     * 
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for creating structured GUI layouts with advanced features.
     */
    public static class Builder {
        private List<String> gridRows = new ArrayList<>();
        private Map<Character, GloomComponent> definitions = new HashMap<>();
        private int width = 9; // Default chest width
        private int height = 3; // Default 3 rows

        /**
         * Sets the grid pattern using multiple rows.
         * Spaces are ignored, dots (.) represent empty slots.
         * 
         * @param rows the grid rows
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
         * Sets the dimensions for the GUI.
         * 
         * @param width the width (typically 9 for chests, 5 for hoppers, 3 for dispensers)
         * @param height the height in rows
         * @return this builder
         */
        public Builder size(int width, int height) {
            this.width = width;
            this.height = height;
            return this;
        }

        /**
         * Defines a component for a specific character symbol.
         * 
         * @param symbol the character symbol
         * @param component the component to place
         * @return this builder
         */
        public Builder define(char symbol, GloomComponent component) {
            definitions.put(symbol, component);
            return this;
        }

        /**
         * Defines a border around the GUI using a specific character.
         * The border component will be placed at all edge slots.
         * 
         * @param symbol the character to use for border
         * @param component the border component
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
                        // Preserve existing pattern or use empty
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
         * Fills all remaining undefined slots with a specific component.
         * This replaces all dots (.) in the grid with the specified character.
         * 
         * @param symbol the character to use for filling
         * @param component the filler component
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
         * Creates a rectangular region filled with a component.
         * 
         * @param symbol the character to use
         * @param component the component to place
         * @param startX starting X coordinate (0-based)
         * @param startY starting Y coordinate (0-based)
         * @param width width of the region
         * @param height height of the region
         * @return this builder
         */
        public Builder region(char symbol, GloomComponent component, int startX, int startY, int width, int height) {
            definitions.put(symbol, component);
            
            // Ensure grid is initialized
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
         * Creates a repeating pattern in a specific row.
         * 
         * @param rowIndex the row index (0-based)
         * @param pattern the pattern to repeat (e.g., "ABA")
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
         * Applies a function to generate components dynamically.
         * The function receives (x, y) coordinates and returns a character symbol.
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
         * Builds the GuiStructure.
         * 
         * @return the built structure
         */
        public GuiStructure build() {
            String[] structureArray = gridRows.toArray(new String[0]);
            return new GuiStructure(structureArray, definitions);
        }
    }
}
