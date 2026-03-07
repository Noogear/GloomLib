package gloomlib.command.resolver.registry;

import gloomlib.command.resolver.registry.RegistryTypesDiscovery.RegistryTypeInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for automatic Registry type discovery.
 */
class RegistryTypesDiscoveryTest {

    @Test
    void testDiscoverAll() {
        // When: Discover all registry types
        List<RegistryTypeInfo> types = RegistryTypesDiscovery.discoverAll();

        // Then: Should discover multiple types
        assertNotNull(types);
        assertFalse(types.isEmpty(), "Should discover at least one type");
        assertTrue(types.size() >= 6, 
            "Should discover at least 6 common types (ENCHANTMENT, ENTITY_TYPE, BIOME, STRUCTURE, MOB_EFFECT, ATTRIBUTE)");
    }

    @Test
    void testDiscoveredTypesHaveValidInfo() {
        // When: Discover all types
        List<RegistryTypeInfo> types = RegistryTypesDiscovery.discoverAll();

        // Then: Each type should have valid info
        for (RegistryTypeInfo info : types) {
            assertNotNull(info.fieldName(), "Field name should not be null");
            assertNotNull(info.targetType(), "Target type should not be null");
            assertNotNull(info.registryKey(), "Registry key should not be null");
            
            assertFalse(info.fieldName().isEmpty(), "Field name should not be empty");
            assertTrue(org.bukkit.Keyed.class.isAssignableFrom(info.targetType()),
                "Target type should implement Keyed: " + info.targetType().getName());
        }
    }

    @Test
    void testCommonTypesAreDiscovered() {
        // Given: Expected common types
        String[] expectedFields = {
            "ENCHANTMENT",
            "ENTITY_TYPE",
            "BIOME",
            "STRUCTURE",
            "MOB_EFFECT",
            "ATTRIBUTE"
        };

        // When: Discover all types
        List<RegistryTypeInfo> types = RegistryTypesDiscovery.discoverAll();
        List<String> fieldNames = types.stream()
            .map(RegistryTypeInfo::fieldName)
            .toList();

        // Then: All common types should be present
        for (String expected : expectedFields) {
            assertTrue(fieldNames.contains(expected),
                "Should discover " + expected + " type");
        }
    }

    @Test
    void testDiscoveryIsCached() {
        // When: Call discoverAll twice
        List<RegistryTypeInfo> first = RegistryTypesDiscovery.discoverAll();
        List<RegistryTypeInfo> second = RegistryTypesDiscovery.discoverAll();

        // Then: Should return the same instance (cached)
        assertSame(first, second, "Discovery results should be cached");
    }

    @Test
    void testPrintDiscoveryReport() {
        // When: Print discovery report
        // Then: Should not throw exception
        assertDoesNotThrow(() -> RegistryTypesDiscovery.printDiscoveryReport());
    }
}
