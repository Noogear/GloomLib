package gloomlib.command.example;

import gloomlib.command.annotation.*;
import gloomlib.command.context.AsyncContext;
import gloomlib.command.util.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Example command demonstrating proper AsyncContext usage.
 *
 * <p>
 * Shows correct patterns for:
 * <ul>
 * <li>Async I/O operations (database, file, network)</li>
 * <li>Thread switching with runSync()</li>
 * <li>Folia-compatible region scheduling</li>
 * <li>CompletableFuture patterns</li>
 * </ul>
 * </p>
 *
 * <h2>Important Rules</h2>
 * <ul>
 * <li>✅ Use runAsync() for: Database queries, file I/O, HTTP requests</li>
 * <li>❌ Never use Bukkit API in runAsync() threads</li>
 * <li>✅ Use runSync() or runOn(Entity/Location) for Bukkit API calls</li>
 * </ul>
 */
@Command("asyncdemo")
@Description("Demonstrates proper async command patterns")
@Permission("gloomlib.demo.async")
public class AsyncDatabaseCommand {

    // Mock database (simulates slow I/O)
    private static final Map<UUID, PlayerData> mockDatabase = new HashMap<>();

    @SubCommand("load")
    @Async
    @Description("Load player data from database")
    public void loadData(AsyncContext ctx, Player player) {
        player.sendMessage(MessageUtils.deserialize("<gray>Loading your data...</gray>"));

        // ✅ CORRECT: Database query in async thread
        ctx.runAsync(() -> queryDatabase(player.getUniqueId()))
                .thenAccept(data -> {
                    // ✅ CORRECT: Switch to main thread for Bukkit API
                    ctx.runSync(() -> {
                        player.sendMessage(MessageUtils.deserialize(
                                "<green>Data loaded!</green> Level: <yellow>" + data.level + "</yellow>, " +
                                        "Coins: <gold>" + data.coins + "</gold>"
                        ));
                    });
                })
                .exceptionally(ex -> {
                    ctx.runSync(() -> {
                        player.sendMessage(MessageUtils.deserialize("<red>Failed to load data: " + ex.getMessage() + "</red>"));
                    });
                    return null;
                });
    }

    @SubCommand("save")
    @Async
    @Description("Save player data to database")
    public void saveData(AsyncContext ctx, Player player, @Arg("level") int level) {
        player.sendMessage(MessageUtils.deserialize("<gray>Saving your data...</gray>"));

        PlayerData data = new PlayerData(player.getUniqueId(), level, 1000);

        // ✅ CORRECT: Database write in async thread
        ctx.runAsync(() -> saveToDatabase(data))
                .thenRun(() -> {
                    // ✅ CORRECT: Message sent via AsyncContext (handles threading)
                    ctx.reply(MessageUtils.deserialize("<green>Data saved successfully!</green>"));
                })
                .exceptionally(ex -> {
                    ctx.reply(MessageUtils.deserialize("<red>Save failed: " + ex.getMessage() + "</red>"));
                    return null;
                });
    }

    @SubCommand("teleport")
    @Async
    @Description("Demonstrates entity region scheduling")
    public void teleportAfterLoad(AsyncContext ctx, Player player) {
        // ✅ CORRECT: I/O in async
        ctx.runAsync(() -> queryDatabase(player.getUniqueId()))
                .thenAccept(data -> {
                    // ✅ CORRECT: Entity operations on entity's region (Folia-safe)
                    ctx.runOn(player, () -> {
                        player.teleport(player.getWorld().getSpawnLocation());
                        player.sendMessage(MessageUtils.deserialize(
                                "<green>Teleported!</green> Your level is <yellow>" + data.level + "</yellow>"
                        ));
                    });
                });
    }

    // Mock database methods (simulate slow I/O)

    /**
     * Simulates a slow database query.
     * ✅ Safe to run in async thread - no Bukkit API calls.
     */
    private PlayerData queryDatabase(UUID playerId) {
        try {
            Thread.sleep(500); // Simulate network latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return mockDatabase.getOrDefault(playerId, new PlayerData(playerId, 1, 0));
    }

    /**
     * Simulates a slow database write.
     * ✅ Safe to run in async thread - no Bukkit API calls.
     */
    private void saveToDatabase(PlayerData data) {
        try {
            Thread.sleep(300); // Simulate write latency
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        mockDatabase.put(data.playerId, data);
    }

    /**
     * Simple data class for player information.
     */
    private record PlayerData(UUID playerId, int level, int coins) {
    }
}
