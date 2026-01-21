package gloomlib.gui.util;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bundle 物品工具类 (MC 1.21+)
 * <p>
 * 处理 Bundle 容器的插入和取出逻辑。
 * 由于 Paper API 可能在不同版本有差异，使用反射和版本检测。
 * 
 * @author GloomLib
 * @since 2.0
 */
public final class BundleUtils {

    private static final boolean BUNDLE_SUPPORTED;
    private static Class<?> bundleContentsClass;
    private static Class<?> dataComponentTypesClass;

    static {
        boolean supported = false;
        try {
            // 尝试加载 MC 1.21+ 的 Bundle 相关类
            bundleContentsClass = Class.forName("org.bukkit.inventory.meta.BundleMeta");
            dataComponentTypesClass = Class.forName("io.papermc.paper.datacomponent.DataComponentTypes");
            supported = true;
        } catch (ClassNotFoundException e) {
            // MC 1.21 之前的版本不支持
        }
        BUNDLE_SUPPORTED = supported;
    }

    private BundleUtils() {
    }

    /**
     * 判断当前服务器是否支持 Bundle
     */
    public static boolean isBundleSupported() {
        return BUNDLE_SUPPORTED;
    }

    /**
     * 判断物品是否为 Bundle
     */
    public static boolean isBundle(@Nullable ItemStack item) {
        if (!BUNDLE_SUPPORTED || GuiItemUtils.isEmpty(item)) {
            return false;
        }
        return item.getType() == Material.BUNDLE;
    }

    /**
     * 尝试向 Bundle 中插入物品
     * 
     * @param bundle     Bundle 物品
     * @param toInsert   要插入的物品
     * @return 插入结果 [新的 Bundle, 剩余物品]
     */
    @NotNull
    public static InsertResult insertIntoBundle(@NotNull ItemStack bundle, @NotNull ItemStack toInsert) {
        if (!isBundle(bundle) || GuiItemUtils.isEmpty(toInsert)) {
            return new InsertResult(bundle, toInsert);
        }

        try {
            // 使用 Paper API 的 Bundle 操作（MC 1.21+）
            if (bundle.getItemMeta() instanceof org.bukkit.inventory.meta.BundleMeta bundleMeta) {
                java.util.List<ItemStack> contents = bundleMeta.getItems();
                
                // 尝试添加物品
                ItemStack cloned = toInsert.clone();
                bundleMeta.addItem(cloned);
                
                // 检查是否成功添加
                java.util.List<ItemStack> newContents = bundleMeta.getItems();
                
                ItemStack newBundle = bundle.clone();
                newBundle.setItemMeta(bundleMeta);
                
                // 计算剩余物品
                int inserted = getTotalAmount(newContents) - getTotalAmount(contents);
                ItemStack remaining = null;
                if (inserted < toInsert.getAmount()) {
                    remaining = toInsert.clone();
                    remaining.setAmount(toInsert.getAmount() - inserted);
                }
                
                return new InsertResult(newBundle, remaining);
            }
        } catch (Exception e) {
            // 版本不兼容或其他错误，返回原样
        }

        return new InsertResult(bundle, toInsert);
    }

    /**
     * 从 Bundle 中取出第一个物品
     * 
     * @param bundle Bundle 物品
     * @return 取出结果 [新的 Bundle, 取出的物品]
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
                
                // 取出第一个物品
                ItemStack extracted = contents.get(0).clone();
                contents.remove(0);
                
                bundleMeta.setItems(contents);
                ItemStack newBundle = bundle.clone();
                newBundle.setItemMeta(bundleMeta);
                
                return new ExtractResult(newBundle, extracted);
            }
        } catch (Exception e) {
            // 版本不兼容或其他错误
        }

        return new ExtractResult(bundle, null);
    }

    /**
     * 计算 Bundle 内物品的总数量
     */
    private static int getTotalAmount(java.util.List<ItemStack> items) {
        return items.stream()
                .mapToInt(ItemStack::getAmount)
                .sum();
    }

    /**
     * Bundle 插入结果
     */
    public record InsertResult(@NotNull ItemStack newBundle, @Nullable ItemStack remaining) {
        public boolean hasRemaining() {
            return !GuiItemUtils.isEmpty(remaining);
        }
    }

    /**
     * Bundle 取出结果
     */
    public record ExtractResult(@NotNull ItemStack newBundle, @Nullable ItemStack extracted) {
        public boolean wasExtracted() {
            return !GuiItemUtils.isEmpty(extracted);
        }
    }
}
