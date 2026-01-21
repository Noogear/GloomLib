/**
 * 窗口系统包 - GUI 窗口实现
 * <p>
 * 提供窗口抽象和具体实现：
 * <ul>
 *   <li>{@link gloomlib.gui.window.Window} - 窗口接口</li>
 *   <li>{@link gloomlib.gui.window.AbstractWindow} - 抽象窗口基类（实现 {@link gloomlib.gui.observable.Observer}）</li>
 *   <li>{@link gloomlib.gui.window.SimpleWindow} - 标准箱子窗口</li>
 *   <li>{@link gloomlib.gui.window.AnvilWindow} - 铁砧输入窗口</li>
 * </ul>
 * 
 * <h3>多观察者支持</h3>
 * <p>
 * 从 3.0 版本开始，窗口实现 {@link gloomlib.gui.observable.Observer} 接口，
 * 支持多个窗口同时观察同一个 {@link gloomlib.gui.api.GloomGui} 实例。
 * 
 * <h3>设计参考</h3>
 * <ul>
 *   <li>InvUI 2.x 窗口系统</li>
 *   <li>文件：{@code invui/src/main/java/xyz/xenondevs/invui/window/AbstractWindow.java}</li>
 * </ul>
 * 
 * @author GloomLib
 * @since 2.0
 */
package gloomlib.gui.window;
