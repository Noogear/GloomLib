package gloomlib.gui.window;

import gloomlib.gui.api.GloomGui;
import org.bukkit.event.inventory.InventoryCloseEvent;

public interface Window {
    void open();

    void close();

    GloomGui getGui();

    void handleClose(InventoryCloseEvent event);

    void tick();
}