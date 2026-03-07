package gloomlib.gui.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Utility class for Bundle item operations.
 */
public final class BundleUtils {

    private BundleUtils() {
    }

    /**
     * Checks if an item is a Bundle.
     *
     * @param item the item to check
     * @return true if it is a Bundle
     */
    public static boolean isBundle(@Nullable ItemStack item) {
        return !GuiItemUtils.isEmpty(item) && item.getType() == Material.BUNDLE;
    }

    /**
     * Attempts to insert an item into a Bundle.
     *
     * @param bundle   the Bundle item
     * @param toInsert the item to insert
     * @return the insert result
     */
    @NotNull
    public static InsertResult insertIntoBundle(@NotNull ItemStack bundle, @NotNull ItemStack toInsert) {
        if (!isBundle(bundle) || GuiItemUtils.isEmpty(toInsert)) {
            return new InsertResult(bundle, toInsert);
        }

        if (bundle.getItemMeta() instanceof BundleMeta bundleMeta) {
            List<ItemStack> contents = bundleMeta.getItems();
            bundleMeta.addItem(toInsert.clone());
            List<ItemStack> newContents = bundleMeta.getItems();

            ItemStack newBundle = bundle.clone();
            newBundle.setItemMeta(bundleMeta);

            int inserted = getTotalAmount(newContents) - getTotalAmount(contents);
            ItemStack remaining = null;
            if (inserted < toInsert.getAmount()) {
                remaining = toInsert.clone();
                remaining.setAmount(toInsert.getAmount() - inserted);
            }

            return new InsertResult(newBundle, remaining);
        }

        return new InsertResult(bundle, toInsert);
    }

    /**
     * Extracts the first item from a Bundle.
     *
     * @param bundle the Bundle item
     * @return the extract result
     */
    @NotNull
    public static ExtractResult extractFromBundle(@NotNull ItemStack bundle) {
        if (!isBundle(bundle)) {
            return new ExtractResult(bundle, null);
        }

        if (bundle.getItemMeta() instanceof BundleMeta bundleMeta) {
            List<ItemStack> contents = bundleMeta.getItems();
            if (contents.isEmpty()) {
                return new ExtractResult(bundle, null);
            }

            ItemStack extracted = contents.get(0).clone();
            contents.remove(0);
            bundleMeta.setItems(contents);

            ItemStack newBundle = bundle.clone();
            newBundle.setItemMeta(bundleMeta);
            return new ExtractResult(newBundle, extracted);
        }

        return new ExtractResult(bundle, null);
    }

    private static int getTotalAmount(List<ItemStack> items) {
        return items.stream().mapToInt(ItemStack::getAmount).sum();
    }

    /**
     * Result of a Bundle insert operation.
     *
     * @param newBundle the new Bundle item
     * @param remaining the remaining item
     */
    public record InsertResult(@NotNull ItemStack newBundle, @Nullable ItemStack remaining) {
        /**
         * Checks if there are remaining items.
         *
         * @return true if items remain
         */
        public boolean hasRemaining() {
            return !GuiItemUtils.isEmpty(remaining);
        }
    }

    /**
     * Result of a Bundle extract operation.
     *
     * @param newBundle the new Bundle item
     * @param extracted the extracted item
     */
    public record ExtractResult(@NotNull ItemStack newBundle, @Nullable ItemStack extracted) {
        /**
         * Checks if an item was extracted.
         *
         * @return true if extracted
         */
        public boolean wasExtracted() {
            return !GuiItemUtils.isEmpty(extracted);
        }
    }
}
