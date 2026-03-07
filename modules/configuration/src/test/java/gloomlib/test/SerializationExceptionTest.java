package gloomlib.test;

import com.google.gson.reflect.TypeToken;
import gloomlib.configuration.api.ConfigurationManager;
import gloomlib.configuration.api.TypeSerializer;
import gloomlib.configuration.api.exception.SerializationException;
import gloomlib.configuration.api.exception.LoadContext;
import gloomlib.configuration.core.util.TypeInference;
import gloomlib.configuration.core.util.YamlLineIndex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SerializationException diagnostic output and TypeToken integration.
 */
@DisplayName("SerializationException — diagnostic output + TypeToken")
public class SerializationExceptionTest {

    // ─── typeMismatch ────────────────────────────────────────────────────────

    @Test
    @DisplayName("typeMismatch: int field receives string — TYPE category with snippet")
    void testTypeMismatch() {
        SerializationException ex = SerializationException.typeMismatch(
                List.of("classes", "warrior", "health"),
                Integer.class,
                "abc"
        );

        System.out.println("=== typeMismatch ===");
        System.out.println(ex.getMessage());

        assertTrue(ex.getMessage().contains("[Type]"));
        assertTrue(ex.getMessage().contains("classes.warrior.health"));
        assertTrue(ex.getMessage().contains("Integer"));
        assertTrue(ex.getMessage().contains("abc"));
        assertEquals(List.of("classes", "warrior", "health"), ex.getNodePath());
        assertEquals("classes.warrior.health", ex.getPathString());
        assertEquals(Integer.class, ex.getExpectedType());
        assertEquals("abc", ex.getActualValue());
        assertNull(ex.getCause());
    }

    @Test
    @DisplayName("typeMismatch: nested list index path — path shown correctly")
    void testTypeMismatchInList() {
        SerializationException ex = SerializationException.typeMismatch(
                List.of("rewards", "[2]", "amount"),
                Double.class,
                "not-a-number"
        );

        System.out.println("=== typeMismatch (list path) ===");
        System.out.println(ex.getMessage());

        assertTrue(ex.getMessage().contains("rewards.[2].amount"));
        assertTrue(ex.getMessage().contains("Double"));
    }

    // ─── missing ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("missing: required field absent — SEMANTIC category, no snippet")
    void testMissing() {
        SerializationException ex = SerializationException.missing(
                List.of("database"),
                "host"
        );

        System.out.println("=== missing ===");
        System.out.println(ex.getMessage());

        assertTrue(ex.getMessage().contains("[Semantic]"));
        assertTrue(ex.getMessage().contains("database.host"));
        assertTrue(ex.getMessage().contains("Missing required field: host"));
        assertEquals(List.of("database", "host"), ex.getNodePath());
        assertNull(ex.getExpectedType());
        assertNull(ex.getActualValue());
    }

    @Test
    @DisplayName("missing: empty parent path — uses field name as full path")
    void testMissingAtRoot() {
        SerializationException ex = SerializationException.missing(List.of(), "version");

        System.out.println("=== missing (root) ===");
        System.out.println(ex.getMessage());

        assertEquals(List.of("version"), ex.getNodePath());
        assertTrue(ex.getMessage().contains("version"));
    }

    // ─── wrap ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("wrap: cause message propagated, snippet shown — CONFIG category")
    void testWrap() {
        NumberFormatException cause = new NumberFormatException("For input string: \"abc\"");
        SerializationException ex = SerializationException.wrap(
                List.of("server", "port"),
                Integer.class,
                "abc",
                cause
        );

        System.out.println("=== wrap ===");
        System.out.println(ex.getMessage());

        assertTrue(ex.getMessage().contains("[Config]"));
        assertTrue(ex.getMessage().contains("server.port"));
        // cause message is used as the main message
        assertTrue(ex.getMessage().contains("abc"));
        assertSame(cause, ex.getCause());
        assertEquals(Integer.class, ex.getExpectedType());
    }

    @Test
    @DisplayName("wrap: null cause — fallback message 'Deserialization failed'")
    void testWrapNoCause() {
        SerializationException ex = SerializationException.wrap(
                List.of("data", "value"), Double.class, "bad", null
        );

        System.out.println("=== wrap (no cause) ===");
        System.out.println(ex.getMessage());

        assertTrue(ex.getMessage().contains("Deserialization failed"));
        assertNull(ex.getCause());
    }

    // ─── validation ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("validation: @Check fails — SEMANTIC category, custom reason")
    void testValidation() {
        SerializationException ex = SerializationException.validation(
                List.of("settings", "maxPlayers"),
                "Validation failed for 'maxPlayers': value -5 must be positive"
        );

        System.out.println("=== validation ===");
        System.out.println(ex.getMessage());

        assertTrue(ex.getMessage().contains("[Semantic]"));
        assertTrue(ex.getMessage().contains("settings.maxPlayers"));
        assertTrue(ex.getMessage().contains("must be positive"));
        assertEquals(List.of("settings", "maxPlayers"), ex.getNodePath());
        assertNull(ex.getExpectedType());
        assertNull(ex.getCause());
    }

    // ─── edge cases ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getPathString: empty path returns '<root>'")
    void testPathStringRoot() {
        SerializationException ex = SerializationException.validation(List.of(), "root error");
        assertEquals("<root>", ex.getPathString());

        System.out.println("=== validation (root, no path) ===");
        System.out.println(ex.getMessage());
    }

    // ─── LoadContext: file name + line numbers ────────────────────────────────

    @Test
    @DisplayName("With LoadContext: output shows filename:line (dotpath)")
    void testLoadContextEnrichesLocation() throws Exception {
        // Write a temp YAML file with known structure
        java.io.File tmp = java.io.File.createTempFile("test-config", ".yml");
        tmp.deleteOnExit();
        java.nio.file.Files.writeString(tmp.toPath(), """
                database:
                  host: localhost
                  port: not-a-number
                  pool:
                    size: bad
                """);

        // Simulate what ConfigurationLoader does before deserialization
        LoadContext.set(tmp.getName(), YamlLineIndex.build(tmp));
        try {
            SerializationException ex1 = SerializationException.typeMismatch(
                    List.of("database", "port"), Integer.class, "not-a-number");
            SerializationException ex2 = SerializationException.typeMismatch(
                    List.of("database", "pool", "size"), Integer.class, "bad");
            SerializationException ex3 = SerializationException.missing(
                    List.of("database"), "password");

            System.out.println("=== with LoadContext (file:line) ===");
            System.out.println(ex1.getMessage());
            System.out.println();
            System.out.println(ex2.getMessage());
            System.out.println();
            System.out.println(ex3.getMessage());

            // All should include the temp filename
            assertTrue(ex1.getMessage().contains(tmp.getName()),
                    "Should contain filename, got: " + ex1.getMessage());
            assertTrue(ex2.getMessage().contains(tmp.getName()));
            assertTrue(ex3.getMessage().contains(tmp.getName()));

            // Line-enriched ones should have line numbers (database.port is on line 3)
            assertTrue(ex1.getMessage().contains(":3"),
                    "database.port should be on line 3, got: " + ex1.getMessage());
        } finally {
            LoadContext.clear();
        }
    }

    @Test
    @DisplayName("Without LoadContext: falls back to dotpath-only location")
    void testWithoutLoadContextFallback() {
        // No LoadContext.set() — factory method runs in isolation
        SerializationException ex = SerializationException.typeMismatch(
                List.of("server", "port"), Integer.class, "invalid");

        System.out.println("=== without LoadContext (fallback dotpath) ===");
        System.out.println(ex.getMessage());

        // Should only contain the dotpath, not any filename
        assertTrue(ex.getMessage().contains("server.port"));
        assertFalse(ex.getMessage().contains(".yml"),
                "No filename expected without LoadContext");
    }

    @Test
    @DisplayName("TypeInference should extract generic parameters from TypeToken")
    void testTypeTokenGenericExtraction() {
        // Test simple generic type
        TypeToken<List<String>> listToken = new TypeToken<List<String>>() {
        };
        Class<?> elementType = TypeInference.extractGenericParameter(listToken.getType(), 0);
        assertEquals(String.class, elementType);

        // Test map with two generic parameters
        TypeToken<Map<String, Integer>> mapToken = new TypeToken<Map<String, Integer>>() {
        };
        Class<?> keyType = TypeInference.extractGenericParameter(mapToken.getType(), 0);
        Class<?> valueType = TypeInference.extractGenericParameter(mapToken.getType(), 1);
        assertEquals(String.class, keyType);
        assertEquals(Integer.class, valueType);

        // Test complex nested generics
        TypeToken<Map<UUID, List<Integer>>> complexToken = new TypeToken<Map<UUID, List<Integer>>>() {
        };
        Class<?> complexKeyType = TypeInference.extractGenericParameter(complexToken.getType(), 0);
        Class<?> complexValueType = TypeInference.extractGenericParameter(complexToken.getType(), 1);
        assertEquals(UUID.class, complexKeyType);
        assertEquals(List.class, complexValueType);
    }

    @Test
    @DisplayName("TypeInference should get raw type from TypeToken")
    void testTypeTokenRawType() {
        TypeToken<List<String>> token = new TypeToken<List<String>>() {
        };
        Class<?> rawType = token.getRawType();
        assertEquals(List.class, rawType);

        TypeToken<Map<UUID, Integer>> mapToken = new TypeToken<Map<UUID, Integer>>() {
        };
        Class<?> mapRawType = mapToken.getRawType();
        assertEquals(Map.class, mapRawType);
    }

    @Test
    @DisplayName("ConfigurationManager should support TypeSerializer registration")
    void testTypeSerializerRegistration() {
        TypeToken<List<UUID>> token = new TypeToken<List<UUID>>() {
        };

        // Register a custom serializer
        ConfigurationManager.registerTypeSerializer(token, new TypeSerializer<List<UUID>>() {
            @Override
            public Object serialize(List<UUID> value, java.lang.reflect.Type genericType) {
                return value.stream().map(UUID::toString).toList();
            }

            @Override
            public List<UUID> deserialize(Object yamlValue, java.lang.reflect.Type genericType) throws SerializationException {
                if (yamlValue instanceof List<?> list) {
                    return list.stream()
                            .map(Object::toString)
                            .map(UUID::fromString)
                            .toList();
                }
                throw SerializationException.typeMismatch(List.of(), List.class, yamlValue);
            }
        });

        // Test deserialization with registered serializer
        List<String> rawData = List.of(
                "550e8400-e29b-41d4-a716-446655440000",
                "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        );

        assertDoesNotThrow(() -> {
            List<UUID> result = ConfigurationManager.deserialize(rawData, token);
            assertNotNull(result);
            assertEquals(2, result.size());
            assertEquals(UUID.fromString("550e8400-e29b-41d4-a716-446655440000"), result.get(0));
            assertEquals(UUID.fromString("6ba7b810-9dad-11d1-80b4-00c04fd430c8"), result.get(1));
        });
    }

}
