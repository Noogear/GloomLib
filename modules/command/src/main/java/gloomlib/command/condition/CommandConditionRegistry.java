package gloomlib.command.condition;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Command Condition Registry.
 *
 * <p>
 * Manages registered condition instances.
 * </p>
 */
public class CommandConditionRegistry {

    private final Map<String, CommandCondition> conditions = new ConcurrentHashMap<>();

    /**
     * Registers a condition.
     *
     * @param name      Condition name
     * @param condition Condition instance
     */
    public void register(String name, CommandCondition condition) {
        conditions.put(name, condition);
    }

    /**
     * Gets a condition.
     *
     * @param name Condition name
     * @return Condition instance, or null
     */
    public @Nullable CommandCondition getCondition(String name) {
        return conditions.get(name);
    }

    /**
     * Checks if a condition is registered.
     *
     * @param name Condition name
     * @return True if registered
     */
    public boolean hasCondition(String name) {
        return conditions.containsKey(name);
    }

    /**
     * Gets all conditions.
     *
     * @return Condition map
     */
    public Map<String, CommandCondition> getAllConditions() {
        return Map.copyOf(conditions);
    }
}
