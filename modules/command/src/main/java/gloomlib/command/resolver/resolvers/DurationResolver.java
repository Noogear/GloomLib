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

import java.lang.reflect.Parameter;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 时间间隔参数解析器。
 *
 * <p>
 * 支持格式：{@code 1d2h3m4s}、{@code 30m}、{@code 1h30m} 等。
 * </p>
 */
public class DurationResolver implements ArgumentResolver<Duration> {

    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?",
            Pattern.CASE_INSENSITIVE);

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        return StringArgumentType.word();
    }

    @Override
    public Duration resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        String input = context.getArgument(name, String.class);
        return parse(input);
    }

    /**
     * 解析时间字符串。
     *
     * @param input 输入字符串
     * @return Duration 对象
     */
    public static Duration parse(String input) {
        if (input == null || input.isEmpty()) {
            throw new CommandException(CommandMessages.VALIDATION_DURATION_EMPTY.get());
        }

        // 尝试纯数字（默认秒）
        if (input.matches("\\d+")) {
            return Duration.ofSeconds(Long.parseLong(input));
        }

        Matcher matcher = DURATION_PATTERN.matcher(input);
        if (!matcher.matches()) {
            throw new CommandException(CommandMessages.VALIDATION_DURATION_INVALID.get(
                    Component.text(input, NamedTextColor.YELLOW)));
        }

        long days = matcher.group(1) != null ? Long.parseLong(matcher.group(1)) : 0;
        long hours = matcher.group(2) != null ? Long.parseLong(matcher.group(2)) : 0;
        long minutes = matcher.group(3) != null ? Long.parseLong(matcher.group(3)) : 0;
        long seconds = matcher.group(4) != null ? Long.parseLong(matcher.group(4)) : 0;

        if (days == 0 && hours == 0 && minutes == 0 && seconds == 0) {
            throw new CommandException(CommandMessages.VALIDATION_DURATION_POSITIVE.get());
        }

        return Duration.ofDays(days)
                .plusHours(hours)
                .plusMinutes(minutes)
                .plusSeconds(seconds);
    }

    /**
     * 格式化 Duration 为可读字符串。
     *
     * @param duration Duration 对象
     * @return 可读字符串
     */
    public static String format(Duration duration) {
        if (duration.isZero()) {
            return "0s";
        }

        long totalSeconds = duration.getSeconds();
        long days = totalSeconds / 86400;
        long hours = (totalSeconds % 86400) / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;

        StringBuilder sb = new StringBuilder();
        if (days > 0)
            sb.append(days).append("d");
        if (hours > 0)
            sb.append(hours).append("h");
        if (minutes > 0)
            sb.append(minutes).append("m");
        if (seconds > 0)
            sb.append(seconds).append("s");

        return sb.toString();
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining();

        if (remaining.isEmpty()) {
            builder.suggest("30s");
            builder.suggest("5m");
            builder.suggest("1h");
            builder.suggest("1d");
        } else if (remaining.matches("\\d+")) {
            builder.suggest(remaining + "s");
            builder.suggest(remaining + "m");
            builder.suggest(remaining + "h");
            builder.suggest(remaining + "d");
        }

        return builder.buildFuture();
    }

    @Override
    public Class<Duration> getType() {
        return Duration.class;
    }
}
