package gloomlib.command.builder;

import gloomlib.command.annotation.*;
import gloomlib.command.resolver.ArgumentResolver;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import gloomlib.command.suggestion.SuggestionProvider;
import gloomlib.command.util.CommandMessages;
import gloomlib.command.util.ParameterUtils;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Brigadier 命令树构建器。
 *
 * <p>
 * 负责构建 Brigadier 命令树，包括：
 * </p>
 * <ul>
 * <li>方法分支构建</li>
 * <li>参数链构建</li>
 * <li>可选参数处理</li>
 * <li>建议提供者注册</li>
 * <li>命令执行器绑定</li>
 * </ul>
 */
public class BrigadierTreeBuilder {

    private final ArgumentResolverRegistry resolverRegistry;
    private final Map<Class<? extends SuggestionProvider>, SuggestionProvider> suggestionCache = new HashMap<>();

    /**
     * 命令执行函数类型。
     */
    @FunctionalInterface
    public interface ExecutionFunction {
        int execute(CommandContext<CommandSourceStack> ctx) throws Exception;
    }

    public BrigadierTreeBuilder(ArgumentResolverRegistry resolverRegistry) {
        this.resolverRegistry = resolverRegistry;
    }

    /**
     * 为命令方法构建 Brigadier 分支。
     *
     * @param builder         命令构建器
     * @param method          命令方法
     * @param executionFn     执行函数
     */
    public void buildMethodBranch(
            LiteralArgumentBuilder<CommandSourceStack> builder,
            Method method,
            ExecutionFunction executionFn) {
        
        Parameter[] parameters = method.getParameters();
        int startIndex = ParameterUtils.getStartParameterIndex(parameters);

        if (startIndex >= parameters.length) {
            // 无参数命令
            builder.executes(ctx -> {
                try {
                    return executionFn.execute(ctx);
                } catch (Exception e) {
                    e.printStackTrace();
                    return 0;
                }
            });
        } else {
            // 有参数命令，构建参数链
            buildArgumentChain(builder, method, parameters, startIndex, executionFn);
        }
    }

    /**
     * 递归构建参数链。
     *
     * @param builder     命令构建器
     * @param method      命令方法
     * @param parameters  方法参数数组
     * @param paramIndex  当前参数索引
     * @param executionFn 执行函数
     */
    private void buildArgumentChain(
            ArgumentBuilder<CommandSourceStack, ?> builder,
            Method method,
            Parameter[] parameters,
            int paramIndex,
            ExecutionFunction executionFn) {
        
        // 递归终止条件
        if (paramIndex >= parameters.length) {
            builder.executes(ctx -> {
                try {
                    return executionFn.execute(ctx);
                } catch (Exception e) {
                    e.printStackTrace();
                    return 0;
                }
            });
            return;
        }

        Parameter param = parameters[paramIndex];

        // 跳过 Flag 和 Switch 注解的参数（不需要从命令行解析）
        if (param.isAnnotationPresent(Flag.class) || param.isAnnotationPresent(Switch.class)) {
            buildArgumentChain(builder, method, parameters, paramIndex + 1, executionFn);
            return;
        }

        // 构建参数节点
        String argName = ParameterUtils.getParameterName(param);
        ArgumentResolver<?> resolver = resolverRegistry.getResolver(param.getType());
        
        if (resolver == null) {
            throw new IllegalArgumentException(String.format(
                CommandMessages.MSG_UNSUPPORTED_TYPE, 
                param.getType().getName(), 
                argName, 
                method.getName()));
        }

        RequiredArgumentBuilder<CommandSourceStack, ?> argumentBuilder = 
            Commands.argument(argName, resolver.createArgumentType(param));

        // 注册建议提供者
        registerSuggestions(argumentBuilder, param, resolver);

        // 如果是可选参数，在此处添加执行点
        if (param.isAnnotationPresent(Optional.class)) {
            builder.executes(ctx -> {
                try {
                    return executionFn.execute(ctx);
                } catch (Exception e) {
                    e.printStackTrace();
                    return 0;
                }
            });
        }

        // 递归构建下一个参数
        buildArgumentChain(argumentBuilder, method, parameters, paramIndex + 1, executionFn);
        
        // 将参数节点挂载到当前节点
        builder.then(argumentBuilder);
    }

    /**
     * 注册参数的建议提供者。
     *
     * @param argumentBuilder 参数构建器
     * @param param           参数定义
     * @param resolver        参数解析器
     */
    private void registerSuggestions(
            RequiredArgumentBuilder<CommandSourceStack, ?> argumentBuilder,
            Parameter param,
            ArgumentResolver<?> resolver) {
        
        // 检查是否有 @Suggest 注解
        Suggest suggestAnnotation = param.getAnnotation(Suggest.class);
        if (suggestAnnotation != null) {
            SuggestionProvider provider = getSuggestionProvider(suggestAnnotation.value());
            argumentBuilder.suggests((ctx, suggestionsBuilder) -> 
                provider.suggest(ctx, suggestionsBuilder));
        } else {
            // 使用 resolver 的默认建议
            argumentBuilder.suggests((ctx, suggestionsBuilder) -> 
                resolver.suggest(ctx, suggestionsBuilder, param));
        }
    }

    /**
     * 获取或创建建议提供者实例。
     *
     * @param providerClass 提供者类
     * @return 提供者实例
     */
    private SuggestionProvider getSuggestionProvider(Class<? extends SuggestionProvider> providerClass) {
        return suggestionCache.computeIfAbsent(providerClass, clazz -> {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException(
                    String.format(CommandMessages.MSG_PROVIDER_INIT_ERROR, clazz.getName()), 
                    e);
            }
        });
    }
}
