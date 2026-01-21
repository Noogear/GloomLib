package gloomlib.gui.window;

import gloomlib.gui.api.GloomGui;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;

/**
 * GUI 窗口接口
 * 
 * @author GloomLib
 * @since 2.0
 */
public interface Window {
    /**
     * 打开窗口
     */
    void open();

    /**
     * 关闭窗口
     */
    void close();

    /**
     * 获取 GUI 实例
     * 
     * @return GUI 实例
     */
    GloomGui getGui();

    /**
     * 获取查看此窗口的玩家
     * 
     * @return 玩家实例
     */
    Player getViewer();

    /**
     * 检查窗口是否已关闭
     * 
     * @return 如果窗口已关闭则返回 true
     */
    boolean isClosed();

    /**
     * 处理窗口关闭事件
     * 
     * @param event 关闭事件
     */
    void handleClose(InventoryCloseEvent event);

    /**
     * 窗口 tick 更新
     */
    void tick();
}