package gloomlib.gui.window;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.util.GuiSecurity;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractWindow implements Window, InventoryHolder {

    protected final Player viewer;
    protected final Component title;
    protected final GloomGui gui;
    protected Inventory inventory;
    protected boolean isClosed = false;

    public AbstractWindow(Player viewer, Component title, GloomGui gui) {
        this.viewer = viewer;
        this.title = title;
        this.gui = gui;
    }

    protected abstract Inventory createInventory();

    @Override
    public void open() {
        this.inventory = createInventory();
        gui.bindToWindow(this);
        viewer.openInventory(inventory);
    }

    @Override
    public void close() {
        if (!isClosed) {
            viewer.closeInventory();
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        isClosed = true;
        gui.destroy();
        GuiSecurity.cleanInventory((Player) event.getPlayer());
    }

    @Override
    public void tick() {
        if (isClosed) return;
        gui.tick();
    }

    @Override
    public GloomGui getGui() {
        return gui;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player getViewer() {
        return viewer;
    }
}