package gloomlib.command.core.internal;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Thread-safe command registration tracker.
 */
public final class CommandTracker {

    private final Set<Object> instances = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<String>> aliases = new ConcurrentHashMap<>();

    /**
     * Tracks a command instance.
     *
     * @param instance Command instance
     * @param names    Command names (aliases)
     */
    public void track(Object instance, Set<String> names) {
        instances.add(instance);

        if (!names.isEmpty()) {
            for (String name : names) {
                aliases.put(name.toLowerCase(), names);
            }
        }
    }

    /**
     * Untracks a command.
     *
     * @param commandName Command name
     * @return true if found and removed
     */
    public boolean untrack(String commandName) {
        Set<String> names = aliases.remove(commandName.toLowerCase());
        if (names != null) {
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
