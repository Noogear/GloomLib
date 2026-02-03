package gloomlib.command.condition;

import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 命令条件注册表。
 *
 * <p>
 * 管理已注册的条件实例。
 * </p>
 */
public class CommandConditionRegistry {

    private final Map<String, CommandCondition> conditions = new ConcurrentHashMap<>();

    /**
     * 注册条件。
     *
     * @param name      条件名称
     * @param condition 条件实例
     */
    public void register(String name, CommandCondition condition) {
        conditions.put(name, condition);
    }

    /**
     * 获取条件。
     *
     * @param name 条件名称
     * @return 条件实例，或 null
     */
    public @Nullable CommandCondition getCondition(String name) {
        return conditions.get(name);
    }

    /**
     * 检查条件是否已注册。
     *
     * @param name 条件名称
     * @return 是否已注册
     */
    public boolean hasCondition(String name) {
        return conditions.containsKey(name);
    }

    /**
     * 获取所有条件。
     *
     * @return 条件映射
     */
    public Map<String, CommandCondition> getAllConditions() {
        return Map.copyOf(conditions);
    }
}
