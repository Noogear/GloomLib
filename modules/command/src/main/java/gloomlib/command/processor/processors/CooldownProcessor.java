package gloomlib.command.processor.processors;

import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.processor.PreProcessor;
import net.kyori.adventure.text.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Command Cooldown Processor.
 *
 * <p>
 * Manages command cooldowns to prevent command spamming.
 * </p>
 */
public class CooldownProcessor implements PreProcessor {

    /** Cooldown data: commandName -> (playerUuid -> lastExecutionTime) */
    private final Map<String, Map<UUID, Long>> cooldowns = new ConcurrentHashMap<>();

    /**
     * Sets cooldown time.
     *
     * @param commandName Command name
     * @param playerUuid  Player UUID
     * @param durationMs  Cooldown duration (milliseconds)
     */
    public void setCooldown(String commandName, UUID playerUuid, long durationMs) {
        cooldowns.computeIfAbsent(commandName, k -> new ConcurrentHashMap<>())
                .put(playerUuid, System.currentTimeMillis() + durationMs);
    }

    /**
     * Checks if cooldown is active.
     *
     * @param commandName Command name
     * @param playerUuid  Player UUID
     * @return Remaining cooldown time (milliseconds), 0 if not in cooldown
     */
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

    /**
     * Clears cooldown for a player.
     *
     * @param commandName Command name
     * @param playerUuid  Player UUID
     */
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

    /**
     * Formats remaining time.
     *
     * @param remainingMs Remaining milliseconds
     * @return Formatted time string
     */
    public static String formatRemainingTime(long remainingMs) {
        if (remainingMs <= 0)
            return "0s";

        long seconds = TimeUnit.MILLISECONDS.toSeconds(remainingMs);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(remainingMs);
        long hours = TimeUnit.MILLISECONDS.toHours(remainingMs);

        if (hours > 0) {
            return hours + "h " + (minutes % 60) + "m";
        } else if (minutes > 0) {
            return minutes + "m " + (seconds % 60) + "s";
        } else {
            return seconds + "s";
        }
    }
}
