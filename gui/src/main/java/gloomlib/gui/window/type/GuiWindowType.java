package gloomlib.gui.window.type;

import org.bukkit.event.inventory.InventoryType;
import org.jetbrains.annotations.NotNull;

public enum GuiWindowType {

    CHEST(InventoryType.CHEST, 9, true, 6),

    HOPPER(InventoryType.HOPPER, 5, false, 1),

    DISPENSER(InventoryType.DISPENSER, 9, false, 1),

    SHULKER_BOX(InventoryType.SHULKER_BOX, 27, false, 1),

    FURNACE(InventoryType.FURNACE, 3, false, 1),

    BLAST_FURNACE(InventoryType.BLAST_FURNACE, 3, false, 1),

    SMOKER(InventoryType.SMOKER, 3, false, 1),

    BREWING_STAND(InventoryType.BREWING, 5, false, 1),

    ENCHANTING(InventoryType.ENCHANTING, 2, false, 1),

    ANVIL(InventoryType.ANVIL, 3, false, 1),

    SMITHING(InventoryType.SMITHING, 3, false, 1),

    BEACON(InventoryType.BEACON, 1, false, 1),

    STONECUTTER(InventoryType.STONECUTTER, 2, false, 1),

    LOOM(InventoryType.LOOM, 4, false, 1),

    CARTOGRAPHY(InventoryType.CARTOGRAPHY, 3, false, 1),

    GRINDSTONE(InventoryType.GRINDSTONE, 3, false, 1),

    LECTERN(InventoryType.LECTERN, 1, false, 1),

    MERCHANT(InventoryType.MERCHANT, 3, false, 1);

    private final InventoryType bukkitType;
    private final int defaultSlots;
    private final boolean resizable;
    private final int maxRows;

    GuiWindowType(InventoryType bukkitType, int defaultSlots, boolean resizable, int maxRows) {
        this.bukkitType = bukkitType;
        this.defaultSlots = defaultSlots;
        this.resizable = resizable;
        this.maxRows = maxRows;
    }

    @NotNull
    public static GuiWindowType fromBukkitType(@NotNull InventoryType bukkitType) {
        for (GuiWindowType type : values()) {
            if (type.bukkitType == bukkitType) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unsupported inventory type: " + bukkitType);
    }

    public static boolean isSupported(@NotNull InventoryType bukkitType) {
        for (GuiWindowType type : values()) {
            if (type.bukkitType == bukkitType) {
                return true;
            }
        }
        return false;
    }

    @NotNull
    public InventoryType getBukkitType() {
        return bukkitType;
    }

    public int getDefaultSlots() {
        return defaultSlots;
    }

    public boolean isResizable() {
        return resizable;
    }

    public int getMaxRows() {
        return maxRows;
    }

    public int getSlotsForRows(int rows) {
        if (!resizable) {
            throw new UnsupportedOperationException(this + " is not resizable");
        }
        if (rows < 1 || rows > maxRows) {
            throw new IllegalArgumentException("Invalid rows: " + rows + " (must be 1-" + maxRows + ")");
        }
        return 9 * rows;
    }

    public boolean isSpecialContainer() {
        return switch (this) {
            case FURNACE, BLAST_FURNACE, SMOKER,
                 BREWING_STAND, ENCHANTING, ANVIL,
                 SMITHING, BEACON, STONECUTTER,
                 LOOM, CARTOGRAPHY, GRINDSTONE -> true;
            default -> false;
        };
    }

    public boolean isStorageContainer() {
        return switch (this) {
            case CHEST, HOPPER, DISPENSER, SHULKER_BOX -> true;
            default -> false;
        };
    }

    @NotNull
    public String getDisplayName() {
        return switch (this) {
            case CHEST -> "Chest";
            case HOPPER -> "Hopper";
            case DISPENSER -> "Dispenser";
            case SHULKER_BOX -> "Shulker Box";
            case FURNACE -> "Furnace";
            case BLAST_FURNACE -> "Blast Furnace";
            case SMOKER -> "Smoker";
            case BREWING_STAND -> "Brewing Stand";
            case ENCHANTING -> "Enchanting Table";
            case ANVIL -> "Anvil";
            case SMITHING -> "Smithing Table";
            case BEACON -> "Beacon";
            case STONECUTTER -> "Stonecutter";
            case LOOM -> "Loom";
            case CARTOGRAPHY -> "Cartography Table";
            case GRINDSTONE -> "Grindstone";
            case LECTERN -> "Lectern";
            case MERCHANT -> "Merchant";
        };
    }
}
