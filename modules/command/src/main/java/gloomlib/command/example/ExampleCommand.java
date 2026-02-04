package gloomlib.command.example;

import gloomlib.command.annotation.*;
import gloomlib.command.context.GloomCommandContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.time.Duration;

/**
 * Example Command Class.
 *
 * <p>
 * Demonstrates various features of the GloomCommand framework.
 * </p>
 *
 * <h2>Feature Demo</h2>
 * <ul>
 * <li>Basic command definition</li>
 * <li>Subcommands</li>
 * <li>Argument annotations</li>
 * <li>Permission control</li>
 * <li>Player restrictions</li>
 * <li>Cooldown system</li>
 * </ul>
 */
@Command("example")
@Description("GloomCommand 示例命令")
@Permission("gloomlib.command.example")
public class ExampleCommand {

    /**
     * Default execution (no arguments).
     */
    @Usage
    public void showHelp(GloomCommandContext ctx) {
        ctx.getSender().sendMessage(Component.text("=== GloomCommand Example ===", NamedTextColor.GOLD));
        ctx.getSender().sendMessage(Component.text("/example help - Show help", NamedTextColor.YELLOW));
        ctx.getSender().sendMessage(Component.text("/example greet <player> - Greet player", NamedTextColor.YELLOW));
        ctx.getSender()
                .sendMessage(Component.text("/example gamemode <mode> - Change gamemode", NamedTextColor.YELLOW));
        ctx.getSender()
                .sendMessage(Component.text("/example teleport <world> - Teleport to world", NamedTextColor.YELLOW));
    }

    /**
     * Greet subcommand.
     */
    @SubCommand("greet")
    @Description("Greet a player")
    public void greet(
            GloomCommandContext ctx,
            @Arg("player") Player target,
            @Arg("message") @Optional @Default("Hello!") String message) {
        target.sendMessage(
                Component.text("[" + ctx.getSender().getName() + " says] ", NamedTextColor.AQUA)
                        .append(Component.text(message, NamedTextColor.WHITE)));
        ctx.reply(Component.text("Message sent to " + target.getName() + "!", NamedTextColor.GREEN));
    }

    /**
     * Change gamemode.
     */
    @SubCommand("gamemode")
    @Description("Change game mode")
    @PlayerOnly
    @Permission("gloomlib.command.example.gamemode")
    public void setGameMode(
            Player player,
            @Arg("mode") GameMode mode) {
        player.setGameMode(mode);
        player.sendMessage(
                Component.text("Game mode changed to: ", NamedTextColor.GREEN)
                        .append(Component.text(mode.name(), NamedTextColor.YELLOW)));
    }

    /**
     * Teleport to specified world.
     */
    @SubCommand("teleport")
    @Description("Teleport to specified world")
    @PlayerOnly
    @Cooldown(value = 30, message = "<red>传送冷却中！请等待 <remaining> 后再试。")
    public void teleportToWorld(
            Player player,
            @Arg("world") World world) {
        player.teleport(world.getSpawnLocation());
        player.sendMessage(
                Component.text("Teleported to world: ", NamedTextColor.GREEN)
                        .append(Component.text(world.getName(), NamedTextColor.AQUA)));
    }

    /**
     * Temporary ban.
     */
    @SubCommand("tempban")
    @Description("Temporarily ban a player")
    @Permission("gloomlib.command.example.ban")
    public void tempBan(
            GloomCommandContext ctx,
            @Arg("player") Player target,
            @Arg("duration") Duration duration,
            @Arg("reason") @Optional @Greedy @Default("Violation") String reason) {
        // Example: Only sends message, does not actually ban
        ctx.reply(Component.text()
                .append(Component.text("Temporarily banned ", NamedTextColor.RED))
                .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                .append(Component.text(", duration: ", NamedTextColor.RED))
                .append(Component.text(formatDuration(duration), NamedTextColor.YELLOW))
                .append(Component.text(", reason: ", NamedTextColor.RED))
                .append(Component.text(reason, NamedTextColor.WHITE))
                .build());
    }

    /**
     * Numeric argument with range constraint.
     */
    @SubCommand("heal")
    @Description("Heal health")
    @PlayerOnly
    @SuppressWarnings("deprecation")
    public void heal(
            Player player,
            @Arg("amount") @Range(min = 1, max = 20) @Optional @Default("20") int amount) {
        double newHealth = Math.min(player.getHealth() + amount, player.getMaxHealth());
        player.setHealth(newHealth);
        player.sendMessage(
                Component.text("Healed ", NamedTextColor.GREEN)
                        .append(Component.text(amount + " health points", NamedTextColor.RED)));
    }

    /**
     * Formats duration.
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)
            sb.append(days).append("d");
        if (hours > 0)
            sb.append(hours).append("h");
        if (minutes > 0)
            sb.append(minutes).append("m");
        if (secs > 0)
            sb.append(secs).append("s");
        return sb.toString();
    }
}
