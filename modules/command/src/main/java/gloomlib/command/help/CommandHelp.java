package gloomlib.command.help;

import gloomlib.command.annotation.*;
import gloomlib.command.message.CommandMessages;
import gloomlib.command.util.MessageUtils;
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
 * Command Help Generator with modern Adventure interactive components.
 *
 * <p>
 * Automatically generates beautiful command help with:
 * </p>
 * <ul>
 * <li>Click events to suggest commands</li>
 * <li>Hover events to show detailed descriptions</li>
 * <li>MiniMessage formatting for colors and gradients</li>
 * <li>Interactive pagination controls</li>
 * </ul>
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
     * Displays help page with interactive components.
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

        // Header with separator
        String separator = "=".repeat(20);
        Component header = CommandMessages.HELP_HEADER.get(
                separator,
                commandName.toUpperCase(),
                separator);
        sender.sendMessage(header);

        // Entries with rich interactions
        int start = (page - 1) * itemsPerPage;
        int end = Math.min(start + itemsPerPage, visibleEntries.size());

        for (int i = start; i < end; i++) {
            HelpEntry entry = visibleEntries.get(i);

            // Build detailed hover text
            String description = entry.description.isEmpty() ? CommandMessages.HELP_NO_DESC.fallback()
                    : entry.description;
            Component hoverText = Component.text()
                    .append(CommandMessages.HELP_HOVER_COMMAND.get(entry.usage))
                    .append(Component.newline())
                    .append(CommandMessages.HELP_HOVER_DESCRIPTION.get(description))
                    .append(Component.newline())
                    .append(Component.newline())
                    .append(CommandMessages.HELP_HOVER_CLICK.get())
                    .build();

            // Usage component with click and hover
            Component usageComponent = Component.text()
                    .append(CommandMessages.HELP_USAGE_PREFIX.get())
                    .append(Component.text(entry.usage, NamedTextColor.AQUA, TextDecoration.BOLD))
                    .clickEvent(ClickEvent.suggestCommand(entry.usage))
                    .hoverEvent(HoverEvent.showText(hoverText))
                    .build();

            // Description with subtle color
            Component descComponent = entry.description.isEmpty()
                    ? Component.empty()
                    : Component.text()
                            .append(Component.newline())
                            .append(CommandMessages.HELP_DESC_PREFIX.get(entry.description))
                            .build();

            sender.sendMessage(usageComponent.append(descComponent));
        }

        // Footer with pagination
        if (totalPages > 1) {
            sender.sendMessage(Component.empty()); // Spacing

            String footerSeparator = "=".repeat(15);
            Component footer = CommandMessages.HELP_SEPARATOR.get(
                    footerSeparator + " " + CommandMessages.HELP_PAGE_INFO.get(page, totalPages) + " "
                            + footerSeparator);
            sender.sendMessage(footer);

            // Interactive pagination buttons
            Component prevButton = page > 1
                    ? CommandMessages.HELP_PREV_BUTTON.get()
                            .clickEvent(ClickEvent.runCommand(String.format("/%s help %d", commandName, page - 1)))
                            .hoverEvent(HoverEvent.showText(
                                    MessageUtils.MINI_MESSAGE.deserialize(
                                            CommandMessages.HELP_GOTO_PAGE.get(page - 1).toString())))
                    : CommandMessages.HELP_PREV_DISABLED.get();

            Component nextButton = page < totalPages
                    ? CommandMessages.HELP_NEXT_BUTTON.get()
                            .clickEvent(ClickEvent.runCommand(String.format("/%s help %d", commandName, page + 1)))
                            .hoverEvent(HoverEvent.showText(
                                    MessageUtils.MINI_MESSAGE.deserialize(
                                            CommandMessages.HELP_GOTO_PAGE.get(page + 1).toString())))
                    : CommandMessages.HELP_NEXT_DISABLED.get();

            Component pageHint = Component.text()
                    .append(Component.text("  "))
                    .append(prevButton)
                    .append(CommandMessages.HELP_PAGE_SEPARATOR.get())
                    .append(nextButton)
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
