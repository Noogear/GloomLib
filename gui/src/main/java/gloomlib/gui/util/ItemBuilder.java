package gloomlib.gui.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

public class ItemBuilder {

    private final ItemStack itemStack;

    private ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material);
    }

    private ItemBuilder(ItemStack item) {
        this.itemStack = item.clone();
    }

    public static ItemBuilder from(Material material) {
        return new ItemBuilder(material);
    }

    public static ItemBuilder from(ItemStack item) {
        return new ItemBuilder(item);
    }

    public ItemBuilder name(Component name) {
        itemStack.editMeta(meta ->
                meta.displayName(name.decoration(TextDecoration.ITALIC, false))
        );
        return this;
    }

    public ItemBuilder lore(Component... lines) {
        itemStack.editMeta(meta -> meta.lore(Arrays.asList(lines)));
        return this;
    }

    public ItemBuilder lore(List<Component> lines) {
        itemStack.editMeta(meta -> meta.lore(lines));
        return this;
    }

    public ItemBuilder amount(int amount) {
        itemStack.setAmount(amount);
        return this;
    }

    public ItemBuilder customModelData(int data) {
        itemStack.editMeta(meta -> meta.setCustomModelData(data));
        return this;
    }

    public ItemBuilder flags(ItemFlag... flags) {
        itemStack.editMeta(meta -> meta.addItemFlags(flags));
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        itemStack.editMeta(meta -> {
            if (glow) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            } else {
                meta.removeEnchant(org.bukkit.enchantments.Enchantment.DURABILITY);
                meta.removeItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
        });
        return this;
    }

    public ItemBuilder lock() {
        itemStack.editMeta(meta -> {
            meta.getPersistentDataContainer().set(
                    GuiSecurity.LOCK_KEY,
                    PersistentDataType.BYTE,
                    (byte) 1
            );
        });
        return this;
    }

    public <M extends ItemMeta> ItemBuilder modifyMeta(Class<M> metaClass, Consumer<M> modifier) {
        itemStack.editMeta(metaClass, modifier);
        return this;
    }

    public ItemStack build() {
        return itemStack;
    }
}