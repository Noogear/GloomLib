package gloomlib.examples;

import gloomlib.gui.api.GloomGuiBuilder;
import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.util.ItemBuilder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * 导航系统使用示例
 * <p>
 * 展示如何使用新的 InteractionContext 导航 API：
 * <ul>
 *   <li>{@code ctx.navigateBack()} - 返回上一个菜单</li>
 *   <li>{@code ctx.canNavigateBack()} - 检查是否可以返回</li>
 *   <li>{@code ctx.getNavigationDepth()} - 获取导航深度</li>
 *   <li>{@code ctx.clearNavigationHistory()} - 清空导航历史</li>
 * </ul>
 *
 * <p><b>关键变化：</b>
 * <ul>
 *   <li>❌ 旧方式：{@code .withBackButton('B')} - 已删除</li>
 *   <li>✅ 新方式：在任何组件的 {@code onClick} 中调用 {@code ctx.navigateBack()}</li>
 *   <li>✅ 灵活性：返回功能不再绑定到特定按钮，可在任何交互逻辑中使用</li>
 * </ul>
 */
public class NavigationExample {


    /**
     * 示例 1：简单返回按钮
     * <p>
     * 最基础的返回实现：点击按钮调用 ctx.navigateBack()
     */
    public void openMenuWithSimpleBackButton(Player player) {
        GloomGuiBuilder.chest()
                .title(Component.text("简单导航"))
                .rows(3)
                .structure(
                        "GGGGGGGGG",
                        "G.......G",
                        "GGGBGGGGG"
                )
                .define('G', createBorder())
                .define('B', createSimpleBackButton())
                .define('.', createEmptyComponent())
                .navigationEnabled(true)  // 启用导航跟踪
                .open(player);
    }

    /**
     * 示例 1.5：智能返回/关闭按钮（推荐）
     * <p>
     * 使用 navigateBackOrClose()：有上一级则返回，无上一级则关闭
     * <p>
     * <b>优势</b>：按钮始终有效，无需判断是否有导航历史
     */
    public void openMenuWithSmartBackButton(Player player) {
        GloomGuiBuilder.chest()
                .title(Component.text("智能导航"))
                .rows(3)
                .structure(
                        "GGGGGGGGG",
                        "G.......G",
                        "GGGBGGGGG"
                )
                .define('G', createBorder())
                .define('B', createSmartBackButton())  // ✨ 智能返回/关闭
                .define('.', createEmptyComponent())
                .withAutoBack()  // 自动启用导航跟踪
                .open(player);
    }

    /**
     * 示例 2：带反馈的返回按钮
     * <p>
     * 返回时发送消息提示玩家。
     */
    public void openMenuWithFeedbackBackButton(Player player) {
        GloomGuiBuilder.chest()
                .title(Component.text("带反馈的导航"))
                .rows(3)
                .structure(
                        "GGGGGGGGG",
                        "G.......G",
                        "GGGBGGGGG"
                )
                .define('G', createBorder())
                .define('B', GloomComponent.builder()
                        .icon(ItemBuilder.from(Material.ARROW)
                                .name(Component.text("返回").color(NamedTextColor.YELLOW))
                                .lore(Component.text("点击返回上一页").color(NamedTextColor.GRAY))
                                .build())
                        .onClick(ctx -> {
                            if (ctx.navigateBack()) {
                                ctx.player().sendMessage(
                                        Component.text("✓ 已返回上一页").color(NamedTextColor.GREEN)
                                );
                            } else {
                                ctx.player().sendMessage(
                                        Component.text("✗ 无上一页可返回").color(NamedTextColor.RED)
                                );
                            }
                        })
                        .build())
                .define('.', createEmptyComponent())
                .navigationEnabled(true)
                .open(player);
    }

    /**
     * 示例 3：条件返回按钮
     * <p>
     * 只有在有导航历史时才允许返回。
     */
    public void openMenuWithConditionalBackButton(Player player) {
        GloomGuiBuilder.chest()
                .title(Component.text("条件导航"))
                .rows(3)
                .structure(
                        "GGGGGGGGG",
                        "G.......G",
                        "GGGBGGGGG"
                )
                .define('G', createBorder())
                .define('B', GloomComponent.builder()
                        .icon(ItemBuilder.from(Material.ARROW)
                                .name(Component.text("返回").color(NamedTextColor.YELLOW))
                                .build())
                        .onClick(ctx -> {
                            // 先检查是否可以返回
                            if (!ctx.canNavigateBack()) {
                                ctx.player().sendMessage(
                                        Component.text("这是第一页，无法返回").color(NamedTextColor.RED)
                                );
                                return;
                            }
                            ctx.navigateBack();
                        })
                        .build())
                .define('.', createEmptyComponent())
                .navigationEnabled(true)
                .open(player);
    }


    /**
     * 示例 4：自动返回（ESC 键）
     * <p>
     * 使用 withAutoBack() 配置 ESC 键自动返回。
     */
    public void openMenuWithAutoBack(Player player) {
        GloomGuiBuilder.chest()
                .title(Component.text("自动返回"))
                .rows(3)
                .structure(
                        "GGGGGGGGG",
                        "G.......G",
                        "GGGGGGGGG"
                )
                .define('G', createBorder())
                .define('.', createEmptyComponent())
                .navigationEnabled(true)
                .withAutoBack()  // ESC 键自动返回
                .open(player);
    }

    /**
     * 示例 5：多功能按钮（返回 + 其他操作）
     * <p>
     * 演示如何在一个按钮中组合返回和其他逻辑。
     */
    public void openMenuWithMultiActionButton(Player player) {
        GloomGuiBuilder.chest()
                .title(Component.text("多功能导航"))
                .rows(3)
                .structure(
                        "GGGGGGGGG",
                        "G.......G",
                        "GGGBGGGGG"
                )
                .define('G', createBorder())
                .define('B', GloomComponent.builder()
                        .icon(ItemBuilder.from(Material.ARROW)
                                .name(Component.text("保存并返回").color(NamedTextColor.YELLOW))
                                .lore(
                                        Component.text("点击保存当前数据").color(NamedTextColor.GRAY),
                                        Component.text("并返回上一页").color(NamedTextColor.GRAY)
                                )
                                .build())
                        .onClick(ctx -> {
                            // 执行保存逻辑
                            savePlayerData(ctx.player());

                            // 然后返回
                            if (ctx.navigateBack()) {
                                ctx.player().sendMessage(
                                        Component.text("✓ 数据已保存并返回").color(NamedTextColor.GREEN)
                                );
                            }
                        })
                        .build())
                .define('.', createEmptyComponent())
                .navigationEnabled(true)
                .open(player);
    }

    /**
     * 示例 6：确认对话框模式
     * <p>
     * 使用导航功能实现"确认/取消"对话框。
     */
    public void openConfirmationDialog(Player player, Runnable onConfirm) {
        GloomGuiBuilder.chest()
                .title(Component.text("确认操作"))
                .rows(3)
                .structure(
                        "GGGGGGGGG",
                        "G..Y.N..G",
                        "GGGGGGGGG"
                )
                .define('G', createBorder())
                .define('Y', GloomComponent.builder()
                        .icon(ItemBuilder.from(Material.GREEN_CONCRETE)
                                .name(Component.text("确认").color(NamedTextColor.GREEN))
                                .build())
                        .onClick(ctx -> {
                            onConfirm.run();  // 执行确认操作
                            ctx.navigateBack();  // 返回上一页
                        })
                        .build())
                .define('N', GloomComponent.builder()
                        .icon(ItemBuilder.from(Material.RED_CONCRETE)
                                .name(Component.text("取消").color(NamedTextColor.RED))
                                .build())
                        .onClick(ctx -> {
                            ctx.navigateBack();  // 直接返回
                            ctx.player().sendMessage(
                                    Component.text("操作已取消").color(NamedTextColor.GRAY)
                            );
                        })
                        .build())
                .define('.', createEmptyComponent())
                .navigationEnabled(true)
                .open(player);
    }

    /**
     * 示例 7：显示导航深度
     * <p>
     * 显示当前菜单层级信息。
     */
    public void openMenuWithDepthInfo(Player player) {
        GloomGuiBuilder.chest()
                .title(Component.text("导航深度信息"))
                .rows(3)
                .structure(
                        "GGGGGGGGG",
                        "G.......G",
                        "GGGIBGGGG"
                )
                .define('G', createBorder())
                .define('B', createSimpleBackButton())
                .define('I', GloomComponent.builder()
                        .icon(ItemBuilder.from(Material.BOOK)
                                .name(Component.text("导航信息").color(NamedTextColor.AQUA))
                                .build())
                        .onClick(ctx -> {
                            int depth = ctx.getNavigationDepth();
                            ctx.player().sendMessage(
                                    Component.text("当前菜单层级: " + depth).color(NamedTextColor.YELLOW)
                            );

                            if (ctx.canNavigateBack()) {
                                ctx.player().sendMessage(
                                        Component.text("可以返回上一页").color(NamedTextColor.GREEN)
                                );
                            } else {
                                ctx.player().sendMessage(
                                        Component.text("这是第一页").color(NamedTextColor.RED)
                                );
                            }
                        })
                        .build())
                .define('.', createEmptyComponent())
                .navigationEnabled(true)
                .open(player);
    }

    /**
     * 示例 8：主菜单（无返回）
     * <p>
     * 主菜单通常不需要返回按钮，只有关闭按钮。
     */
    public void openMainMenu(Player player) {
        GloomGuiBuilder.chest()
                .title(Component.text("§6§l主菜单"))
                .rows(3)
                .structure(
                        "GGGGGGGGG",
                        "G123456GG",
                        "GGGXGGGGG"
                )
                .define('G', createBorder())
                .define('1', createMenuItem("商店", Material.EMERALD, ctx -> openShopMenu(ctx.player())))
                .define('2', createMenuItem("背包", Material.CHEST, ctx -> openInventoryMenu(ctx.player())))
                .define('3', createMenuItem("任务", Material.WRITABLE_BOOK, ctx -> openQuestsMenu(ctx.player())))
                .define('4', createMenuItem("技能", Material.ENCHANTED_BOOK, ctx -> openSkillsMenu(ctx.player())))
                .define('5', createMenuItem("设置", Material.COMPARATOR, ctx -> openSettingsMenu(ctx.player())))
                .define('6', createMenuItem("帮助", Material.BOOK, ctx -> openHelpMenu(ctx.player())))
                .define('X', GloomComponent.builder()
                        .icon(ItemBuilder.from(Material.BARRIER)
                                .name(Component.text("关闭").color(NamedTextColor.RED))
                                .build())
                        .onClick(ctx -> ctx.player().closeInventory())
                        .build())
                // 注意：主菜单通常不启用 navigationEnabled，因为它是顶层菜单
                .open(player);
    }


    /**
     * 创建边框组件
     */
    private GloomComponent createBorder() {
        return GloomComponent.builder()
                .icon(ItemBuilder.from(Material.GRAY_STAINED_GLASS_PANE)
                        .name(Component.empty())
                        .build())
                .build();
    }

    /**
     * 创建空组件
     */
    private GloomComponent createEmptyComponent() {
        return GloomComponent.builder()
                .icon(ItemBuilder.from(Material.AIR).build())
                .build();
    }

    /**
     * 创建简单返回按钮
     */
    private GloomComponent createSimpleBackButton() {
        return GloomComponent.builder()
                .icon(ItemBuilder.from(Material.ARROW)
                        .name(Component.text("返回").color(NamedTextColor.YELLOW))
                        .lore(Component.text("点击返回上一页").color(NamedTextColor.GRAY))
                        .build())
                .onClick(ctx -> ctx.navigateBack())
                .build();
    }

    /**
     * 创建智能返回/关闭按钮
     * <p>
     * 推荐使用此方法：有上一级则返回，无上一级则关闭
     */
    private GloomComponent createSmartBackButton() {
        return GloomComponent.builder()
                .icon(ItemBuilder.from(Material.ARROW)
                        .name(Component.text("返回").color(NamedTextColor.YELLOW))
                        .lore(Component.text("点击返回/关闭").color(NamedTextColor.GRAY))
                        .build())
                .onClick(ctx -> ctx.navigateBackOrClose())  // ✨ 智能返回或关闭
                .build();
    }

    /**
     * 创建菜单项按钮
     */
    private GloomComponent createMenuItem(String name, Material icon, java.util.function.Consumer<gloomlib.gui.interaction.InteractionContext> action) {
        return GloomComponent.builder()
                .icon(ItemBuilder.from(icon)
                        .name(Component.text(name).color(NamedTextColor.YELLOW))
                        .build())
                .onClick(action)
                .build();
    }


    private void openShopMenu(Player player) {
        GloomGuiBuilder.chest()
                .title(Component.text("商店"))
                .rows(3)
                .structure(
                        "GGGGGGGGG",
                        "G.......G",
                        "GGGBGGGGG"
                )
                .define('G', createBorder())
                .define('B', createSimpleBackButton())
                .define('.', createEmptyComponent())
                .navigationEnabled(true)
                .withAutoBack()
                .open(player);
    }

    private void openInventoryMenu(Player player) {
        openShopMenu(player);  // 简化演示
    }

    private void openQuestsMenu(Player player) {
        openShopMenu(player);  // 简化演示
    }

    private void openSkillsMenu(Player player) {
        openShopMenu(player);  // 简化演示
    }

    private void openSettingsMenu(Player player) {
        openShopMenu(player);  // 简化演示
    }

    private void openHelpMenu(Player player) {
        openShopMenu(player);  // 简化演示
    }

    /**
     * 模拟数据保存
     */
    private void savePlayerData(Player player) {
        // 实际项目中在这里保存数据
    }
}
