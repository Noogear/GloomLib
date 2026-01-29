package gloomlib.translation.tag;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

/**
 * Tag resolver for {@code <arg:N>} placeholders in translation strings.
 *
 * <p>Substitutes indexed tags with corresponding components from the argument list.</p>
 *
 * @since 1.0.0
 */
public final class IndexedArgumentTag implements TagResolver {

    private final List<? extends ComponentLike> arguments;

    /**
     * Creates a new indexed argument tag resolver.
     *
     * @param arguments the list of argument components
     */
    public IndexedArgumentTag(@NotNull List<? extends ComponentLike> arguments) {
        this.arguments = Objects.requireNonNull(arguments, "arguments");
    }

    @Override
    public @Nullable Tag resolve(@NotNull String name, @NotNull ArgumentQueue queue, @NotNull Context ctx)
            throws ParsingException {
        if (!has(name)) {
            return null;
        }

        // Read index from argument queue (MiniMessage parses <arg:0> as tag="arg", arg="0")
        int index = queue.popOr("No argument index provided")
                .asInt()
                .orElseThrow(() -> ctx.newException("Invalid argument index", queue));

        if (index < 0 || index >= arguments.size()) {
            throw ctx.newException("Argument index out of range: " + index, queue);
        }

        return Tag.selfClosingInserting(arguments.get(index));
    }

    @Override
    public boolean has(@NotNull String name) {
        return "arg".equals(name);
    }

    /**
     * Creates an indexed argument tag resolver from string arguments.
     *
     * @param arguments the string arguments to convert to text components
     * @return a new tag resolver
     */
    public static IndexedArgumentTag ofStrings(@NotNull List<String> arguments) {
        return new IndexedArgumentTag(arguments.stream()
                .<ComponentLike>map(Component::text)
                .toList());
    }

    /**
     * Creates an indexed argument tag resolver from components.
     *
     * @param arguments the component arguments
     * @return a new tag resolver
     */
    public static IndexedArgumentTag of(@NotNull ComponentLike... arguments) {
        return new IndexedArgumentTag(List.of(arguments));
    }
}
