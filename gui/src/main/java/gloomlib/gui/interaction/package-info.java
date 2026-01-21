/**
 * 交互处理包 - 玩家与 GUI 的交互上下文
 * <p>
 * 提供完整的交互上下文信息和处理工具：
 * <ul>
 *   <li>{@link gloomlib.gui.interaction.InteractionContext} - 交互上下文记录</li>
 *   <li>{@link gloomlib.gui.interaction.DragContext} - 拖拽交互上下文</li>
 * </ul>
 * 
 * <h3>设计参考</h3>
 * <ul>
 *   <li>InvUI 2.x 完整点击处理逻辑</li>
 *   <li>文件：{@code invui/src/main/java/xyz/xenondevs/invui/gui/AbstractGui.java#L100-L550}</li>
 * </ul>
 * 
 * <h3>支持的交互类型</h3>
 * <ul>
 *   <li>基本点击（左键、右键、中键）</li>
 *   <li>修饰键点击（Shift、Ctrl）</li>
 *   <li>数字键快捷栏交换</li>
 *   <li>副手交换（F 键）</li>
 *   <li>双击收集</li>
 *   <li>拖拽分配</li>
 *   <li>MC 1.21+ Bundle 交互</li>
 *   <li>创造模式特殊操作</li>
 * </ul>
 * 
 * @author GloomLib
 * @since 2.0
 */
package gloomlib.gui.interaction;
