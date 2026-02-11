package gloomlib.command.resolver.registry;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import gloomlib.command.annotation.Greedy;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import org.jetbrains.annotations.NotNull;

/**
 * Built-in argument resolver registration.
 */
public final class BuiltInResolvers {

        private BuiltInResolvers() {
                throw new UnsupportedOperationException("Utility class");
        }

        /**
         * Registers all built-in resolvers.
         *
         * @param registry Registry to register into
         */
        public static void registerAll(@NotNull ArgumentResolverRegistry registry) {
                registerBasicTypes(registry);
                AutoRegistrar.registerAll(registry);
        }

        private static void registerBasicTypes(@NotNull ArgumentResolverRegistry registry) {
                registry.register(Boolean.class,
                                BrigadierResolver.of(Boolean.class, BoolArgumentType::bool));

                registry.register(Integer.class,
                                BrigadierResolver.of(Integer.class, NumericResolvers::intArgument));

                registry.register(Long.class,
                                BrigadierResolver.of(Long.class, NumericResolvers::longArgument));

                registry.register(Float.class,
                                BrigadierResolver.of(Float.class, NumericResolvers::floatArgument));

                registry.register(Double.class,
                                BrigadierResolver.of(Double.class, NumericResolvers::doubleArgument));

                NumericResolvers.registerByteShort(registry);

                registry.register(String.class,
                                BrigadierResolver.of(String.class, param -> {
                                        Greedy greedy = param.getAnnotation(Greedy.class);
                                        return greedy != null
                                                        ? StringArgumentType.greedyString()
                                                        : StringArgumentType.string();
                                }));
        }
}
