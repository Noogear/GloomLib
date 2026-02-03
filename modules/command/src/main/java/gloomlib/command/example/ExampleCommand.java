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
 * 示例命令类。
 *
 * <p>
 * 展示 GloomCommand 框架的各种功能。
 * </p>
 *
 * <h2>功能演示</h2>
 * <ul>
 * <li>基础命令定义</li>
 * <li>子命令</li>
 * <li>参数注解</li>
 * <li>权限控制</li>
 * <li>玩家限制</li>
 * <li>冷却系统</li>
 * </ul>
 */
@Command("example")
@Description("GloomCommand 示例命令")
@Permission("gloomlib.command.example")
public class ExampleCommand {

    /**
     * 默认执行（无参数）。
     */
    @Usage
    public void showHelp(GloomCommandContext ctx) {
        ctx.getSender().sendMessage(Component.text("=== GloomCommand 示例 ===", NamedTextColor.GOLD));
        ctx.getSender().sendMessage(Component.text("/example help - 显示帮助", NamedTextColor.YELLOW));
        ctx.getSender().sendMessage(Component.text("/example greet <player> - 打招呼", NamedTextColor.YELLOW));
        ctx.getSender().sendMessage(Component.text("/example gamemode <mode> - 切换游戏模式", NamedTextColor.YELLOW));
        ctx.getSender().sendMessage(Component.text("/example teleport <world> - 传送到世界", NamedTextColor.YELLOW));
    }

    /**
     * 打招呼子命令。
     */
    @SubCommand("greet")
    @Description("向玩家打招呼")
    public void greet(
            GloomCommandContext ctx,
            @Arg("player") Player target,
            @Arg("message") @Optional @Default("你好！") String message) {
        target.sendMessage(
                Component.text("[" + ctx.getSender().getName() + " 说] ", NamedTextColor.AQUA)
                        .append(Component.text(message, NamedTextColor.WHITE)));
        ctx.reply(Component.text("已向 " + target.getName() + " 发送消息！", NamedTextColor.GREEN));
    }

    /**
     * 切换游戏模式。
     */
    @SubCommand("gamemode")
    @Description("切换游戏模式")
    @PlayerOnly
    @Permission("gloomlib.command.example.gamemode")
    public void setGameMode(
            Player player,
            @Arg("mode") GameMode mode) {
        player.setGameMode(mode);
        player.sendMessage(
                Component.text("游戏模式已切换为: ", NamedTextColor.GREEN)
                        .append(Component.text(mode.name(), NamedTextColor.YELLOW)));
    }

    /**
     * 传送到指定世界。
     */
    @SubCommand("teleport")
    @Description("传送到指定世界")
    @PlayerOnly
    @Cooldown(value = 30, message = "<red>传送冷却中！请等待 <remaining> 后再试。")
    public void teleportToWorld(
            Player player,
            @Arg("world") World world) {
        player.teleport(world.getSpawnLocation());
        player.sendMessage(
                Component.text("已传送到世界: ", NamedTextColor.GREEN)
                        .append(Component.text(world.getName(), NamedTextColor.AQUA)));
    }

    /**
     * 设置临时封禁。
     */
    @SubCommand("tempban")
    @Description("临时封禁玩家")
    @Permission("gloomlib.command.example.ban")
    public void tempBan(
            GloomCommandContext ctx,
            @Arg("player") Player target,
            @Arg("duration") Duration duration,
            @Arg("reason") @Optional @Greedy @Default("违规行为") String reason) {
        // 示例：仅发送消息，不实际封禁
        ctx.reply(Component.text()
                .append(Component.text("已临时封禁 ", NamedTextColor.RED))
                .append(Component.text(target.getName(), NamedTextColor.YELLOW))
                .append(Component.text("，时长: ", NamedTextColor.RED))
                .append(Component.text(formatDuration(duration), NamedTextColor.YELLOW))
                .append(Component.text("，原因: ", NamedTextColor.RED))
                .append(Component.text(reason, NamedTextColor.WHITE))
                .build());
    }

    /**
     * 带范围约束的数值参数。
     */
    @SubCommand("heal")
    @Description("恢复生命值")
    @PlayerOnly
    @SuppressWarnings("deprecation")
    public void heal(
            Player player,
            @Arg("amount") @Range(min = 1, max = 20) @Optional @Default("20") int amount) {
        double newHealth = Math.min(player.getHealth() + amount, player.getMaxHealth());
        player.setHealth(newHealth);
        player.sendMessage(
                Component.text("已恢复 ", NamedTextColor.GREEN)
                        .append(Component.text(amount + " 点生命值", NamedTextColor.RED)));
    }

    /**
     * 格式化时长。
     */
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)
            sb.append(days).append("天");
        if (hours > 0)
            sb.append(hours).append("小时");
        if (minutes > 0)
            sb.append(minutes).append("分钟");
        if (secs > 0)
            sb.append(secs).append("秒");
        return sb.toString();
    }
}
