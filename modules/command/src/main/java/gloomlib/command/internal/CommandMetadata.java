package gloomlib.command.internal;

import gloomlib.command.annotation.Command;

/**
 * Command metadata extraction utility.
 */
public final class CommandMetadata {

    private CommandMetadata() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static String getName(Object instance) {
        Command cmd = instance.getClass().getAnnotation(Command.class);
        return cmd != null ? cmd.value() : null;
    }

    public static String[] getAliases(Object instance) {
        Command cmd = instance.getClass().getAnnotation(Command.class);
        return cmd != null ? cmd.aliases() : new String[0];
    }
}
