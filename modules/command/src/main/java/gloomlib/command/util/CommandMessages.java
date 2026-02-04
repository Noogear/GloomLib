package gloomlib.command.util;

/**
 * 命令错误消息常量。
 *
 * <p>
 * 集中管理所有命令相关的错误消息模板。
 * </p>
 */
public final class CommandMessages {

    public static final String MSG_REQUIRE_ANNOTATION = "Class %s must have @Command annotation";
    public static final String MSG_UNSUPPORTED_TYPE = "Unsupported parameter type: %s (param: %s, method: %s)";
    public static final String MSG_RESOLVER_NOT_FOUND = "Argument resolver not found: %s";
    public static final String MSG_PROVIDER_INIT_ERROR = "Could not instantiate suggestion provider: %s";

    private CommandMessages() {
        throw new UnsupportedOperationException("Utility class");
    }
}
