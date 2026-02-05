package gloomlib.command.example;

import gloomlib.command.annotation.*;
import gloomlib.command.help.CommandHelp;
import gloomlib.command.util.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Example command demonstrating the enhanced interactive help system.
 *
 * <p>
 * Features:
 * <ul>
 * <li>Click to suggest commands</li>
 * <li>Hover to show detailed descriptions</li>
 * <li>Interactive pagination buttons</li>
 * <li>MiniMessage formatting in help text</li>
 * </ul>
 * </p>
 */
@Command("helpdemo")
@Description("Demo of interactive help system")
@Permission("gloomlib.demo.help")
public class InteractiveHelpCommand {

    private final CommandHelp help = new CommandHelp("helpdemo");

    public InteractiveHelpCommand() {
        // Pre-register entries for demo
        help.addEntry("/helpdemo test", "Test command with no arguments")
                .addEntry("/helpdemo give <player> <amount>", "Give items to a player")
                .addEntry("/helpdemo teleport <x> <y> <z>", "Teleport to coordinates")
                .addEntry("/helpdemo weather <type>", "Change weather", "gloomlib.demo.weather")
                .addEntry("/helpdemo time <ticks>", "Set world time")
                .addEntry("/helpdemo gamemode <mode>", "Change gamemode", "gloomlib.demo.gamemode")
                .addEntry("/helpdemo effect <effect> [duration]", "Apply potion effect")
                .addEntry("/helpdemo spawn <entity> [count]", "Spawn entities")
                .addEntry("/helpdemo heal", "Restore health")
                .addEntry("/helpdemo feed", "Restore hunger")
                .addEntry("/helpdemo fly [player]", "Toggle flight", "gloomlib.demo.fly")
                .addEntry("/helpdemo speed <speed>", "Set movement speed");
    }

    @Usage
    @Description("Show help page 1")
    public void showHelp(CommandSender sender) {
        help.display(sender, 1);
    }

    @SubCommand("help")
    @Description("Show specific help page")
    public void showHelpPage(CommandSender sender, @Arg("page") @Optional int page) {
        help.display(sender, page);
    }

    @SubCommand("test")
    @Description("Test command to demonstrate help entries")
    public void test(Player player) {
        player.sendMessage(MessageUtils.deserialize(
                "<green>Test command executed!</green> Try <yellow>/helpdemo help</yellow> to see the full list."
        ));
    }

    @SubCommand("features")
    @Description("Explain interactive help features")
    public void features(CommandSender sender) {
        sender.sendMessage(MessageUtils.deserialize("<gold><bold>Interactive Help Features:</bold></gold>"));
        sender.sendMessage(Component.empty());
        sender.sendMessage(MessageUtils.deserialize(" <dark_gray>▸</dark_gray> <aqua><bold>Click</bold></aqua> any command to fill it in chat"));
        sender.sendMessage(MessageUtils.deserialize(" <dark_gray>▸</dark_gray> <aqua><bold>Hover</bold></aqua> to see detailed descriptions"));
        sender.sendMessage(MessageUtils.deserialize(" <dark_gray>▸</dark_gray> <aqua><bold>Navigation</bold></aqua> buttons for multiple pages"));
        sender.sendMessage(MessageUtils.deserialize(" <dark_gray>▸</dark_gray> <aqua><bold>Permission</bold></aqua> filtering (only shows allowed commands)"));
        sender.sendMessage(Component.empty());
        sender.sendMessage(MessageUtils.deserialize("<gray>Try <yellow>/helpdemo help</yellow> to see it in action!</gray>"));
    }
}
