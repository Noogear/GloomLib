package gloomlib.command.example;

import gloomlib.command.GloomCommand;
import gloomlib.command.annotation.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

import java.util.Set;

/**
 * Example command manager for testing command unregister functionality.
 *
 * <p>
 * This demonstrates the Cloud-style command unregistration feature
 * implemented in GloomLib based on cloud-minecraft framework.
 * </p>
 *
 * <h2>Commands:</h2>
 * <ul>
 * <li>/cmdtest unload &lt;command&gt; - Unregister a command</li>
 * <li>/cmdtest list - List all registered commands</li>
 * <li>/cmdtest check &lt;command&gt; - Check if command is registered</li>
 * </ul>
 *
 * @see gloomlib.command.util.CommandUnregister
 */
@Command("cmdtest")
@Permission("gloomlib.cmdtest")
@Description("Test command unregister functionality")
public class CommandUnregisterTest {

    @Inject
    private GloomCommand gloomCommand;

    /**
     * Unload a command at runtime.
     *
     * <p>Usage: /cmdtest unload &lt;command&gt;</p>
     * <p>Example: /cmdtest unload fly</p>
     *
     * <p><b>Expected Result:</b></p>
     * <ul>
     * <li>Command disappears from tab completion</li>
     * <li>Command removed from /help list</li>
     * <li>Executing command returns "Unknown command"</li>
     * <li>All online players' command lists refreshed</li>
     * </ul>
     */
    @SubCommand("unload")
    public void unloadCommand(CommandSender sender, @Arg String commandName) {
        // Validate command exists
        if (!gloomCommand.isCommandRegistered(commandName)) {
            sender.sendMessage(Component.text(
                    "❌ Command '" + commandName + "' is not registered in GloomLib!",
                    NamedTextColor.RED
            ));
            return;
        }

        // Confirm before unloading
        sender.sendMessage(Component.text(
                "🔄 Attempting to unload command: " + commandName,
                NamedTextColor.YELLOW
        ));

        // Perform unload
        boolean success = gloomCommand.unregisterCommand(commandName);

        if (success) {
            sender.sendMessage(Component.text(
                    "✅ Successfully unloaded command: " + commandName,
                    NamedTextColor.GREEN
            ));
            sender.sendMessage(Component.text(
                    "   Try typing /" + commandName + " - should show 'Unknown command'",
                    NamedTextColor.GRAY
            ));
            sender.sendMessage(Component.text(
                    "   Tab completion should no longer suggest this command",
                    NamedTextColor.GRAY
            ));
        } else {
            sender.sendMessage(Component.text(
                    "❌ Failed to unload command: " + commandName,
                    NamedTextColor.RED
            ));
            sender.sendMessage(Component.text(
                    "   Check console for error details",
                    NamedTextColor.GRAY
            ));
        }
    }

    /**
     * List all registered commands.
     *
     * <p>Usage: /cmdtest list</p>
     *
     * <p>Displays all commands currently registered with GloomLib framework.</p>
     */
    @SubCommand("list")
    public void listCommands(CommandSender sender) {
        Set<String> commands = gloomCommand.getRegisteredCommandNames();

        sender.sendMessage(Component.text(
                "═══════════════════════════════",
                NamedTextColor.GOLD
        ));
        sender.sendMessage(Component.text(
                "  GloomLib Registered Commands",
                NamedTextColor.YELLOW
        ));
        sender.sendMessage(Component.text(
                "═══════════════════════════════",
                NamedTextColor.GOLD
        ));

        if (commands.isEmpty()) {
            sender.sendMessage(Component.text(
                    "  No commands registered",
                    NamedTextColor.GRAY
            ));
        } else {
            sender.sendMessage(Component.text(
                    "  Total: " + commands.size() + " command(s)",
                    NamedTextColor.AQUA
            ));
            sender.sendMessage(Component.text(""));

            int index = 1;
            for (String cmd : commands) {
                sender.sendMessage(Component.text(
                        "  " + index + ". /" + cmd,
                        NamedTextColor.WHITE
                ));
                index++;
            }
        }

        sender.sendMessage(Component.text(
                "═══════════════════════════════",
                NamedTextColor.GOLD
        ));
    }

    /**
     * Check if a command is registered.
     *
     * <p>Usage: /cmdtest check &lt;command&gt;</p>
     * <p>Example: /cmdtest check fly</p>
     */
    @SubCommand("check")
    public void checkCommand(CommandSender sender, @Arg String commandName) {
        boolean registered = gloomCommand.isCommandRegistered(commandName);

        if (registered) {
            sender.sendMessage(Component.text(
                    "✅ Command '" + commandName + "' IS registered",
                    NamedTextColor.GREEN
            ));
            sender.sendMessage(Component.text(
                    "   You can unload it with: /cmdtest unload " + commandName,
                    NamedTextColor.GRAY
            ));
        } else {
            sender.sendMessage(Component.text(
                    "❌ Command '" + commandName + "' is NOT registered",
                    NamedTextColor.RED
            ));
            sender.sendMessage(Component.text(
                    "   It might be from another plugin or already unloaded",
                    NamedTextColor.GRAY
            ));
        }
    }

    /**
     * Display help information.
     *
     * <p>Usage: /cmdtest</p>
     */
    @Usage
    public void showHelp(CommandSender sender) {
        sender.sendMessage(Component.text(
                "═══════════════════════════════════════",
                NamedTextColor.GOLD
        ));
        sender.sendMessage(Component.text(
                "  Command Unregister Test Utility",
                NamedTextColor.YELLOW
        ));
        sender.sendMessage(Component.text(
                "═══════════════════════════════════════",
                NamedTextColor.GOLD
        ));
        sender.sendMessage(Component.text(""));

        sender.sendMessage(Component.text(
                "  /cmdtest list",
                NamedTextColor.AQUA
        ));
        sender.sendMessage(Component.text(
                "    └─ List all registered commands",
                NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(""));

        sender.sendMessage(Component.text(
                "  /cmdtest check <command>",
                NamedTextColor.AQUA
        ));
        sender.sendMessage(Component.text(
                "    └─ Check if command is registered",
                NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(""));

        sender.sendMessage(Component.text(
                "  /cmdtest unload <command>",
                NamedTextColor.AQUA
        ));
        sender.sendMessage(Component.text(
                "    └─ Unload a command at runtime",
                NamedTextColor.GRAY
        ));
        sender.sendMessage(Component.text(""));

        sender.sendMessage(Component.text(
                "⚠️  Note: Only GloomLib-registered commands can be unloaded",
                NamedTextColor.YELLOW
        ));
        sender.sendMessage(Component.text(
                "═══════════════════════════════════════",
                NamedTextColor.GOLD
        ));
    }

    /**
     * Advanced: Unload multiple commands at once.
     *
     * <p>Usage: /cmdtest unload-all &lt;cmd1&gt; [cmd2] [cmd3] ...</p>
     * <p>Example: /cmdtest unload-all fly tp warp</p>
     */
    @SubCommand("unload-all")
    public void unloadMultiple(CommandSender sender, @Arg String... commandNames) {
        if (commandNames == null || commandNames.length == 0) {
            sender.sendMessage(Component.text(
                    "Usage: /cmdtest unload-all <cmd1> [cmd2] ...",
                    NamedTextColor.RED
            ));
            return;
        }

        sender.sendMessage(Component.text(
                "🔄 Unloading " + commandNames.length + " command(s)...",
                NamedTextColor.YELLOW
        ));

        int successCount = 0;
        int failCount = 0;

        for (String cmd : commandNames) {
            if (gloomCommand.unregisterCommand(cmd)) {
                sender.sendMessage(Component.text(
                        "  ✅ " + cmd,
                        NamedTextColor.GREEN
                ));
                successCount++;
            } else {
                sender.sendMessage(Component.text(
                        "  ❌ " + cmd + " (not found or failed)",
                        NamedTextColor.RED
                ));
                failCount++;
            }
        }

        sender.sendMessage(Component.text(""));
        sender.sendMessage(Component.text(
                "Result: " + successCount + " succeeded, " + failCount + " failed",
                successCount > 0 ? NamedTextColor.GREEN : NamedTextColor.RED
        ));
    }
}
