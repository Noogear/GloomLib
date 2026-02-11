package gloomlib.command.internal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.RootCommandNode;
import gloomlib.command.util.Reflection;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Brigadier command unregistration via hidden removeCommand() API.
 *
 * <br>
 * <b>Implementation Note:</b> Uses cached reflection for thread-safe atomic
 * operations.
 */
public final class BrigadierUnregister {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(BrigadierUnregister.class);

    private BrigadierUnregister() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean unregisterCommand(Commands commands, String commandName) {
        if (commands == null || commandName == null || commandName.isEmpty()) {
            return false;
        }

        try {
            unsafeOperation(commands, () -> {
                CommandDispatcher<CommandSourceStack> dispatcher = commands.getDispatcher();
                RootCommandNode<CommandSourceStack> root = dispatcher.getRoot();

                Reflection.invokeMethod(root, "removeCommand",
                        new Class<?>[] { String.class },
                        commandName.toLowerCase());
            });

            resendCommands();
            return true;

        } catch (Exception e) {
            LOGGER.debug("Failed to unregister command", e);
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
