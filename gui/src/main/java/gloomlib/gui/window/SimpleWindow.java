package gloomlib.gui.window;

import gloomlib.gui.api.GloomGui;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

public class SimpleWindow extends AbstractWindow {

    private final InventoryType type;
    private final int size;

    public SimpleWindow(Player viewer, Component title, GloomGui gui, InventoryType type, int size) {
        super(viewer, title, gui);
        this.type = type;
        this.size = size;
    }

    @Override
    protected Inventory createInventory() {
        if (type == InventoryType.CHEST) {
            if (size <= 0 || size > 54 || size % 9 != 0) {
                throw new IllegalArgumentException("Chest inventory size must be a multiple of 9 and between 9 and 54. Given: " + size);
            }
            return Bukkit.createInventory(this, size, title);
        } else {
            return Bukkit.createInventory(this, type, title);
        }
    }
}