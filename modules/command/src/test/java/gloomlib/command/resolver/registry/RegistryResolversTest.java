package gloomlib.command.resolver.registry;

import gloomlib.command.GloomCommand;
import gloomlib.command.annotation.Arg;
import gloomlib.command.annotation.Command;
import gloomlib.command.annotation.Range;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Biome;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.generator.structure.Structure;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Paper registry-based argument resolvers.
 */
class RegistryResolversTest {

    @Test
    void testRegistryKeysExist() {
        // Verify that the RegistryKeys we use actually exist
        assertNotNull(io.papermc.paper.registry.RegistryKey.ENCHANTMENT);
        assertNotNull(io.papermc.paper.registry.RegistryKey.ENTITY_TYPE);
        assertNotNull(io.papermc.paper.registry.RegistryKey.BIOME);
        assertNotNull(io.papermc.paper.registry.RegistryKey.STRUCTURE);
        assertNotNull(io.papermc.paper.registry.RegistryKey.MOB_EFFECT);
        assertNotNull(io.papermc.paper.registry.RegistryKey.ATTRIBUTE);
    }
}
