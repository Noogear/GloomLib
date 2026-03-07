package gloomlib.command.api.condition;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for named {@link CommandCondition} instances.
 *
 * <p>
 * Conditions are registered by name and looked up by the {@code @Condition} annotation
 * on command handler methods. The registry is thread-safe and suitable for asynchronous
 * registration during plugin startup.
 * </p>
 */
public class CommandConditionRegistry {

    private final ConcurrentHashMap<String, CommandCondition> conditions = new ConcurrentHashMap<>();

    /**
     * Registers a named condition.
     *
     * @param name      Unique condition name (case-sensitive)
     * @param condition Condition to register
     */
    public void register(@NotNull String name, @NotNull CommandCondition condition) {
        conditions.put(name, condition);
    }

    /**
     * Looks up a registered condition by name.
     *
     * @param name Condition name
     * @return The condition, or {@code null} if not registered
     */
    @Nullable
    public CommandCondition get(@NotNull String name) {
        return conditions.get(name);
    }

    /**
     * Returns whether a condition with the given name is registered.
     *
     * @param name Condition name
     * @return {@code true} if the condition exists
     */
    public boolean hasCondition(@NotNull String name) {
        return conditions.containsKey(name);
    }

    /**
     * Removes a registered condition.
     *
     * @param name Condition name to remove
     */
    public void unregister(@NotNull String name) {
        conditions.remove(name);
    }

    /** Removes all registered conditions. */
    public void clear() {
        conditions.clear();
    }
}
