package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 文本颜色参数解析器。
 *
 * <p>
 * 支持 Adventure API 的 {@link TextColor} 类型，
 * 包括命名颜色和十六进制颜色。
 * </p>
 *
 * <h2>支持的格式</h2>
 * <ul>
 * <li>命名颜色：{@code red}, {@code green}, {@code blue}, ...</li>
 * <li>十六进制：{@code #FF0000}, {@code #00FF00}, ...</li>
 * </ul>
 */
public class TextColorResolver implements ArgumentResolver<TextColor> {

    /** 命名颜色映射表 */
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

        // 尝试命名颜色
        NamedTextColor namedColor = NAMED_COLORS.get(input);
        if (namedColor != null) {
            return namedColor;
        }

        // 尝试十六进制颜色
        if (input.startsWith("#") && input.length() == 7) {
            TextColor hexColor = TextColor.fromHexString(input);
            if (hexColor != null) {
                return hexColor;
            }
        }

        // 尝试不带 # 的十六进制
        if (input.length() == 6) {
            TextColor hexColor = TextColor.fromHexString("#" + input);
            if (hexColor != null) {
                return hexColor;
            }
        }

        throw new IllegalArgumentException("无效的颜色值: " + input +
                "。支持的格式: 命名颜色 (如 red, blue) 或十六进制 (如 #FF0000)");
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining().toLowerCase();

        // 提供命名颜色建议
        for (String colorName : NAMED_COLORS.keySet()) {
            if (colorName.startsWith(remaining)) {
                builder.suggest(colorName);
            }
        }

        // 如果输入以 # 开头，提示十六进制格式
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
