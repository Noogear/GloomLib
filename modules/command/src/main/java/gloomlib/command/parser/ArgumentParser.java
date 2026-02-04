package gloomlib.command.parser;

import gloomlib.command.annotation.*;
import gloomlib.command.context.AsyncContext;
import gloomlib.command.context.GloomCommandContext;
import gloomlib.command.exception.CommandException;
import gloomlib.command.processor.processors.ValidationProcessor;
import gloomlib.command.resolver.ArgumentResolver;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import gloomlib.command.util.CommandMessages;
import gloomlib.command.util.ParameterUtils;
import gloomlib.command.util.TypeConverterUtil;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Parameter;

/**
 * 命令参数解析器。
 *
 * <p>
 * 负责从 Brigadier CommandContext 中解析参数，支持：
 * </p>
 * <ul>
 * <li>特殊参数注入（CommandSender, Player, GloomCommandContext, AsyncContext）</li>
 * <li>自定义参数解析器</li>
 * <li>默认值处理</li>
 * <li>可选参数</li>
 * <li>开关和标志</li>
 * <li>范围验证</li>
 * </ul>
 */
public class ArgumentParser {

    private final JavaPlugin plugin;
    private final ArgumentResolverRegistry resolverRegistry;
    private final ValidationProcessor validationProcessor;

    public ArgumentParser(JavaPlugin plugin, ArgumentResolverRegistry resolverRegistry) {
        this.plugin = plugin;
        this.resolverRegistry = resolverRegistry;
        this.validationProcessor = new ValidationProcessor();
    }

    /**
     * 解析方法的所有参数。
     *
     * @param ctx        Brigadier CommandContext
     * @param parameters 方法参数数组
     * @param sender     命令发送者
     * @return 解析后的参数值数组
     * @throws CommandException 解析失败或验证失败时抛出
     */
    public Object[] resolveArguments(
            CommandContext<CommandSourceStack> ctx,
            Parameter[] parameters,
            CommandSender sender) throws CommandException {
        
        Object[] args = new Object[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            args[i] = resolveParameter(ctx, param, sender, i);
        }
        
        return args;
    }

    /**
     * 解析单个参数。
     *
     * @param ctx    Brigadier CommandContext
     * @param param  参数定义
     * @param sender 命令发送者
     * @param index  参数索引
     * @return 解析后的参数值
     * @throws CommandException 解析失败或验证失败时抛出
     */
    private Object resolveParameter(
            CommandContext<CommandSourceStack> ctx,
            Parameter param,
            CommandSender sender,
            int index) throws CommandException {
        
        Class<?> paramType = param.getType();
        
        // 1. 特殊参数 - 自动注入
        if (CommandSender.class.isAssignableFrom(paramType)) {
            return sender;
        }
        if (Player.class.equals(paramType) && index == 0) {
            return sender;
        }
        if (AsyncContext.class.isAssignableFrom(paramType)) {
            return new AsyncContext(ctx, plugin);
        }
        if (GloomCommandContext.class.isAssignableFrom(paramType)) {
            return new GloomCommandContext(ctx);
        }
        
        // 2. 开关参数 - 默认为 false
        if (param.isAnnotationPresent(Switch.class)) {
            return false;
        }
        
        // 3. 标志参数 - 默认为 null
        if (param.isAnnotationPresent(Flag.class)) {
            return null;
        }
        
        // 4. 自定义参数解析
        return resolveCustomParameter(ctx, param, sender);
    }

    /**
     * 解析自定义参数（使用 ArgumentResolver）。
     *
     * @param ctx    Brigadier CommandContext
     * @param param  参数定义
     * @param sender 命令发送者
     * @return 解析后的参数值
     * @throws CommandException 解析失败或验证失败时抛出
     */
    private Object resolveCustomParameter(
            CommandContext<CommandSourceStack> ctx,
            Parameter param,
            CommandSender sender) throws CommandException {
        
        Class<?> paramType = param.getType();
        String argName = ParameterUtils.getParameterName(param);
        
        try {
            ArgumentResolver<?> resolver = resolverRegistry.getResolver(paramType);
            if (resolver == null) {
                throw new IllegalArgumentException(
                    String.format(CommandMessages.MSG_RESOLVER_NOT_FOUND, paramType.getName()));
            }
            
            Object resolvedValue = resolver.resolve(ctx, argName, param);
            
            // 数值范围验证
            if (resolvedValue instanceof Number numberValue) {
                validateRange(numberValue, param);
            }
            
            return resolvedValue;
            
        } catch (Exception e) {
            // 尝试使用默认值
            return resolveWithDefault(e, param, sender);
        }
    }

    /**
     * 当解析失败时，尝试使用默认值。
     *
     * @param originalException 原始异常
     * @param param             参数定义
     * @param sender            命令发送者
     * @return 默认值，或在无默认值时重新抛出异常
     * @throws CommandException 无默认值且参数非可选时抛出
     */
    private Object resolveWithDefault(
            Exception originalException,
            Parameter param,
            CommandSender sender) throws CommandException {
        
        // 检查是否有 @Default 注解
        Default defaultAnnotation = param.getAnnotation(Default.class);
        if (defaultAnnotation != null) {
            return TypeConverterUtil.convertDefault(
                defaultAnnotation.value(), 
                param.getType(), 
                sender
            ).orElse(null);
        }
        
        // 检查是否有 @Optional 注解
        if (param.isAnnotationPresent(Optional.class)) {
            return null;
        }
        
        // 无默认值且非可选，重新抛出异常
        if (originalException instanceof CommandException cmdEx) {
            throw cmdEx;
        }
        throw new CommandException(originalException.getMessage());
    }

    /**
     * 验证数值范围。
     *
     * @param value 数值
     * @param param 参数定义
     * @throws CommandException 验证失败时抛出
     */
    private void validateRange(Number value, Parameter param) throws CommandException {
        if (!param.isAnnotationPresent(Range.class)) {
            return;
        }
        
        Range range = param.getAnnotation(Range.class);
        ValidationProcessor.ValidationResult result = 
            validationProcessor.validateRange(value, range, param);
        
        if (!result.isValid()) {
            throw new CommandException(result.getErrorMessage());
        }
    }
}
