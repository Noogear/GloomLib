package gloomlib.gui.interaction;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 槽位优先级接口 - 用于 Shift+点击时控制物品优先放置的位置
 * <p>
 * 参考 InvUI 的优先级系统，允许组件定义接受物品的优先级。
 * 优先级越高，Shift+点击时越优先尝试放置到该槽位。
 * 
 * @author GloomLib
 * @since 2.0
 */
public interface SlotPriority {

    /**
     * 最低优先级 - 不接受物品
     */
    int PRIORITY_NONE = -1;

    /**
     * 低优先级 - 最后考虑
     */
    int PRIORITY_LOW = 0;

    /**
     * 普通优先级 - 默认
     */
    int PRIORITY_NORMAL = 50;

    /**
     * 高优先级 - 优先考虑
     */
    int PRIORITY_HIGH = 100;

    /**
     * 最高优先级 - 最优先考虑
     */
    int PRIORITY_HIGHEST = 200;

    /**
     * 获取槽位接受指定物品的优先级
     * 
     * @param slot 槽位索引
     * @param item 要放置的物品
     * @return 优先级值，PRIORITY_NONE 表示不接受
     */
    int getPriority(int slot, @Nullable ItemStack item);

    /**
     * 判断槽位是否接受指定物品
     * 
     * @param slot 槽位索引
     * @param item 要放置的物品
     * @return 是否接受
     */
    default boolean acceptsItem(int slot, @Nullable ItemStack item) {
        return getPriority(slot, item) > PRIORITY_NONE;
    }

    /**
     * 创建默认优先级策略（所有槽位普通优先级）
     */
    @NotNull
    static SlotPriority normal() {
        return (slot, item) -> PRIORITY_NORMAL;
    }

    /**
     * 创建拒绝所有物品的策略
     */
    @NotNull
    static SlotPriority none() {
        return (slot, item) -> PRIORITY_NONE;
    }

    /**
     * 创建高优先级策略
     */
    @NotNull
    static SlotPriority high() {
        return (slot, item) -> PRIORITY_HIGH;
    }
}
