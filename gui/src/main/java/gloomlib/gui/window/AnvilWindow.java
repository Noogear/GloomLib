package gloomlib.gui.window;

import gloomlib.gui.api.GloomGui;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.function.Consumer;

public class AnvilWindow extends AbstractWindow {

    private final Consumer<String> inputHandler;

    public AnvilWindow(Player viewer, Component title, GloomGui gui, Consumer<String> inputHandler) {
        super(viewer, title, gui);
        this.inputHandler = inputHandler;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(this, InventoryType.ANVIL, title);
    }

    public void handleRename(String text) {
        if (inputHandler != null) {
            inputHandler.accept(text);
        }
    }

}