package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.exception.CommandException;
import gloomlib.command.message.CommandMessages;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Text Color Argument Resolver.
 *
 * <p>
 * Supports Adventure API {@link TextColor} type,
 * including named colors and hexadecimal colors.
 * </p>
 *
 * <h2>Supported Formats</h2>
 * <ul>
 * <li>Named colors: {@code red}, {@code green}, {@code blue}, ...</li>
 * <li>Hexadecimal: {@code #FF0000}, {@code #00FF00}, ...</li>
 * </ul>
 */
public class TextColorResolver implements ArgumentResolver<TextColor> {

    /**
     * Named color map
     */
    private static final Map<String, NamedTextColor> NAMED_COLORS = new HashMap<>();

    static {
        NAMED_COLORS.put("black", NamedTextColor.BLACK);
        NAMED_COLORS.put("dark_blue", NamedTextColor.DARK_BLUE);
        NAMED_COLORS.put("dark_green", NamedTextColor.DARK_GREEN);
        NAMED_COLORS.put("dark_aqua", NamedTextColor.DARK_AQUA);
        NAMED_COLORS.put("dark_red", NamedTextColor.DARK_RED);
        NAMED_COLORS.put("dark_purple", NamedTextColor.DARK_PURPLE);
        NAMED_COLORS.put("gold", NamedTextColor.GOLD);
        NAMED_COLORS.put("gray", NamedTextColor.GRAY);
        NAMED_COLORS.put("dark_gray", NamedTextColor.DARK_GRAY);
        NAMED_COLORS.put("blue", NamedTextColor.BLUE);
        NAMED_COLORS.put("green", NamedTextColor.GREEN);
        NAMED_COLORS.put("aqua", NamedTextColor.AQUA);
        NAMED_COLORS.put("red", NamedTextColor.RED);
        NAMED_COLORS.put("light_purple", NamedTextColor.LIGHT_PURPLE);
        NAMED_COLORS.put("yellow", NamedTextColor.YELLOW);
        NAMED_COLORS.put("white", NamedTextColor.WHITE);
    }

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        return StringArgumentType.word();
    }

    @Override
    public TextColor resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        String input = context.getArgument(name, String.class).toLowerCase();

        // Try named color
        NamedTextColor namedColor = NAMED_COLORS.get(input);
        if (namedColor != null) {
            return namedColor;
        }

        // Try hex color
        if (input.startsWith("#") && input.length() == 7) {
            TextColor hexColor = TextColor.fromHexString(input);
            if (hexColor != null) {
                return hexColor;
            }
        }

        // Try hex color without #
        if (input.length() == 6) {
            TextColor hexColor = TextColor.fromHexString("#" + input);
            if (hexColor != null) {
                return hexColor;
            }
        }

        throw new CommandException(CommandMessages.ARG_COLOR_INVALID.get(Component.text(input)));
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining().toLowerCase();

        // Provide named color suggestions
        for (String colorName : NAMED_COLORS.keySet()) {
            if (colorName.startsWith(remaining)) {
                builder.suggest(colorName);
            }
        }

        // If input starts with #, suggest hex format
        if (remaining.startsWith("#") && remaining.length() < 7) {
            builder.suggest("#FF0000");
            builder.suggest("#00FF00");
            builder.suggest("#0000FF");
        }

        return builder.buildFuture();
    }

    @Override
    public Class<TextColor> getType() {
        return TextColor.class;
    }
}
