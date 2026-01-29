package gloomlib.translation.api;

import gloomlib.translation.impl.MiniMessageTranslatorImpl;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import net.kyori.adventure.translation.Translator;
import net.kyori.examination.Examinable;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * Translator that renders TranslatableComponents using MiniMessage format strings.
 *
 * <p>Provides singleton access for rendering with locale-specific translations.</p>
 *
 * @since 1.0.0
 */
public interface MiniMessageTranslator extends Translator, Examinable {

    /**
     * Gets the singleton translator instance.
     *
     * @return the translator instance
     */
    static @NotNull MiniMessageTranslator translator() {
        return MiniMessageTranslatorImpl.INSTANCE;
    }

    /**
     * Gets the component renderer used by this translator.
     *
     * @return the translatable component renderer
     */
    static @NotNull TranslatableComponentRenderer<Locale> renderer() {
        return MiniMessageTranslatorImpl.INSTANCE.renderer;
    }

    /**
     * Renders a component, translating any {@link net.kyori.adventure.text.TranslatableComponent}s
     * within it using the given locale.
     *
     * @param component the component to render
     * @param locale the locale to use for translation
     * @return the rendered component with translations applied
     */
    static @NotNull Component render(final @NotNull Component component, final @NotNull Locale locale) {
        return renderer().render(component, locale);
    }

    /**
     * Sets the translation source for this translator.
     *
     * @param source the translator to use as the source
     * @return true if the source was set successfully
     */
    boolean setSource(final @NotNull Translator source);
}
