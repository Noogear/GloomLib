package gloomlib.gui.observable;

import org.jetbrains.annotations.NotNull;

/**
 * 可观察对象接口 - 支持多观察者模式
 * <p>
 * 此接口允许多个 {@link Observer} 观察同一个对象的特定槽位变化。
 * 这是实现多玩家共享 GUI 的核心机制。
 * <p>
 * 设计参考：InvUI 2.x 的 Observable 接口
 * {@link <a href="https://github.com/NichtStudioCode/InvUI/blob/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/Observable.java">InvUI Observable.java</a>}
 * 
 * <h3>三参数观察机制</h3>
 * <ul>
 *   <li><b>who</b> - 观察者实例（通常是 Window）</li>
 *   <li><b>what</b> - 观察的槽位索引</li>
 *   <li><b>how</b> - 通知方式标识（用于传递给观察者）</li>
 * </ul>
 * 
 * @author GloomLib
 * @since 3.0
 * @see Observer
 */
public interface Observable {

    /**
     * 注册观察者监听特定槽位的变化
     * 
     * @param who  观察者实例
     * @param what 要观察的槽位索引
     * @param how  通知方式标识（将在 {@link Observer#notifyUpdate(int)} 中传递）
     */
    void addObserver(@NotNull Observer who, int what, int how);

    /**
     * 移除观察者对特定槽位的监听
     * 
     * @param who  观察者实例
     * @param what 要取消观察的槽位索引
     * @param how  通知方式标识
     */
    void removeObserver(@NotNull Observer who, int what, int how);

    /**
     * 移除观察者的所有监听
     * 
     * @param who 要移除的观察者实例
     */
    void removeAllObservers(@NotNull Observer who);

    /**
     * 获取特定槽位的更新周期（毫秒）
     * <p>
     * 如果返回值 > 0，系统将定期自动查询该槽位的变化并通知观察者。
     * 默认返回 -1 表示不自动更新。
     * 
     * @param what 槽位索引
     * @return 更新周期（毫秒），-1 表示不自动更新
     */
    default int getUpdatePeriod(int what) {
        return -1;
    }
}
