package gloomlib.command.help;

import gloomlib.command.annotation.*;
import gloomlib.command.message.CommandMessages;
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
 * Command Help Generator.
 *
 * <p>
 * Automatically generates beautiful command help information (based on
 * Adventure API).
 * </p>
 */
public class CommandHelp {

    private final String commandName;
    private final List<HelpEntry> entries = new ArrayList<>();
    private int itemsPerPage = 8;

    /**
     * Creates command help.
     *
     * @param commandName Command name
     */
    public CommandHelp(String commandName) {
        this.commandName = commandName;
    }

    /**
     * Scans command class for help entries.
     *
     * @param commandClass Command class
     * @return this (fluent API)
     */
    public CommandHelp scanClass(Class<?> commandClass) {
        // Scan @Usage methods
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
     * Adds a help entry.
     */
    private void addEntry(String usage, Method method) {
        Description desc = method.getAnnotation(Description.class);
        Permission perm = method.getAnnotation(Permission.class);

        StringBuilder usageBuilder = new StringBuilder("/").append(usage);

        // Build parameter usage
        for (Parameter param : method.getParameters()) {
            // Skip sender parameter
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
     * Manually adds a help entry.
     *
     * @param usage       Usage
     * @param description Description
     * @return this (fluent API)
     */
    public CommandHelp addEntry(String usage, String description) {
        entries.add(new HelpEntry(usage, description, null));
        return this;
    }

    /**
     * Manually adds a help entry (with permission).
     *
     * @param usage       Usage
     * @param description Description
     * @param permission  Required permission
     * @return this (fluent API)
     */
    public CommandHelp addEntry(String usage, String description, String permission) {
        entries.add(new HelpEntry(usage, description, permission));
        return this;
    }

    /**
     * Sets items per page.
     *
     * @param items Items count
     * @return this (fluent API)
     */
    public CommandHelp setItemsPerPage(int items) {
        this.itemsPerPage = items;
        return this;
    }

    /**
     * Displays help page.
     *
     * @param sender Receiver
     * @param page   Page number (1-based)
     */
    public void display(CommandSender sender, int page) {
        // Filter entries visible to sender
        List<HelpEntry> visibleEntries = entries.stream()
                .filter(entry -> entry.permission == null || sender.hasPermission(entry.permission))
                .toList();

        if (visibleEntries.isEmpty()) {
            sender.sendMessage(CommandMessages.HELP_NO_COMMANDS.get());
            return;
        }

        int totalPages = (int) Math.ceil((double) visibleEntries.size() / itemsPerPage);
        page = Math.max(1, Math.min(page, totalPages));

        // Header
        Component header = Component.text()
                .append(Component.text("═".repeat(20), NamedTextColor.GOLD))
                .append(Component.text(" " + commandName.toUpperCase() + " HELP ", NamedTextColor.YELLOW,
                        TextDecoration.BOLD))
                .append(Component.text("═".repeat(20), NamedTextColor.GOLD))
                .build();
        sender.sendMessage(header);

        // Entries
        int start = (page - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, visibleEntries.size());

        for (int i = start; i < end; i++) {
            HelpEntry entry = visibleEntries.get(i);

            Component usageComponent = Component.text(entry.usage, NamedTextColor.AQUA)
                    .clickEvent(ClickEvent.suggestCommand(entry.usage))
                    .hoverEvent(HoverEvent.showText(CommandMessages.HELP_CLICK_HINT.get()));

            Component descComponent = Component.text(" - " + entry.description, NamedTextColor.GRAY);

            sender.sendMessage(usageComponent.append(descComponent));
        }

        // Footer
        if (totalPages > 1) {
            Component footer = Component.text()
                    .append(Component.text("═".repeat(15), NamedTextColor.GOLD))
                    .append(Component.text(" ")
                            .append(CommandMessages.HELP_PAGE.get(Component.text(page), Component.text(totalPages)))
                            .append(Component.text(" ")))
                    .append(Component.text("═".repeat(15), NamedTextColor.GOLD))
                    .build();
            sender.sendMessage(footer);

            // Pagination hint
            Component pageHint = Component.text()
                    .append(page > 1
                            ? CommandMessages.HELP_PREV.get().color(NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand(String.format("/%s help %d", commandName, page - 1)))
                            : CommandMessages.HELP_PREV.get().color(NamedTextColor.DARK_GRAY))
                    .append(Component.text(" | ", NamedTextColor.GRAY))
                    .append(page < totalPages
                            ? CommandMessages.HELP_NEXT.get().color(NamedTextColor.GREEN)
                            .clickEvent(ClickEvent.runCommand(String.format("/%s help %d", commandName, page + 1)))
                            : CommandMessages.HELP_NEXT.get().color(NamedTextColor.DARK_GRAY))
                    .build();
            sender.sendMessage(pageHint);
        }
    }

    /**
     * Displays help (first page).
     *
     * @param sender Receiver
     */
    public void display(CommandSender sender) {
        display(sender, 1);
    }

    /**
     * Help entry.
     */
    private record HelpEntry(String usage, String description, String permission) {
    }
}
