package gloomlib.gui.util;

import gloomlib.gui.GloomGuiManager;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public class GuiSecurity {

    public static final NamespacedKey LOCK_KEY = new NamespacedKey(GloomGuiManager.getPlugin(), "gui_item_lock");

    public static boolean isLocked(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(LOCK_KEY, PersistentDataType.BYTE);
    }

    public static ItemStack markAsGuiItem(ItemStack item) {
        item.editMeta(meta ->
                meta.getPersistentDataContainer().set(LOCK_KEY, PersistentDataType.BYTE, (byte) 1)
        );
        return item;
    }

    public static void cleanInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (isLocked(item)) {
                item.setAmount(0);
            }
        }
    }
}