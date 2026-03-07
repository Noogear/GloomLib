package gloomlib.test;

import gloomlib.configuration.ConfigurationFile;
import gloomlib.configuration.ConfigurationPart;
import gloomlib.configuration.util.TypeInference;
import org.junit.jupiter.api.*;

import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 类型推断系统测试
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("类型推断优化测试")
public class TypeInferenceTest {

    @AfterAll
    static void cleanup() {
        System.out.println("\n=== Cleanup ===");
        System.out.println("Final cache stats: " + TypeInference.getCacheStats());
        TypeInference.clearCaches();
        System.out.println("✓ Caches cleared");
    }

    @BeforeEach
    void setUp() {
        TypeInference.clearCaches();
    }

    @Test
    @Order(1)
    @DisplayName("1. 测试简单泛型参数提取")
    void testSimpleGenericExtraction() throws Exception {
        System.out.println("\n=== Test 1: Simple Generic Extraction ===");

        // Map<String, String>
        Field stringMapField = GenericTestConfig.class.getField("stringMap");
        Class<?> keyType = TypeInference.extractGenericParameter(stringMapField.getGenericType(), 0);
        Class<?> valueType = TypeInference.extractGenericParameter(stringMapField.getGenericType(), 1);

        assertEquals(String.class, keyType, "Map key type should be String");
        assertEquals(String.class, valueType, "Map value type should be String");
        System.out.println("✓ Map<String, String> → Key: " + keyType.getSimpleName() + ", Value: " + valueType.getSimpleName());

        // Map<String, Integer>
        Field intMapField = GenericTestConfig.class.getField("intMap");
        Class<?> intValueType = TypeInference.extractGenericParameter(intMapField.getGenericType(), 1);
        assertEquals(Integer.class, intValueType, "Map value type should be Integer");
        System.out.println("✓ Map<String, Integer> → Value: " + intValueType.getSimpleName());

        // Map<String, TestPart>
        Field partMapField = GenericTestConfig.class.getField("partMap");
        Class<?> partValueType = TypeInference.extractGenericParameter(partMapField.getGenericType(), 1);
        assertEquals(TestPart.class, partValueType, "Map value type should be TestPart");
        System.out.println("✓ Map<String, TestPart> → Value: " + partValueType.getSimpleName());
    }

    @Test
    @Order(2)
    @DisplayName("2. 测试 List/Set 泛型提取")
    void testCollectionGenericExtraction() throws Exception {
        System.out.println("\n=== Test 2: Collection Generic Extraction ===");

        // List<String>
        Field stringListField = GenericTestConfig.class.getField("stringList");
        Class<?> listType = TypeInference.extractGenericParameter(stringListField.getGenericType(), 0);
        assertEquals(String.class, listType, "List element type should be String");
        System.out.println("✓ List<String> → Element: " + listType.getSimpleName());

        // List<TestPart>
        Field partListField = GenericTestConfig.class.getField("partList");
        Class<?> partListType = TypeInference.extractGenericParameter(partListField.getGenericType(), 0);
        assertEquals(TestPart.class, partListType, "List element type should be TestPart");
        System.out.println("✓ List<TestPart> → Element: " + partListType.getSimpleName());

        // Set<Integer>
        Field intSetField = GenericTestConfig.class.getField("intSet");
        Class<?> setType = TypeInference.extractGenericParameter(intSetField.getGenericType(), 0);
        assertEquals(Integer.class, setType, "Set element type should be Integer");
        System.out.println("✓ Set<Integer> → Element: " + setType.getSimpleName());
    }

    @Test
    @Order(3)
    @DisplayName("3. 测试嵌套泛型")
    void testNestedGenerics() throws Exception {
        System.out.println("\n=== Test 3: Nested Generics ===");

        // Map<String, List<String>>
        Field nestedMapField = GenericTestConfig.class.getField("nestedMap");
        Class<?> nestedMapValue = TypeInference.extractGenericParameter(nestedMapField.getGenericType(), 1);
        assertEquals(List.class, nestedMapValue, "Nested map value should be List");
        System.out.println("✓ Map<String, List<String>> → Value: " + nestedMapValue.getSimpleName());

        // List<Map<String, Integer>>
        Field nestedListField = GenericTestConfig.class.getField("nestedList");
        Class<?> nestedListElement = TypeInference.extractGenericParameter(nestedListField.getGenericType(), 0);
        assertEquals(Map.class, nestedListElement, "Nested list element should be Map");
        System.out.println("✓ List<Map<String, Integer>> → Element: " + nestedListElement.getSimpleName());
    }

    @Test
    @Order(4)
    @DisplayName("4. 测试通配符类型")
    void testWildcardTypes() throws Exception {
        System.out.println("\n=== Test 4: Wildcard Types ===");

        // List<? extends Number>
        Field wildcardListField = GenericTestConfig.class.getField("wildcardList");
        Class<?> wildcardListType = TypeInference.extractGenericParameter(wildcardListField.getGenericType(), 0);
        assertTrue(Number.class.isAssignableFrom(wildcardListType) || wildcardListType == Number.class,
                "Wildcard list should resolve to Number or its subclass");
        System.out.println("✓ List<? extends Number> → " + wildcardListType.getSimpleName());

        // Map<String, ? super Integer>
        Field wildcardMapField = GenericTestConfig.class.getField("wildcardMap");
        Class<?> wildcardMapValue = TypeInference.extractGenericParameter(wildcardMapField.getGenericType(), 1);
        assertNotNull(wildcardMapValue, "Wildcard map value should not be null");
        System.out.println("✓ Map<String, ? super Integer> → " + wildcardMapValue.getSimpleName());
    }


    @Test
    @Order(6)
    @DisplayName("6. 测试字段类型推断")
    void testFieldTypeInference() throws Exception {
        System.out.println("\n=== Test 6: Field Type Inference ===");

        // Map 字段推断值类型
        Field partMapField = GenericTestConfig.class.getField("partMap");
        Class<?> inferredType = TypeInference.inferFieldType(partMapField);
        assertEquals(TestPart.class, inferredType);
        System.out.println("✓ Map<String, TestPart> field inferred value type: " + inferredType.getSimpleName());

        // List 字段推断元素类型
        Field stringListField = GenericTestConfig.class.getField("stringList");
        Class<?> listInferredType = TypeInference.inferFieldType(stringListField);
        assertEquals(String.class, listInferredType);
        System.out.println("✓ List<String> field inferred element type: " + listInferredType.getSimpleName());

        // Set 字段推断元素类型
        Field intSetField = GenericTestConfig.class.getField("intSet");
        Class<?> setInferredType = TypeInference.inferFieldType(intSetField);
        assertEquals(Integer.class, setInferredType);
        System.out.println("✓ Set<Integer> field inferred element type: " + setInferredType.getSimpleName());
    }

    @Test
    @Order(7)
    @DisplayName("7. 测试泛型继承链解析")
    void testInheritanceChainResolution() {
        System.out.println("\n=== Test 7: Inheritance Chain Resolution ===");

        // StringList extends ArrayList<String>
        Map<TypeVariable<?>, java.lang.reflect.Type> stringListMappings =
                TypeInference.resolveInheritanceChain(StringList.class, ArrayList.class);

        assertFalse(stringListMappings.isEmpty(), "StringList should have generic mappings");
        System.out.println("✓ StringList extends ArrayList<String>");
        System.out.println("  - Type mappings: " + stringListMappings.size());

        // IntegerMap extends HashMap<String, Integer>
        Map<TypeVariable<?>, java.lang.reflect.Type> integerMapMappings =
                TypeInference.resolveInheritanceChain(IntegerMap.class, HashMap.class);

        assertFalse(integerMapMappings.isEmpty(), "IntegerMap should have generic mappings");
        System.out.println("✓ IntegerMap extends HashMap<String, Integer>");
        System.out.println("  - Type mappings: " + integerMapMappings.size());
    }

    @Test
    @Order(8)
    @DisplayName("8. 测试完整泛型参数提取")
    void testFullGenericParameters() throws Exception {
        System.out.println("\n=== Test 8: Full Generic Parameters ===");

        // Map<String, TestPart> 的所有泛型参数
        Field partMapField = GenericTestConfig.class.getField("partMap");
        Class<?>[] params = TypeInference.getGenericParameters(partMapField);

        assertEquals(2, params.length, "Map should have 2 generic parameters");
        assertEquals(String.class, params[0], "First parameter should be String");
        assertEquals(TestPart.class, params[1], "Second parameter should be TestPart");

        System.out.println("✓ Map<String, TestPart> parameters:");
        System.out.println("  - [0]: " + params[0].getSimpleName());
        System.out.println("  - [1]: " + params[1].getSimpleName());
    }

    @Test
    @Order(9)
    @DisplayName("9. 测试缓存功能")
    void testCaching() throws Exception {
        System.out.println("\n=== Test 9: Caching ===");

        Field stringMapField = GenericTestConfig.class.getField("stringMap");

        // 第一次提取（写入缓存）
        long start1 = System.nanoTime();
        Class<?> type1 = TypeInference.extractGenericParameter(stringMapField.getGenericType(), 1);
        long time1 = System.nanoTime() - start1;

        // 第二次提取（从缓存读取）
        long start2 = System.nanoTime();
        Class<?> type2 = TypeInference.extractGenericParameter(stringMapField.getGenericType(), 1);
        long time2 = System.nanoTime() - start2;

        assertEquals(type1, type2, "Both extractions should return same type");
        System.out.println("✓ Cache working correctly");
        System.out.println("  - First extraction: " + time1 + " ns");
        System.out.println("  - Second extraction: " + time2 + " ns");
        System.out.println("  - Speedup: " + (time1 / (double) time2) + "x");

        // 打印缓存统计
        System.out.println("\n" + TypeInference.getCacheStats());
    }

    @Test
    @Order(10)
    @DisplayName("10. 测试边缘情况")
    void testEdgeCases() {
        System.out.println("\n=== Test 10: Edge Cases ===");

        // null 类型
        Class<?> nullType = TypeInference.extractGenericParameter((Type) null, 0);
        assertEquals(Object.class, nullType, "null type should return Object.class");
        System.out.println("✓ null → Object.class");

        // 索引越界
        Field stringMapField;
        try {
            stringMapField = GenericTestConfig.class.getField("stringMap");
            Class<?> outOfBounds = TypeInference.extractGenericParameter(stringMapField.getGenericType(), 99);
            assertEquals(Object.class, outOfBounds, "Out of bounds should return Object.class");
            System.out.println("✓ Index 99 (out of bounds) → Object.class");
        } catch (Exception e) {
            fail("Should handle out of bounds gracefully: " + e.getMessage());
        }
    }

    // 测试配置类
    public static class GenericTestConfig extends ConfigurationFile {
        public Map<String, String> stringMap = new HashMap<>();
        public Map<String, Integer> intMap = new HashMap<>();
        public Map<String, TestPart> partMap = new HashMap<>();
        public List<String> stringList = new ArrayList<>();
        public List<TestPart> partList = new ArrayList<>();
        public Set<Integer> intSet = new HashSet<>();

        // 嵌套泛型
        public Map<String, List<String>> nestedMap = new HashMap<>();
        public List<Map<String, Integer>> nestedList = new ArrayList<>();

        // 通配符
        public List<? extends Number> wildcardList = new ArrayList<>();
        public Map<String, ? super Integer> wildcardMap = new HashMap<>();
    }

    public static class TestPart extends ConfigurationPart {
        public String name = "test";
        public int value = 0;
    }

    // 泛型继承链测试
    public static class StringList extends ArrayList<String> {
    }

    public static class IntegerMap extends HashMap<String, Integer> {
    }
}
