package gloomlib.gui.click;

import org.jetbrains.annotations.NotNull;

/**
 * GUI 点击类型枚举
 * <p>
 * 定义了 Minecraft GUI 中所有可能的点击类型，包括基础点击、
 * 快捷键组合、拖拽操作等。
 * <p>
 * 参考实现：
 * <ul>
 *   <li>InvUI: Click.java 和 CustomContainerMenu.java</li>
 *   <li>Triumph-GUI: GuiClick.java</li>
 * </ul>
 * 
 * @author GloomLib
 * @since 2.0
 * @see <a href=\"https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/Click.java\">InvUI Click.java</a>
 * @see <a href=\"https://github.com/triumphteam/triumph-gui/blob/update/v4/core/src/main/java/dev/triumphteam/gui/click/GuiClick.java\">Triumph-GUI GuiClick.java</a>
 */
public enum GuiClickType {
    /**
     * 左键点击
     */
    LEFT,
    
    /**
     * 右键点击
     */
    RIGHT,
    
    /**
     * 中键点击（创造模式）
     */
    MIDDLE,
    
    /**
     * Shift + 左键点击（快速移动）
     */
    SHIFT_LEFT,
    
    /**
     * Shift + 右键点击（快速移动半堆）
     */
    SHIFT_RIGHT,
    
    /**
     * 数字键 1-9（与快捷栏交换）
     * <p>
     * 需要配合 {@link GuiClickContext#hotbarButton()} 使用
     */
    NUMBER_KEY,
    
    /**
     * F 键（与副手交换）
     */
    SWAP_OFFHAND,
    
    /**
     * Q 键（丢弃单个物品）
     */
    DROP,
    
    /**
     * Ctrl + Q（丢弃整个堆叠）
     */
    CONTROL_DROP,
    
    /**
     * 双击（收集相同物品）
     */
    DOUBLE_CLICK,
    
    /**
     * 创造模式特殊点击
     */
    CREATIVE,
    
    /**
     * 窗口边界外左键点击
     */
    WINDOW_BORDER_LEFT,
    
    /**
     * 窗口边界外右键点击
     */
    WINDOW_BORDER_RIGHT,
    
    /**
     * 拖拽操作 - 左键拖拽（平均分配）
     */
    DRAG_LEFT,
    
    /**
     * 拖拽操作 - 右键拖拽（每个槽位一个）
     */
    DRAG_RIGHT,
    
    /**
     * 拖拽操作 - 中键拖拽（每个槽位完整堆叠，创造模式）
     */
    DRAG_MIDDLE,
    
    /**
     * 未知点击类型
     */
    UNKNOWN;
    
    /**
     * 是否为拖拽类型
     * 
     * @return 如果是拖拽类型返回 true
     */
    public boolean isDrag() {
        return this == DRAG_LEFT || this == DRAG_RIGHT || this == DRAG_MIDDLE;
    }
    
    /**
     * 是否为 Shift 点击
     * 
     * @return 如果是 Shift 点击返回 true
     */
    public boolean isShift() {
        return this == SHIFT_LEFT || this == SHIFT_RIGHT;
    }
    
    /**
     * 是否为左键相关
     * 
     * @return 如果是左键相关返回 true
     */
    public boolean isLeft() {
        return this == LEFT || this == SHIFT_LEFT || this == WINDOW_BORDER_LEFT || this == DRAG_LEFT;
    }
    
    /**
     * 是否为右键相关
     * 
     * @return 如果是右键相关返回 true
     */
    public boolean isRight() {
        return this == RIGHT || this == SHIFT_RIGHT || this == WINDOW_BORDER_RIGHT || this == DRAG_RIGHT;
    }
    
    /**
     * 是否涉及快捷键（数字键或副手交换）
     * 
     * @return 如果涉及快捷键返回 true
     */
    public boolean isHotkey() {
        return this == NUMBER_KEY || this == SWAP_OFFHAND;
    }
    
    /**
     * 是否为丢弃操作
     * 
     * @return 如果是丢弃操作返回 true
     */
    public boolean isDrop() {
        return this == DROP || this == CONTROL_DROP;
    }
    
    /**
     * 获取描述性名称
     * 
     * @return 描述性名称
     */
    @NotNull
    public String getDisplayName() {
        return switch (this) {
            case LEFT -> "左键";
            case RIGHT -> "右键";
            case MIDDLE -> "中键";
            case SHIFT_LEFT -> "Shift+左键";
            case SHIFT_RIGHT -> "Shift+右键";
            case NUMBER_KEY -> "数字键";
            case SWAP_OFFHAND -> "F键";
            case DROP -> "Q键";
            case CONTROL_DROP -> "Ctrl+Q";
            case DOUBLE_CLICK -> "双击";
            case CREATIVE -> "创造模式";
            case WINDOW_BORDER_LEFT -> "窗口外左键";
            case WINDOW_BORDER_RIGHT -> "窗口外右键";
            case DRAG_LEFT -> "左键拖拽";
            case DRAG_RIGHT -> "右键拖拽";
            case DRAG_MIDDLE -> "中键拖拽";
            case UNKNOWN -> "未知";
        };
    }
}
