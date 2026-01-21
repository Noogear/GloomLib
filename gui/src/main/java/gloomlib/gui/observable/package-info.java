/**
 * 多观察者模式核心包
 * <p>
 * 提供 {@link gloomlib.gui.observable.Observable} 和 {@link gloomlib.gui.observable.Observer} 接口，
 * 支持多个窗口同时观察同一个 GUI 实例的变化。
 * <p>
 * 这是实现共享 GUI（如商店、拍卖行）的核心机制。多个玩家可以同时查看同一个 GUI 实例，
 * 当 GUI 状态变化时，所有观察者都会收到通知并更新显示。
 * 
 * <h3>设计参考</h3>
 * <ul>
 *   <li>InvUI 2.x Observable/Observer 模式</li>
 *   <li>文件：{@code invui/src/main/java/xyz/xenondevs/invui/Observable.java}</li>
 *   <li>文件：{@code invui/src/main/java/xyz/xenondevs/invui/Observer.java}</li>
 * </ul>
 * 
 * @author GloomLib
 * @since 3.0
 */
package gloomlib.gui.observable;
