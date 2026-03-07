package gloomlib.test;

import com.google.gson.reflect.TypeToken;
import gloomlib.configuration.api.ConfigurationManager;
import gloomlib.configuration.api.TypeSerializer;
import gloomlib.configuration.api.exception.SerializationException;
import gloomlib.configuration.core.util.TypeInference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SerializationException and TypeToken integration.
 */
@DisplayName("SerializationException and TypeToken Integration Tests")
public class SerializationExceptionTest {

    @Test
    @DisplayName("SerializationException should provide detailed context")
    void testSerializationExceptionContext() {
        SerializationException ex = SerializationException.builder()
                .message("Failed to deserialize value")
                .path(List.of("classes", "warrior", "health"))
                .expectedType(Integer.class)
                .actualValue("invalid")
                .cause(new NumberFormatException("For input string: \"invalid\""))
                .build();

        // Test basic properties
        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("Failed to deserialize value"));
        assertTrue(ex.getMessage().contains("classes.warrior.health"));
        assertTrue(ex.getMessage().contains("Integer"));
        assertTrue(ex.getMessage().contains("invalid"));

        // Test path retrieval
        assertEquals(List.of("classes", "warrior", "health"), ex.getNodePath());
        assertEquals("classes.warrior.health", ex.getPathString());
        assertEquals(Integer.class, ex.getExpectedType());
        assertEquals("invalid", ex.getActualValue());

        // Test context string
        String context = ex.getContext();
        assertTrue(context.contains("classes.warrior.health"));
        assertTrue(context.contains("Integer"));
        assertTrue(context.contains("invalid"));

        // Test cause
        assertInstanceOf(NumberFormatException.class, ex.getCause());
    }

    @Test
    @DisplayName("SerializationException should handle null values gracefully")
    void testSerializationExceptionNullValues() {
        SerializationException ex = SerializationException.builder()
                .message("Simple error message")
                .build();

        assertNotNull(ex.getMessage());
        assertEquals(List.of(), ex.getNodePath());
        assertEquals("<root>", ex.getPathString());
        assertNull(ex.getExpectedType());
        assertNull(ex.getActualValue());
        assertNull(ex.getCause());
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
                throw new SerializationException("Expected List, got " + yamlValue.getClass());
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

    @Test
    @DisplayName("SerializationException should provide good error messages")
    void testSerializationExceptionErrorMessage() {
        // Test that SerializationException provides helpful debugging information
        SerializationException ex = SerializationException.builder()
                .message("Type mismatch")
                .path(List.of("database", "port"))
                .expectedType(Integer.class)
                .actualValue("localhost")
                .build();

        String message = ex.getMessage();
        assertTrue(message.contains("database.port"));
        assertTrue(message.contains("Integer"));
        assertTrue(message.contains("localhost"));
    }
}
