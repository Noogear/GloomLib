package gloomlib.command.exception;

import gloomlib.command.context.GloomCommandContext;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 异常解析器注册表。
 *
 * <p>
 * 管理全局异常处理器。
 * </p>
 */
public class ExceptionResolverRegistry {

    private final Map<Class<? extends Throwable>, ExceptionResolver<?>> resolvers = new ConcurrentHashMap<>();

    /**
     * 注册异常解析器。
     *
     * @param exceptionType 异常类型
     * @param resolver      解析器
     * @param <T>           异常类型
     */
    public <T extends Throwable> void register(Class<T> exceptionType, ExceptionResolver<T> resolver) {
        resolvers.put(exceptionType, resolver);
    }

    /**
     * 解析异常。
     *
     * @param context   命令上下文
     * @param exception 异常
     * @return 是否已处理
     */
    @SuppressWarnings("unchecked")
    public boolean resolve(GloomCommandContext context, Throwable exception) {
        Class<? extends Throwable> exceptionClass = exception.getClass();

        // 精确匹配
        ExceptionResolver<?> resolver = resolvers.get(exceptionClass);

        if (resolver == null) {
            // 继承匹配
            for (Map.Entry<Class<? extends Throwable>, ExceptionResolver<?>> entry : resolvers.entrySet()) {
                if (entry.getKey().isAssignableFrom(exceptionClass)) {
                    resolver = entry.getValue();
                    break;
                }
            }
        }

        if (resolver != null) {
            ((ExceptionResolver<Throwable>) resolver).resolve(context, exception);
            return true;
        }

        return false;
    }

    /**
     * 获取异常解析器。
     *
     * @param exceptionType 异常类型
     * @param <T>           异常类型
     * @return 解析器，或 null
     */
    @SuppressWarnings("unchecked")
    public <T extends Throwable> @Nullable ExceptionResolver<T> getResolver(Class<T> exceptionType) {
        return (ExceptionResolver<T>) resolvers.get(exceptionType);
    }

    /**
     * 注册默认解析器。
     */
    public void registerDefaults() {
        // CommandException 解析器
        register(CommandException.class, (ctx, ex) -> {
            ctx.getSender().sendMessage(ex.getAdventureMessage());
        });

        // IllegalArgumentException 解析器
        register(IllegalArgumentException.class, (ctx, ex) -> {
            ctx.getSender().sendMessage(
                    Component.text("参数错误: ", NamedTextColor.RED)
                            .append(Component.text(ex.getMessage(), NamedTextColor.YELLOW)));
        });

        // 通用异常解析器
        register(Exception.class, (ctx, ex) -> {
            ctx.getSender().sendMessage(
                    Component.text("命令执行出错: " + ex.getMessage(), NamedTextColor.RED));
            ex.printStackTrace();
        });
    }
}
