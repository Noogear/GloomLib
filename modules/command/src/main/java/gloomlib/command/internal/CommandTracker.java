package gloomlib.command.internal;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks registered commands for ownership verification (Cloud-style).
 *
 * <p>
 * Thread-safe implementation using concurrent collections.
 * Each GloomCommand instance has its own tracker for isolation.
 * </p>
 */
public final class CommandTracker {

    private final Set<Object> instances = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> aliases = new ConcurrentHashMap<>();

    /**
     * Tracks a command instance and its registered names.
     *
     * @param instance Command instance
     * @param names    All registered names (including main name and aliases)
     */
    public void track(Object instance, Set<String> names) {
        instances.add(instance);

        if (!names.isEmpty()) {
            // Use first name as key, store all names as aliases
            String mainName = names.iterator().next();
            aliases.put(mainName.toLowerCase(), names);
        }
    }

    /**
     * Untracks a command by name.
     *
     * @param commandName Command name to untrack
     * @return true if untracked, false if not found
     */
    public boolean untrack(String commandName) {
        Set<String> names = aliases.remove(commandName.toLowerCase());
        if (names != null) {
            // Remove all aliases as well
            for (String name : names) {
                aliases.remove(name.toLowerCase());
            }
            return true;
        }
        return false;
    }

    /**
     * Checks if this tracker owns a command.
     *
     * @param commandName Command name to check
     * @return true if owned by this tracker
     */
    public boolean owns(String commandName) {
        return aliases.containsKey(commandName.toLowerCase());
    }

    /**
     * Gets all tracked command names.
     *
     * @return Unmodifiable view of tracked names
     */
    public Set<String> getTrackedNames() {
        return Collections.unmodifiableSet(aliases.keySet());
    }

    /**
     * Gets all aliases for a command.
     *
     * @param commandName Command name
     * @return Set of all names (main + aliases), or null if not found
     */
    public Set<String> getAliases(String commandName) {
        return aliases.get(commandName.toLowerCase());
    }
}
