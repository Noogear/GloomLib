package gloomlib.command.help;

import gloomlib.command.annotation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令帮助生成器。
 *
 * <p>
 * 自动生成精美的命令帮助信息（基于 Adventure API）。
 * </p>
 */
public class CommandHelp {

    private final String commandName;
    private final List<HelpEntry> entries = new ArrayList<>();
    private int itemsPerPage = 8;

    /**
     * 创建命令帮助。
     *
     * @param commandName 命令名
     */
    public CommandHelp(String commandName) {
        this.commandName = commandName;
    }

    /**
     * 从命令类扫描帮助条目。
     *
     * @param commandClass 命令类
     * @return this（链式调用）
     */
    public CommandHelp scanClass(Class<?> commandClass) {
        // 扫描 @Usage 方法
        for (Method method : commandClass.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Usage.class)) {
                addEntry(commandName, method);
            } else if (method.isAnnotationPresent(SubCommand.class)) {
                SubCommand sub = method.getAnnotation(SubCommand.class);
                addEntry(commandName + " " + sub.value(), method);
            }
        }
        return this;
    }

    /**
     * 添加帮助条目。
     */
    private void addEntry(String usage, Method method) {
        Description desc = method.getAnnotation(Description.class);
        Permission perm = method.getAnnotation(Permission.class);

        StringBuilder usageBuilder = new StringBuilder("/").append(usage);

        // 构建参数用法
        for (Parameter param : method.getParameters()) {
            // 跳过 sender 参数
            if (CommandSender.class.isAssignableFrom(param.getType()))
                continue;
            if (param.getType().getName().contains("CommandContext"))
                continue;

            Arg arg = param.getAnnotation(Arg.class);
            String argName = arg != null && !arg.value().isEmpty() ? arg.value() : param.getName();

            boolean optional = param.isAnnotationPresent(Optional.class);

            if (optional) {
                usageBuilder.append(" [").append(argName).append("]");
            } else {
                usageBuilder.append(" <").append(argName).append(">");
            }
        }

        entries.add(new HelpEntry(
                usageBuilder.toString(),
                desc != null ? desc.value() : "",
                perm != null ? perm.value() : null));
    }

    /**
     * 手动添加帮助条目。
     *
     * @param usage       用法
     * @param description 描述
     * @return this（链式调用）
     */
    public CommandHelp addEntry(String usage, String description) {
        entries.add(new HelpEntry(usage, description, null));
        return this;
    }

    /**
     * 手动添加帮助条目（带权限）。
     *
     * @param usage       用法
     * @param description 描述
     * @param permission  所需权限
     * @return this（链式调用）
     */
    public CommandHelp addEntry(String usage, String description, String permission) {
        entries.add(new HelpEntry(usage, description, permission));
        return this;
    }

    /**
     * 设置每页显示条目数。
     *
     * @param items 条目数
     * @return this（链式调用）
     */
    public CommandHelp setItemsPerPage(int items) {
        this.itemsPerPage = items;
        return this;
    }

    /**
     * 显示帮助页面。
     *
     * @param sender 接收者
     * @param page   页码（从 1 开始）
     */
    public void display(CommandSender sender, int page) {
        // 过滤有权限查看的条目
        List<HelpEntry> visibleEntries = entries.stream()
                .filter(entry -> entry.permission == null || sender.hasPermission(entry.permission))
                .toList();

        if (visibleEntries.isEmpty()) {
            sender.sendMessage(Component.text("没有可用的命令。", NamedTextColor.GRAY));
            return;
        }

        int totalPages = (int) Math.ceil((double) visibleEntries.size() / itemsPerPage);
        page = Math.max(1, Math.min(page, totalPages));

        // 标题
        Component header = Component.text()
                .append(Component.text("═".repeat(20), NamedTextColor.GOLD))
                .append(Component.text(" " + commandName.toUpperCase() + " 帮助 ", NamedTextColor.YELLOW,
                        TextDecoration.BOLD))
                .append(Component.text("═".repeat(20), NamedTextColor.GOLD))
                .build();
        sender.sendMessage(header);

        // 条目
        int start = (page - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, visibleEntries.size());

        for (int i = start; i < end; i++) {
            HelpEntry entry = visibleEntries.get(i);

            Component usageComponent = Component.text(entry.usage, NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.suggestCommand(entry.usage))
                    .hoverEvent(HoverEvent.showText(Component.text("点击填入命令", NamedTextColor.GRAY)));

            Component descComponent = Component.text(" - " + entry.description, NamedTextColor.GRAY);

            sender.sendMessage(usageComponent.append(descComponent));
        }

        // 页脚
        if (totalPages > 1) {
            Component footer = Component.text()
                    .append(Component.text("═".repeat(15), NamedTextColor.GOLD))
                    .append(Component.text(" 第 " + page + "/" + totalPages + " 页 ", NamedTextColor.YELLOW))
                    .append(Component.text("═".repeat(15), NamedTextColor.GOLD))
                    .build();
            sender.sendMessage(footer);

            // 翻页提示
            Component pageHint = Component.text()
                    .append(page > 1
                            ? Component.text("[上一页]", NamedTextColor.GREEN)
                                    .clickEvent(ClickEvent.runCommand("/" + commandName + " help " + (page - 1)))
                            : Component.text("[上一页]", NamedTextColor.DARK_GRAY))
                    .append(Component.text(" | ", NamedTextColor.GRAY))
                    .append(page < totalPages
                            ? Component.text("[下一页]", NamedTextColor.GREEN)
                                    .clickEvent(ClickEvent.runCommand("/" + commandName + " help " + (page + 1)))
                            : Component.text("[下一页]", NamedTextColor.DARK_GRAY))
                    .build();
            sender.sendMessage(pageHint);
        }
    }

    /**
     * 显示帮助（第一页）。
     *
     * @param sender 接收者
     */
    public void display(CommandSender sender) {
        display(sender, 1);
    }

    /**
     * 帮助条目。
     */
    private record HelpEntry(String usage, String description, String permission) {
    }
}
