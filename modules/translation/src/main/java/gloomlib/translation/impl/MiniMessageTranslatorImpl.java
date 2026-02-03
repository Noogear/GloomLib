package gloomlib.translation.impl;

import gloomlib.translation.api.MiniMessageTranslator;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.renderer.TranslatableComponentRenderer;
import net.kyori.adventure.translation.Translator;
import net.kyori.adventure.util.TriState;
import net.kyori.examination.ExaminableProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Implementation of {@link MiniMessageTranslator}.
 */
public final class MiniMessageTranslatorImpl implements MiniMessageTranslator {

    /**
     * Singleton instance.
     */
    public static final MiniMessageTranslatorImpl INSTANCE = new MiniMessageTranslatorImpl();
    private static final Key NAME = Key.key("gloomlib", "translator");
    /**
     * Translatable component renderer for locale-based translation.
     */
    public final TranslatableComponentRenderer<Locale> renderer =
            TranslatableComponentRenderer.usingTranslationSource(this);

    private volatile Translator source;

    private MiniMessageTranslatorImpl() {
    }

    @Override
    public @NotNull Key name() {
        return NAME;
    }

    @Override
    public @NotNull TriState hasAnyTranslations() {
        if (this.source != null) {
            return TriState.TRUE;
        }
        return TriState.FALSE;
    }

    @Override
    public @Nullable MessageFormat translate(@NotNull String key, @NotNull Locale locale) {
        return null;
    }

    @Override
    public @Nullable Component translate(@NotNull TranslatableComponent component, @NotNull Locale locale) {
        Translator currentSource = this.source;
        if (currentSource != null) {
            return currentSource.translate(component, locale);
        }
        return null;
    }

    @Override
    public boolean setSource(@NotNull Translator source) {
        this.source = source;
        return true;
    }

    @Override
    public @NotNull Stream<? extends ExaminableProperty> examinableProperties() {
        return Stream.of(ExaminableProperty.of("source", this.source));
    }
}
