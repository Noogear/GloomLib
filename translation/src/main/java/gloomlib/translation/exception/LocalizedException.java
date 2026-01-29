package gloomlib.translation.exception;

import gloomlib.translation.api.TranslationManager;
import gloomlib.translation.util.MiniMessages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;

/**
 * Exception with localized message resolved from translation key and arguments.
 *
 * <p>Strips MiniMessage tags in {@link #getMessage()} for plain text logging.</p>
 *
 * @since 1.0.0
 */
public class LocalizedException extends RuntimeException {

    /** The translation key for error message. */
    private final String node;
    /** Arguments for message formatting. */
    private String[] arguments;

    /**
     * Creates a new localized exception.
     *
     * @param node the translation key
     * @param cause the cause of this exception, or null
     * @param arguments the arguments to substitute into the translation
     */
    public LocalizedException(
            @NotNull String node,
            @Nullable Exception cause,
            @Nullable String... arguments
    ) {
        super(node, cause);
        this.node = node;
        this.arguments = arguments != null
                ? Arrays.copyOf(arguments, arguments.length)
                : new String[0];
    }

    /**
     * Creates a new localized exception without a cause.
     *
     * @param node the translation key
     * @param arguments the arguments to substitute into the translation
     */
    public LocalizedException(@NotNull String node, @Nullable String... arguments) {
        this(node, (Exception) null, arguments);
    }

    /**
     * Gets the translation key (node).
     *
     * @return the translation key
     */
    public @NotNull String node() {
        return node;
    }

    /**
     * Gets a copy of the arguments array.
     *
     * @return the arguments
     */
    public String[] arguments() {
        return Arrays.copyOf(arguments, arguments.length);
    }

    /**
     * Sets an argument at the specified index.
     *
     * @param index the argument index
     * @param argument the argument value
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void setArgument(int index, @NotNull String argument) {
        if (index < 0 || index >= arguments.length) {
            throw new IndexOutOfBoundsException("Invalid argument index: " + index);
        }
        this.arguments[index] = argument;
    }

    /**
     * Prepends an argument to the beginning of the arguments array.
     *
     * @param argument the argument to prepend
     */
    public void prependArgument(@NotNull String argument) {
        String[] newArgs = new String[arguments.length + 1];
        newArgs[0] = argument;
        System.arraycopy(arguments, 0, newArgs, 1, arguments.length);
        this.arguments = newArgs;
    }

    /**
     * Appends an argument to the end of the arguments array.
     *
     * @param argument the argument to append
     */
    public void appendArgument(@NotNull String argument) {
        String[] newArgs = Arrays.copyOf(arguments, arguments.length + 1);
        newArgs[arguments.length] = argument;
        this.arguments = newArgs;
    }

    @Override
    public String getMessage() {
        return generateLocalizedMessage();
    }

    private String generateLocalizedMessage() {
        try {
            TranslationManager manager = TranslationManager.instance();
            String rawMessage = manager != null
                    ? manager.getRawTranslation(this.node)
                    : null;

            if (rawMessage == null || rawMessage.isEmpty()) {
                rawMessage = this.node;
            }

            String cleanMessage = MiniMessages.get().stripTags(rawMessage);

            for (int i = 0; i < arguments.length; i++) {
                cleanMessage = cleanMessage.replace(
                        "<arg:" + i + ">",
                        arguments[i] != null ? arguments[i] : "null"
                );
            }

            return cleanMessage;
        } catch (Exception e) {
            return String.format(
                    "Failed to translate. Node: %s, Arguments: %s. Cause: %s",
                    node,
                    Arrays.toString(arguments),
                    e.getMessage()
            );
        }
    }
}
