package gloomlib.translation.tag;

import gloomlib.translation.api.TranslationManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.Context;
import net.kyori.adventure.text.minimessage.ParsingException;
import net.kyori.adventure.text.minimessage.tag.Tag;
import net.kyori.adventure.text.minimessage.tag.resolver.ArgumentQueue;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MiniMessage tag resolver for inline translations using {@code <i18n:key>} syntax.
 *
 * <p>Supports arguments via {@code <i18n:key:arg0:arg1>} which map to {@code <arg:N>} in translations.</p>
 *
 * @since 1.0.0
 */
public final class I18NTag implements TagResolver {

    /**
     * Global singleton using {@link TranslationManager#instance()} and default locale.
     * Convenient for simple usage without locale customization.
     */
    public static final TagResolver INSTANCE = new GlobalI18NTag();

    private final TranslationManager translationManager;
    private final Locale viewerLocale;

    /**
     * Creates a new I18N tag resolver.
     *
     * @param translationManager the translation manager to use
     * @param viewerLocale the locale of the viewer, or null to use default
     */
    public I18NTag(@NotNull TranslationManager translationManager, @Nullable Locale viewerLocale) {
        this.translationManager = translationManager;
        this.viewerLocale = viewerLocale != null ? viewerLocale : translationManager.getDefaultLocale();
    }

    @Override
    public @Nullable Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx)
            throws ParsingException {
        if (!has(name)) {
            return null;
        }

        String key = arguments.popOr("The <i18n> tag requires a translation key").value();

        String translation = translationManager.getRawTranslation(key, viewerLocale);
        if (translation == null) {
            return Tag.selfClosingInserting(Component.text(key));
        }

        List<Component> args = new ArrayList<>();
        while (arguments.hasNext()) {
            args.add(Component.text(arguments.pop().value()));
        }

        if (args.isEmpty()) {
            return Tag.selfClosingInserting(ctx.deserialize(translation));
        } else {
            return Tag.selfClosingInserting(ctx.deserialize(translation, new IndexedArgumentTag(args)));
        }
    }

    @Override
    public boolean has(@NotNull String name) {
        return "i18n".equals(name);
    }

    /**
     * Creates a tag resolver for use with MiniMessage.
     *
     * @param translationManager the translation manager
     * @param locale the viewer's locale
     * @return a tag resolver for the "i18n" tag
     */
    public static TagResolver resolver(@NotNull TranslationManager translationManager, @Nullable Locale locale) {
        return new I18NTag(translationManager, locale);
    }

    /**
     * Internal singleton implementation using global TranslationManager.
     */
    private static final class GlobalI18NTag implements TagResolver {

        @Override
        public @Nullable Tag resolve(@NotNull String name, @NotNull ArgumentQueue arguments, @NotNull Context ctx)
                throws ParsingException {
            if (!has(name)) {
                return null;
            }

            String key = arguments.popOr("The <i18n> tag requires a translation key").value();

            TranslationManager manager = TranslationManager.instance();
            if (manager == null) {
                return Tag.selfClosingInserting(Component.text(key));
            }

            String translation = manager.getRawTranslation(key);
            if (translation == null) {
                return Tag.selfClosingInserting(Component.text(key));
            }

            List<Component> args = new ArrayList<>();
            while (arguments.hasNext()) {
                args.add(Component.text(arguments.pop().value()));
            }

            if (args.isEmpty()) {
                return Tag.selfClosingInserting(ctx.deserialize(translation));
            } else {
                return Tag.selfClosingInserting(ctx.deserialize(translation, new IndexedArgumentTag(args)));
            }
        }

        @Override
        public boolean has(@NotNull String name) {
            return "i18n".equals(name);
        }
    }
}
