/**
 * 组件系统包 - 可复用的 GUI 组件
 * <p>
 * 提供基础组件接口和内置组件实现：
 * <ul>
 *   <li>{@link gloomlib.gui.component.GloomComponent} - 组件基础接口</li>
 *   <li>{@link gloomlib.gui.component.Components} - 组件工厂和便捷方法</li>
 * </ul>
 * 
 * <h3>内置组件</h3>
 * <ul>
 *   <li>{@link gloomlib.gui.component.builtin.AnimatedComponent} - 帧动画组件</li>
 *   <li>{@link gloomlib.gui.component.builtin.PagedComponent} - 分页组件</li>
 *   <li>{@link gloomlib.gui.component.builtin.ScrollComponent} - 滚动组件</li>
 *   <li>{@link gloomlib.gui.component.builtin.CycleComponent} - 循环切换组件</li>
 *   <li>{@link gloomlib.gui.component.builtin.TabComponent} - 标签页组件</li>
 *   <li>{@link gloomlib.gui.component.builtin.InventoryLinkComponent} - 背包链接组件</li>
 * </ul>
 * 
 * <h3>设计模式</h3>
 * <p>
 * 组件采用函数式设计，支持：
 * <ul>
 *   <li>响应式状态绑定</li>
 *   <li>自动 tick 更新</li>
 *   <li>事件处理</li>
 *   <li>克隆和复用</li>
 * </ul>
 * 
 * @author GloomLib
 * @since 2.0
 */
package gloomlib.gui.component;
