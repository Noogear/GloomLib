package gloomlib.command.resolver.resolvers;

import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import gloomlib.command.resolver.ArgumentResolver;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;

import org.bukkit.Material;

import java.lang.reflect.Parameter;
import java.util.concurrent.CompletableFuture;

/**
 * 物品材料参数解析器（使用 Paper API）。
 *
 * <p>
 * 使用 Paper 提供的 {@link ArgumentTypes#resource()} 或 key 参数类型。
 * </p>
 */
public class MaterialResolver implements ArgumentResolver<Material> {

    @Override
    public ArgumentType<?> createArgumentType(Parameter parameter) {
        // 使用 Paper 提供的资源键参数类型
        return ArgumentTypes.key();
    }

    @Override
    public Material resolve(CommandContext<CommandSourceStack> context, String name, Parameter parameter) {
        org.bukkit.NamespacedKey key = context.getArgument(name, org.bukkit.NamespacedKey.class);
        Material material = Material.matchMaterial(key.getKey());

        if (material == null) {
            throw new IllegalArgumentException("无效的材料: " + key.getKey());
        }

        return material;
    }

    @Override
    public CompletableFuture<Suggestions> suggest(
            CommandContext<CommandSourceStack> context,
            SuggestionsBuilder builder,
            Parameter parameter) {
        String remaining = builder.getRemaining().toLowerCase();

        for (Material material : Material.values()) {
            if (material.isItem() || material.isBlock()) {
                String name = material.getKey().getKey();
                if (name.startsWith(remaining) || name.contains(remaining)) {
                    builder.suggest("minecraft:" + name);
                }
            }
        }

        return builder.buildFuture();
    }

    @Override
    public Class<Material> getType() {
        return Material.class;
    }
}
