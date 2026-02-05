package gloomlib.command.internal;

import gloomlib.command.annotation.Command;

/**
 * Extracts command metadata from annotated instances.
 *
 * <p>
 * Minimal utility - only provides essential metadata extraction without unnecessary abstraction.
 * </p>
 */
public final class CommandMetadata {

    private CommandMetadata() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Extracts command name from instance.
     *
     * @param instance Command instance
     * @return Command name, or null if not found
     */
    public static String getName(Object instance) {
        Command cmd = instance.getClass().getAnnotation(Command.class);
        return cmd != null ? cmd.value() : null;
    }

    /**
     * Extracts command aliases from instance.
     *
     * @param instance Command instance
     * @return Aliases array, or empty array if none
     */
    public static String[] getAliases(Object instance) {
        Command cmd = instance.getClass().getAnnotation(Command.class);
        return cmd != null ? cmd.aliases() : new String[0];
    }
}
