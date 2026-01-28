package gloomlib.gui.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bundle item utility class (MC 1.21+).
 * Handles Bundle container insertion and extraction logic.
 * Uses reflection and version detection since Paper API may vary across versions.
 */
public final class BundleUtils {

    private static final boolean BUNDLE_SUPPORTED;
    private static Class<?> bundleContentsClass;
    private static Class<?> dataComponentTypesClass;

    static {
        boolean supported = false;
        try {
            bundleContentsClass = Class.forName("org.bukkit.inventory.meta.BundleMeta");
            dataComponentTypesClass = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            supported = true;
        } catch (ClassNotFoundException e) {
        }
        BUNDLE_SUPPORTED = supported;
    }

    private BundleUtils() {
    }

    /**
     * Checks if the current server supports Bundle.
     *
     * @return true if Bundle is supported
     */
    public static boolean isBundleSupported() {
        return BUNDLE_SUPPORTED;
    }

    /**
     * Checks if an item is a Bundle.
     *
     * @param item the item to check
     * @return true if the item is a Bundle
     */
    public static boolean isBundle(@Nullable ItemStack item) {
        if (!BUNDLE_SUPPORTED || GuiItemUtils.isEmpty(item)) {
            return false;
        }
        return item.getType() == Material.BUNDLE;
    }

    /**
     * Attempts to insert an item into a Bundle.
     *
     * @param bundle the Bundle item
     * @param toInsert the item to insert
     * @return the insert result [new Bundle, remaining item]
     */
    @NotNull
    public static InsertResult insertIntoBundle(@NotNull ItemStack bundle, @NotNull ItemStack toInsert) {
        if (!isBundle(bundle) || GuiItemUtils.isEmpty(toInsert)) {
            return new InsertResult(bundle, toInsert);
        }

        try {
            if (bundle.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta bundleMeta) {
                java.util.List<ItemStack> contents = bundleMeta.getItems();
                
                ItemStack cloned = toInsert.clone();
                bundleMeta.addItem(cloned);
                
                java.util.List<ItemStack> newContents = bundleMeta.getItems();
                
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
        } catch (Exception e) {
        }

        return new InsertResult(bundle, toInsert);
    }

    /**
     * Extracts the first item from a Bundle.
     *
     * @param bundle the Bundle item
     * @return the extract result [new Bundle, extracted item]
     */
    @NotNull
    public static ExtractResult extractFromBundle(@NotNull ItemStack bundle) {
        if (!isBundle(bundle)) {
            return new ExtractResult(bundle, null);
        }

        try {
            if (bundle.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta bundleMeta) {
                java.util.List<ItemStack> contents = bundleMeta.getItems();
                
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
        } catch (Exception e) {
        }

        return new ExtractResult(bundle, null);
    }

    /**
     * Calculates the total amount of items in a Bundle.
     *
     * @param items the list of items
     * @return the total amount
     */
    private static int getTotalAmount(java.util.List<ItemStack> items) {
        return items.stream()
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    /**
     * Bundle insert result.
     *
     * @param newBundle the new Bundle after insertion
     * @param remaining the remaining item that could not be inserted
     */
    public record InsertResult(@NotNull ItemStack newBundle, @Nullable ItemStack remaining) {
        /**
         * Checks if there are remaining items that could not be inserted.
         *
         * @return {@code true} if remaining items exist, {@code false} otherwise
         */
        public boolean hasRemaining() {
            return !GuiItemUtils.isEmpty(remaining);
        }
    }

    /**
     * Bundle extract result.
     *
     * @param newBundle the new Bundle after extraction
     * @param extracted the extracted item
     */
    public record ExtractResult(@NotNull ItemStack newBundle, @Nullable ItemStack extracted) {
        /**
         * Checks if an item was successfully extracted.
         *
         * @return {@code true} if extraction occurred, {@code false} otherwise
         */
        public boolean wasExtracted() {
            return !GuiItemUtils.isEmpty(extracted);
        }
    }
}
