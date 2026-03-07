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

/**
 * Fluent builder for constructing custom Bukkit ItemStack instances.
 */
public class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta meta;

    private ItemBuilder(@NotNull ItemStack itemStack) {
        this.itemStack = itemStack;
        this.meta = itemStack.getItemMeta();
    }

    /**
     * Creates a new builder for the specified material.
     *
     * @param material the material
     * @return a new builder
     */
    public static ItemBuilder from(@NotNull Material material) {
        return new ItemBuilder(new ItemStack(material));
    }

    /**
     * Creates a new builder from an existing item stack.
     *
     * @param itemStack the item stack
     * @return a new builder
     */
    public static ItemBuilder from(@NotNull ItemStack itemStack) {
        return new ItemBuilder(itemStack.clone());
    }

    /**
     * Creates a new skull builder.
     *
     * @return a new skull builder
     */
    public static SkullBuilder skull() {
        return new SkullBuilder();
    }

    /**
     * Sets the display name.
     *
     * @param name the name
     * @return this builder
     */
    public ItemBuilder name(@Nullable Component name) {
        if (meta != null && name != null) {
            meta.displayName(name.decoration(TextDecoration.ITALIC, false));
        }
        return this;
    }

    /**
     * Sets the lore.
     *
     * @param lore the lore
     * @return this builder
     */
    public ItemBuilder lore(@NotNull List<Component> lore) {
        if (meta != null) {
            meta.lore(lore.stream()
                    .map(c -> c.decoration(TextDecoration.ITALIC, false))
                    .collect(Collectors.toList()));
        }
        return this;
    }

    /**
     * Sets the lore.
     *
     * @param lore the lore
     * @return this builder
     */
    public ItemBuilder lore(@NotNull Component... lore) {
        return lore(Arrays.asList(lore));
    }

    /**
     * Sets the amount.
     *
     * @param amount the amount
     * @return this builder
     */
    public ItemBuilder amount(int amount) {
        itemStack.setAmount(amount);
        return this;
    }

    /**
     * Sets the damage.
     *
     * @param damage the damage
     * @return this builder
     */
    public ItemBuilder damage(int damage) {
        if (meta instanceof Damageable damageable) {
            damageable.setDamage(damage);
        }
        return this;
    }

    /**
     * Sets the custom model data.
     *
     * @param data the model data
     * @return this builder
     */
    @SuppressWarnings("deprecation")
    public ItemBuilder modelData(int data) {
        if (meta != null) {
            meta.setCustomModelData(data);
        }
        return this;
    }

    /**
     * Adds an enchantment.
     *
     * @param enchantment the enchantment
     * @param level       the level
     * @return this builder
     */
    public ItemBuilder enchant(@NotNull Enchantment enchantment, int level) {
        if (meta != null) {
            meta.addEnchant(enchantment, level, true);
        }
        return this;
    }

    /**
     * Removes an enchantment.
     *
     * @param enchantment the enchantment
     * @return this builder
     */
    public ItemBuilder removeEnchant(@NotNull Enchantment enchantment) {
        if (meta != null) {
            meta.removeEnchant(enchantment);
        }
        return this;
    }

    /**
     * Adds item flags.
     *
     * @param flags the flags
     * @return this builder
     */
    public ItemBuilder flags(@NotNull ItemFlag... flags) {
        if (meta != null) {
            meta.addItemFlags(flags);
        }
        return this;
    }

    /**
     * Removes item flags.
     *
     * @param flags the flags
     * @return this builder
     */
    public ItemBuilder removeFlags(@NotNull ItemFlag... flags) {
        if (meta != null) {
            meta.removeItemFlags(flags);
        }
        return this;
    }

    /**
     * Toggles the glow effect using Paper's enchantment glint override.
     *
     * @param glow true to glow
     * @return this builder
     */
    public ItemBuilder glow(boolean glow) {
        if (meta != null) {
            meta.setEnchantmentGlintOverride(glow);
        }
        return this;
    }

    /**
     * Sets persistent data.
     *
     * @param key   the key
     * @param type  the data type
     * @param value the value
     * @param <T>   storage type
     * @param <Z>   object type
     * @return this builder
     */
    public <T, Z> ItemBuilder pdc(@NotNull NamespacedKey key, @NotNull PersistentDataType<T, Z> type, @NotNull Z value) {
        if (meta != null) {
            meta.getPersistentDataContainer().set(key, type, value);
        }
        return this;
    }

    /**
     * Edits the item meta.
     *
     * @param metaClass the meta class
     * @param consumer  the editor
     * @param <M>       meta type
     * @return this builder
     */
    public <M extends ItemMeta> ItemBuilder editMeta(@NotNull Class<M> metaClass, @NotNull Consumer<M> consumer) {
        if (metaClass.isInstance(meta)) {
            consumer.accept(metaClass.cast(meta));
        }
        return this;
    }

    /**
     * Edits the item meta.
     *
     * @param consumer the editor
     * @return this builder
     */
    public ItemBuilder editMeta(@NotNull Consumer<ItemMeta> consumer) {
        if (meta != null) {
            consumer.accept(meta);
        }
        return this;
    }

    /**
     * Builds the item stack.
     *
     * @return the built item stack
     */
    @NotNull
    public ItemStack build() {
        itemStack.setItemMeta(meta);
        return itemStack;
    }

    /**
     * Builder for player skulls.
     */
    public static class SkullBuilder extends ItemBuilder {

        private SkullBuilder() {
            super(new ItemStack(Material.PLAYER_HEAD));
        }

        /**
         * Sets the skull owner.
         *
         * @param player the player
         * @return this builder
         */
        public SkullBuilder owner(@NotNull OfflinePlayer player) {
            editMeta(SkullMeta.class, meta -> meta.setOwningPlayer(player));
            return this;
        }

        /**
         * Sets the skull texture.
         *
         * @param base64 the base64 texture
         * @return this builder
         */
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
