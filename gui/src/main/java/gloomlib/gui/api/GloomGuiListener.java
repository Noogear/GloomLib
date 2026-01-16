package gloomlib.gui.listener;

import gloomlib.gui.api.GloomGui;
import gloomlib.gui.util.GuiSecurity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.InventoryHolder;

public class GloomGuiListener implements Listener {

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GloomGui gui) {
            gui.handleClick(event);
        } else {
            if (GuiSecurity.isLocked(event.getCurrentItem()) || GuiSecurity.isLocked(event.getCursor())) {
                event.setCancelled(true);
                event.setCurrentItem(null);
                event.getWhoClicked().setItemOnCursor(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GloomGui gui) {
            gui.handleDrag(event);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof GloomGui gui) {
            gui.handleClose(event);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        if (GuiSecurity.isLocked(event.getItemDrop().getItemStack())) {
            event.getItemDrop().remove();
        }
    }

    @EventHandler
    public void onSwapHand(PlayerSwapHandItemsEvent event) {
        if (GuiSecurity.isLocked(event.getOffHandItem()) || GuiSecurity.isLocked(event.getMainHandItem())) {
            event.setCancelled(true);
            GuiSecurity.cleanInventory(event.getPlayer());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(GuiSecurity::isLocked);
    }
}