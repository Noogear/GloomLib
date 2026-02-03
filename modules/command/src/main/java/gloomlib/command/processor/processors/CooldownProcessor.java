package gloomlib.command.processor.processors;

import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.processor.PreProcessor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 命令冷却处理器。
 *
 * <p>
 * 管理命令冷却时间，防止命令被频繁执行。
 * </p>
 */
public class CooldownProcessor implements PreProcessor {

    /** 冷却数据：命令名 -> (玩家名 -> 上次执行时间) */
    private final Map<String, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    /** 默认冷却时间（毫秒） */
    private long defaultCooldown = 0;

    /** 默认冷却消息 */
    private String defaultMessage = "<red>请等待 <yellow>{remaining}</yellow> 后再使用此命令！</red>";

    /**
     * 设置冷却时间。
     *
     * @param commandName 命令名
     * @param playerName  玩家名
     * @param durationMs  冷却时长（毫秒）
     */
    public void setCooldown(String commandName, String playerName, long durationMs) {
        cooldowns.computeIfAbsent(commandName, k -> new ConcurrentHashMap<>())
                .put(playerName, System.currentTimeMillis() + durationMs);
    }

    /**
     * 检查是否处于冷却中。
     *
     * @param commandName 命令名
     * @param playerName  玩家名
     * @return 剩余冷却时间（毫秒），0 表示不在冷却中
     */
    public long getRemainingCooldown(String commandName, String playerName) {
        Map<String, Long> commandCooldowns = cooldowns.get(commandName);
        if (commandCooldowns == null)
            return 0;

        Long expireTime = commandCooldowns.get(playerName);
        if (expireTime == null)
            return 0;

        long remaining = expireTime - System.currentTimeMillis();
        if (remaining <= 0) {
            commandCooldowns.remove(playerName);
            return 0;
        }

        return remaining;
    }

    /**
     * 清除玩家的冷却。
     *
     * @param commandName 命令名
     * @param playerName  玩家名
     */
    public void clearCooldown(String commandName, String playerName) {
        Map<String, Long> commandCooldowns = cooldowns.get(commandName);
        if (commandCooldowns != null) {
            commandCooldowns.remove(playerName);
        }
    }

    /**
     * 清除所有冷却。
     */
    public void clearAllCooldowns() {
        cooldowns.clear();
    }

    @Override
    public Result preProcess(GloomCommandContext context) {
        // 注意：实际的冷却检查需要与 CommandRegistry 集成
        // 这里提供基础的冷却管理功能
        return Result.CONTINUE;
    }

    @Override
    public int getPriority() {
        return 100; // 在权限检查之后执行
    }

    /**
     * 格式化剩余时间。
     *
     * @param remainingMs 剩余毫秒
     * @return 格式化的时间字符串
     */
    public static String formatRemainingTime(long remainingMs) {
        if (remainingMs <= 0)
            return "0秒";

        long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs);
        long hours = TimeUnit.MILLISECONDS.toHours(remainingMs);

        if (hours > 0) {
            return hours + "小时" + (minutes % 60) + "分钟";
        } else if (minutes > 0) {
            return minutes + "分钟" + (seconds % 60) + "秒";
        } else {
            return seconds + "秒";
        }
    }
}
