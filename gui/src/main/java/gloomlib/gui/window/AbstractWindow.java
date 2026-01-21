package gloomlib.gui.window;

import gloomlib.gui.GloomGuiManager;
import gloomlib.gui.api.GloomGui;
import gloomlib.gui.config.GuiConfiguration;
import gloomlib.gui.util.GuiSecurity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractWindow implements Window, InventoryHolder {

    protected final Player viewer;
    protected final Component title;
    protected final GloomGui gui;
    protected Inventory inventory;
    protected final AtomicBoolean isClosed = new AtomicBoolean(false);

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

        GuiConfiguration config = gui.getConfiguration();
        if (config.updateStrategy() == GuiConfiguration.UpdateStrategy.PERIODIC) {
            GloomGuiManager.register(this, config.tickRate());
        }

        viewer.openInventory(inventory);
    }

    @Override
    public void close() {
        if (!isClosed.get()) {
            viewer.closeInventory();
        }
    }

    @Override
    public void handleClose(InventoryCloseEvent event) {
        isClosed.set(true);
        GloomGuiManager.unregister(this);
        gui.handleClose(event);
        GuiSecurity.cleanInventory((Player) event.getPlayer());
    }

    @Override
    public void tick() {
        if (isClosed.get()) return;
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

    @Override
    public Player getViewer() {
        return viewer;
    }

    @Override
    public boolean isClosed() {
        return isClosed.get();
    }

    @SuppressWarnings("deprecation")
   public void updateTitle(final Component title) {
        final List<HumanEntity> viewers = getInventory().getViewers();
        if (!viewers.isEmpty()) {
            final String legacyTitle = LegacyComponentSerializer.legacySection().serialize(title);
            for (HumanEntity humanEntity : viewers) {
                humanEntity.getOpenInventory().setTitle(legacyTitle);
            }
        }
    }

    @SuppressWarnings("deprecation")
    public void updateTitle(@NotNull final HumanEntity viewer, final Component title) {
        final InventoryView openView = viewer.getOpenInventory();
        if (openView.getTopInventory().equals(getInventory())) {
            openView.setTitle(LegacyComponentSerializer.legacySection().serialize(title));
        }
    }
}