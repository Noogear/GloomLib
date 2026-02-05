package gloomlib.command.resolver.registry;

import gloomlib.command.GloomCommand;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Keyed;
import org.jetbrains.annotations.NotNull;

/**
 * Registry type resolver utilities.
 *
 * @implNote Delegates to {@link RegistryTypesDiscovery} for type discovery.
 */
public final class RegistryResolvers {

    private static final ComponentLogger LOGGER = ComponentLogger.logger(RegistryResolvers.class);
    private static final String MSG_REGISTERED = "Registered: {} → {}";
    private static final String MSG_REGISTERED_KEY = "Registered ResourceKey: {}";

    private RegistryResolvers() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void registerAll(@NotNull GloomCommand gloom) {
        RegistryTypesDiscovery.registerAllDiscovered(gloom);
    }

    public static <T extends Keyed> void register(
            @NotNull GloomCommand gloom,
            @NotNull Class<T> type,
            @NotNull RegistryKey<T> registryKey) {

        gloom.registerArgumentResolver(
                type,
                BrigadierResolver.of(type, () -> ArgumentTypes.resource(registryKey))
        );

        LOGGER.debug(MSG_REGISTERED, type.getSimpleName(), registryKey.key());
    }

    public static <T extends Keyed> void registerResourceKey(
            @NotNull GloomCommand gloom,
            @NotNull RegistryKey<T> registryKey) {

        gloom.registerArgumentResolver(
                org.bukkit.NamespacedKey.class,
                BrigadierResolver.of(
                        org.bukkit.NamespacedKey.class,
                        () -> ArgumentTypes.resourceKey(registryKey)
                )
        );

        LOGGER.debug(MSG_REGISTERED_KEY, registryKey.key());
    }
}
