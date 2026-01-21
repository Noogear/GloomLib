/**
 * API 包 - 核心 GUI 接口
 * <p>
 * 提供 GloomLib GUI 的核心 API：
 * <ul>
 *   <li>{@link gloomlib.gui.api.GloomGui} - GUI 核心类（实现 {@link gloomlib.gui.observable.Observable}）</li>
 *   <li>{@link gloomlib.gui.api.GloomGuiBuilder} - GUI 构建器</li>
 * </ul>
 * 
 * <h3>3.0 版本新特性</h3>
 * <ul>
 *   <li>多观察者模式 - 支持多个玩家同时查看同一个 GUI</li>
 *   <li>Property 系统 - 类型安全的只读/读写状态分离</li>
 *   <li>SlotElement 系统 - 统一的槽位内容抽象（组件/GUI链接/背包链接）</li>
 *   <li>增强的交互处理 - 支持 MC 1.21+ 新特性</li>
 * </ul>
 * 
 * <h3>设计参考</h3>
 * <ul>
 *   <li>InvUI 2.x - 多观察者模式、Property 系统、SlotElement</li>
 *   <li>triumph-gui v4 - 构建器模式、平台抽象</li>
 * </ul>
 * 
 * @author GloomLib
 * @since 2.0
 */
package gloomlib.gui.api;
