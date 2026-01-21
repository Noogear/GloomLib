package gloomlib.gui.observable;

/**
 * 观察者接口 - 接收来自 {@link Observable} 的更新通知
 * <p>
 * 实现此接口的类可以观察 {@link Observable} 对象的变化。
 * 当被观察的槽位发生变化时，{@link #notifyUpdate(int)} 方法会被调用。
 * <p>
 * 设计参考：InvUI 2.x 的 Observer 接口
 * {@link <a href="https://github.com/NichtStudiocode/InvUI/blob/ver/2.x/invui/src/main/java/xyz/xenondevs/invui/Observer.java">InvUI Observer.java</a>}
 * 
 * <h3>典型用法</h3>
 * <pre>{@code
 * public class MyWindow implements Observer {
 *     private final GloomGui gui;
 *     private final Inventory inventory;
 *     
 *     public void open() {
 *         // 观察所有槽位
 *         for (int slot = 0; slot < gui.getSize(); slot++) {
 *             gui.addObserver(this, slot, slot);
 *         }
 *     }
 *     
 *     @Override
 *     public void notifyUpdate(int slot) {
 *         // 更新对应槽位的物品显示
 *         ItemStack newItem = gui.renderSlot(slot);
 *         inventory.setItem(slot, newItem);
 *     }
 * }
 * }</pre>
 * 
 * @author GloomLib
 * @since 3.0
 * @see Observable
 */
public interface Observer {

    /**
     * 接收更新通知
     * <p>
 * 当被观察的槽位发生变化时，此方法会被调用。
     * 参数值对应 {@link Observable#addObserver(Observer, int, int)} 中的 'how' 参数。
     * 
     * @param how 通知方式标识（通常用槽位索引）
     */
    void notifyUpdate(int how);
}
