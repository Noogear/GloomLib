package gloomlib.command.example;

import gloomlib.command.annotation.*;
import gloomlib.command.util.MessageUtils;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;

/**
 * Example command demonstrating modern MiniMessage formatting.
 *
 * <p>
 * Showcases:
 * <ul>
 * <li>MiniMessage color tags</li>
 * <li>Gradients and rainbow text</li>
 * <li>Click and hover events</li>
 * <li>Component building patterns</li>
 * </ul>
 * </p>
 */
@Command("moderndemo")
@Description("Demonstrates modern MiniMessage formatting")
@Permission("gloomlib.demo.modern")
public class ModernMessagingCommand {

    @Usage
    @Description("Show MiniMessage color examples")
    public void colors(Player player) {
        player.sendMessage(MessageUtils.deserialize("<red>Red text</red>"));
        player.sendMessage(MessageUtils.deserialize("<gold><bold>Bold gold text</bold></gold>"));
        player.sendMessage(MessageUtils.deserialize("<gradient:red:blue>Gradient text!</gradient>"));
        player.sendMessage(MessageUtils.deserialize("<rainbow:!>Rainbow text!</rainbow>"));
    }

    @SubCommand("interactive")
    @Description("Show interactive components")
    public void interactive(Player player) {
        Component message = MessageUtils.deserialize(
                "<hover:show_text:'<green>Click to run /help</green>'>" +
                        "<click:run_command:/help>" +
                        "<yellow><bold>[Click Me!]</bold></yellow>" +
                        "</click>" +
                        "</hover>"
        );
        player.sendMessage(message);

        player.sendMessage(MessageUtils.deserialize(
                "<gray>Try hovering and clicking the button above!</gray>"
        ));
    }

    @SubCommand("styled")
    @Description("Show styled text examples")
    public void styled(Player player) {
        player.sendMessage(MessageUtils.deserialize("<bold>Bold</bold> text"));
        player.sendMessage(MessageUtils.deserialize("<italic>Italic</italic> text"));
        player.sendMessage(MessageUtils.deserialize("<underlined>Underlined</underlined> text"));
        player.sendMessage(MessageUtils.deserialize("<strikethrough>Strikethrough</strikethrough> text"));
        player.sendMessage(MessageUtils.deserialize("<obfuscated>Obfuscated</obfuscated> text"));
    }

    @SubCommand("complex")
    @Description("Show complex nested formatting")
    public void complex(Player player) {
        Component complex = MessageUtils.deserialize(
                "<gray>Welcome </gray>" +
                        "<gradient:gold:yellow><bold>" + player.getName() + "</bold></gradient>" +
                        "<gray>! You have </gray>" +
                        "<hover:show_text:'<green>Very impressive!</green>'>" +
                        "<gradient:green:aqua><bold>1337</bold></gradient>" +
                        "</hover>" +
                        "<gray> points!</gray>"
        );
        player.sendMessage(complex);
    }
}
