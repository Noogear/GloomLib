package gloomlib.command.processor.processors;

import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.processor.PreProcessor;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Command cooldown management.
 */
public class CooldownProcessor implements PreProcessor {

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long MINUTES_PER_HOUR = 60L;

    private final Map<String, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<>();

    public static String formatRemainingTime(long remainingMs) {
        if (remainingMs <= 0)
            return "0s";

        long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs);
        long hours = TimeUnit.MILLISECONDS.toHours(remainingMs);

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes % MINUTES_PER_HOUR);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds % SECONDS_PER_MINUTE);
        } else {
            return seconds + "s";
        }
    }

    public void setCooldown(String commandName, UUID playerUuid, long durationMs) {
        cooldowns.computeIfAbsent(commandName, k -> new ConcurrentHashMap<>())
                .put(playerUuid, System.currentTimeMillis() + durationMs);
    }

    public long getRemainingCooldown(String commandName, UUID playerUuid) {
        Map<UUID, Long> commandCooldowns = cooldowns.get(commandName);
        if (commandCooldowns == null)
            return 0;

        Long expireTime = commandCooldowns.get(playerUuid);
        if (expireTime == null)
            return 0;

        long remaining = expireTime - System.currentTimeMillis();
        if (remaining <= 0) {
            commandCooldowns.remove(playerUuid);
            return 0;
        }

        return remaining;
    }

    public void clearCooldown(String commandName, UUID playerUuid) {
        Map<UUID, Long> commandCooldowns = cooldowns.get(commandName);
        if (commandCooldowns != null) {
            commandCooldowns.remove(playerUuid);
        }
    }

    /**
     * Clears all cooldowns.
     */
    public void clearAllCooldowns() {
        cooldowns.clear();
    }

    @Override
    public Result preProcess(GloomCommandContext context) {
        // Note: Actual cooldown check needs integration with CommandRegistry
        // This provides basic cooldown management functionality
        return Result.CONTINUE;
    }

    @Override
    public int getPriority() {
        return 100; // Execute after permission check
    }
}
