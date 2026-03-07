package gloomlib.command.core.internal;

import io.papermc.paper.command.brigadier.Commands;

import java.util.Set;

/**
 * Manages command unregistration with ownership verification.
 *
 * <p>
 * Wraps {@link BrigadierUnregister} with tracker-based ownership checks.
 * Only commands tracked by the associated tracker can be unregistered.
 * </p>
 */
public final class CommandUnregistrar {

    private final Commands commands;
    private final CommandTracker tracker;

    /**
     * Creates a command unregistrar.
     *
     * @param commands Paper commands registrar
     * @param tracker  Command tracker for ownership verification
     */
    public CommandUnregistrar(Commands commands, CommandTracker tracker) {
        this.commands = commands;
        this.tracker = tracker;
    }

    /**
     * Unregisters a command by name with ownership verification.
     *
     * <p>
     * Only commands tracked by this manager's tracker will be unregistered.
     * All aliases of the command are automatically unregistered.
     * </p>
     *
     * @param commandName Command name to unregister
     * @return true if successfully unregistered, false otherwise
     */
    public boolean unregister(String commandName) {
        // Ownership verification
        if (!tracker.owns(commandName)) {
            return false;
        }

        // Get all names to unregister (main + aliases)
        Set<String> allNames = getAllNames(commandName);
        if (allNames == null || allNames.isEmpty()) {
            return false;
        }

        // Unregister all names
        int count = BrigadierUnregister.unregisterCommands(commands,
                allNames.toArray(new String[0]));

        // Untrack after successful unregistration
        if (count > 0) {
            tracker.untrack(commandName);
            return true;
        }

        return false;
    }

    /**
     * Gets all names (main + aliases) for a command.
     *
     * @param commandName Command name
     * @return Set of all names, or null if not found
     */
    private Set<String> getAllNames(String commandName) {
        return tracker.getAliases(commandName);
    }
}
