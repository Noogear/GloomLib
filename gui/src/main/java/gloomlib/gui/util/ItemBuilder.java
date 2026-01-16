package gloomlib.gui.util;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta meta;

    private ItemBuilder(@NotNull ItemStack itemStack) {
        this.itemStack = itemStack;
        this.meta = itemStack.getItemMeta();
    }

    public static ItemBuilder from(@NotNull Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    public static ItemBuilder from(@NotNull ItemStack itemStack) {
        return new ItemBuilder(itemStack.clone());
    }

    public static SkullBuilder skull() {
        return new SkullBuilder();
    }

    public ItemBuilder name(@Nullable Component name) {
        if (meta != null && name != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        }
        return this;
    }

    public ItemBuilder lore(@NotNull List<Component> lore) {
        if (meta != null) {
            meta.lore(lore.stream()
                    .map(c -> c.decoration(TextDecoration.ITALIC, false))
                    .collect(Collectors.toList()));
        }
        return this;
    }

    public ItemBuilder lore(@NotNull Component... lore) {
        return lore(Arrays.asList(lore));
    }

    public ItemBuilder amount(int amount) {
        itemStack.setAmount(amount);
        return this;
    }

    public ItemBuilder damage(int damage) {
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(damage);
        }
        return this;
    }

    public ItemBuilder modelData(int data) {
        if (meta != null) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    public ItemBuilder enchant(@NotNull Enchantment enchantment, int level) {
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    public ItemBuilder removeEnchant(@NotNull Enchantment enchantment) {
        if (meta != null) {
            meta.removeEnchant(enchantment);
        }
        return this;
    }

    public ItemBuilder flags(@NotNull ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder removeFlags(@NotNull ItemFlag... flags) {
        if (meta != null) {
            meta.removeItemFlags(flags);
        }
        return this;
    }

    public ItemBuilder glow(boolean glow) {
        if (glow) {
            enchant(Enchantment.UNBREAKING, 1);
            flags(ItemFlag.HIDE_ENCHANTS);
        } else {
            removeEnchant(Enchantment.UNBREAKING);
            removeFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    public <T, Z> ItemBuilder pdc(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z value) {
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, type, value);
        }
        return this;
    }

    public <M extends ItemMeta> ItemBuilder editMeta(@NotNull Class<M> metaClass, @NotNull Consumer<M> consumer) {
        if (metaClass.isInstance(meta)) {
            consumer.accept(metaClass.cast(meta));
        }
        return this;
    }

    public ItemBuilder editMeta(@NotNull Consumer<ItemMeta> consumer) {
        if (meta != null) {
            consumer.accept(meta);
        }
        return this;
    }

    @NotNull
    public ItemStack build() {
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    public static class SkullBuilder extends ItemBuilder {

        private SkullBuilder() {
            super(new ItemStack(Material.PLAYER_HEAD));
        }

        public SkullBuilder owner(@NotNull OfflinePlayer player) {
            editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
            return this;
        }

        public SkullBuilder texture(@NotNull String base64) {
            editMeta(SkullMeta.class, meta -> {
                PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
                profile.getProperties().add(new ProfileProperty("textures", base64));
                meta.setPlayerProfile(profile);
            });
            return this;
        }
    }
}