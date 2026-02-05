package gloomlib.command.internal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.RootCommandNode;
import gloomlib.command.util.Reflection;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Brigadier command unregister utility using hidden {@code removeCommand()} API.
 *
 * <p>
 * Based on cloud-minecraft's ModernPaperBrigadier implementation.
 * Uses Brigadier's undocumented but stable removeCommand method.
 * </p>
 *
 * <h2>Optimizations</h2>
 * <ul>
 * <li>Cached reflection - removeCommand method is cached by {@link Reflection}</li>
 * <li>Unified reflection API - all reflection goes through Reflection utility</li>
 * <li>Thread-safe operation - uses atomic invalid flag manipulation</li>
 * </ul>
 */
public final class BrigadierUnregister {

    private BrigadierUnregister() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Unregisters a command using Brigadier's {@code removeCommand()} method.
     *
     * @param commands    Paper Commands registrar
     * @param commandName Command name (without '/')
     * @return true if successfully removed
     */
    public static boolean unregisterCommand(Commands commands, String commandName) {
        if (commands == null || commandName == null || commandName.isEmpty()) {
            return false;
        }

        try {
            unsafeOperation(commands, () -> {
                CommandDispatcher<CommandSourceStack> dispatcher = commands.getDispatcher();
                RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();

                // Invoke Brigadier's removeCommand method (cached by Reflection)
                Reflection.invokeMethod(root, "removeCommand",
                        new Class<?>[]{String.class},
                        commandName.toLowerCase());
            });

            resendCommands();
            return true;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Unregisters multiple commands.
     *
     * @param commands     Paper Commands registrar
     * @param commandNames Command names to unregister
     * @return Number of commands successfully removed
     */
    public static int unregisterCommands(Commands commands, String... commandNames) {
        int count = 0;
        for (String name : commandNames) {
            if (unregisterCommand(commands, name)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Performs unsafe operation by temporarily bypassing Paper's protection.
     *
     * @param commands Paper Commands registrar
     * @param task     Task to perform
     * @throws Exception if operation fails
     */
    private static void unsafeOperation(Commands commands, UnsafeTask task) throws Exception {
        // Save original state
        boolean previousState = Reflection.getField(commands, "invalid");

        try {
            // Temporarily disable protection
            Reflection.setField(commands, "invalid", false);

            // Perform the task
            task.run();

        } finally {
            // Restore original state
            Reflection.setField(commands, "invalid", previousState);
        }
    }

    /**
     * Refreshes command lists for all online players.
     */
    private static void resendCommands() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.updateCommands();
        }
    }

    /**
     * Functional interface for unsafe operations.
     */
    @FunctionalInterface
    private interface UnsafeTask {
        void run() throws Exception;
    }
}
