/**
 * 槽位元素系统 - 统一的槽位内容抽象
 * <p>
 * 提供 {@link gloomlib.gui.slot.SlotElement} sealed 接口，
 * 统一处理组件、GUI 嵌套和背包链接三种槽位内容类型。
 * <p>
 * 使用 Java 17+ sealed 接口和 record 特性，确保类型安全和模式匹配支持。
 * 
 * <h3>设计参考</h3>
 * <ul>
 *   <li>InvUI 2.x SlotElement 系统</li>
 *   <li>文件：{@code invui/src/main/java/xyz/xenondevs/invui/gui/SlotElement.java}</li>
 * </ul>
 * 
 * @author GloomLib
 * @since 3.0
 */
package gloomlib.gui.slot;
