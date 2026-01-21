/**
 * 状态管理包 - 响应式状态容器
 * <p>
 * 提供多种响应式状态管理方案：
 * <ul>
 *   <li>{@link gloomlib.gui.state.Property} - 不可变属性接口</li>
 *   <li>{@link gloomlib.gui.state.MutableProperty} - 可变属性接口</li>
 *   <li>{@link gloomlib.gui.state.ReactiveState} - 传统响应式状态（兼容性保留）</li>
 *   <li>{@link gloomlib.gui.state.AsyncState} - 异步数据加载状态</li>
 * </ul>
 * 
 * <h3>设计参考</h3>
 * <ul>
 *   <li>InvUI 2.x Property 系统</li>
 *   <li>文件：{@code invui/src/main/java/xyz/xenondevs/invui/state/Property.java}</li>
 *   <li>文件：{@code invui/src/main/java/xyz/xenondevs/invui/state/MutableProperty.java}</li>
 * </ul>
 * 
 * <h3>使用建议</h3>
 * <p>
 * 新代码应优先使用 {@link gloomlib.gui.state.Property} 和 {@link gloomlib.gui.state.MutableProperty}，
 * 它们提供更好的类型安全和API设计。{@link gloomlib.gui.state.ReactiveState} 保留用于向后兼容。
 * 
 * @author GloomLib
 * @since 3.0
 */
package gloomlib.gui.state;
