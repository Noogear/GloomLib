package gloomlib.translation.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

/**
 * Provides shared MiniMessage instances for the translation system.
 *
 * @since 1.0.0
 */
public final class MiniMessages {

    private static final MiniMessage DEFAULT = MiniMessage.miniMessage();
    private static final MiniMessage STRICT = MiniMessage.builder().strict(true).build();
    private static final MiniMessage EMPTY_TAGS = MiniMessage.builder().tags(TagResolver.empty()).build();

    private MiniMessages() {
        throw new UnsupportedOperationException();
    }

    /**
     * Gets the default MiniMessage instance.
     *
     * @return the default instance
     */
    public static @NotNull MiniMessage get() {
        return DEFAULT;
    }

    /**
     * Gets the strict MiniMessage instance (no lenient parsing).
     *
     * @return the strict instance
     */
    public static @NotNull MiniMessage strict() {
        return STRICT;
    }

    /**
     * Gets a MiniMessage instance with no built-in tags.
     *
     * @return an empty-tags instance
     */
    public static @NotNull MiniMessage emptyTags() {
        return EMPTY_TAGS;
    }

    /**
     * Extracts plain text from a component, ignoring formatting.
     *
     * @param component the component
     * @return the plain text content
     */
    public static @NotNull String toPlainText(@NotNull Component component) {
        StringBuilder sb = new StringBuilder();
        extractText(component, sb);
        return sb.toString();
    }

    private static void extractText(Component component, StringBuilder sb) {
        if (component instanceof TextComponent text) {
            sb.append(text.content());
        }
        for (Component child : component.children()) {
            extractText(child, sb);
        }
    }
}

