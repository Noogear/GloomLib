package gloomlib.command.resolver.paper;

import gloomlib.command.resolver.ArgumentResolverRegistry;
import gloomlib.command.resolver.registry.AutoRegistrar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试自动批量注册功能。
 */
class AutoRegistrarTest {

    private ArgumentResolverRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ArgumentResolverRegistry();
    }

    @Test
    @DisplayName("registerAll - 注册所有可自动调用的类型")
    void testRegisterAll() {
        // 先检查是否可以发现类型
        var discovered = AutoRegistrar.discoverAllTypes();
        System.out.println("\n发现了 " + discovered.size() + " 个 Paper 类型");
        
        if (discovered.isEmpty()) {
            System.out.println("警告: Paper ArgumentTypes 类不可用，跳过测试");
            return; // 测试环境可能没有 Paper API
        }
        
        int registered = AutoRegistrar.registerAll(registry);

        System.out.println("注册了 " + registered + " 个类型");

        // 验证至少注册了一些常见类型
        assertTrue(registered > 20, "应该至少注册 20+ 个类型，实际: " + registered);

        // 验证具体类型已注册
        assertTrue(registry.hasResolver(java.util.UUID.class), "应该注册 UUID");
        assertTrue(registry.hasResolver(org.bukkit.GameMode.class), "应该注册 GameMode");
        assertTrue(registry.hasResolver(net.kyori.adventure.key.Key.class), "应该注册 Key");
        assertTrue(registry.hasResolver(net.kyori.adventure.text.Component.class), "应该注册 Component");
    }

    @Test
    @DisplayName("registerParameterless - 只注册无参数类型")
    void testRegisterParameterless() {
        var discovered = AutoRegistrar.discoverAllTypes();
        if (discovered.isEmpty()) {
            System.out.println("警告: Paper ArgumentTypes 类不可用，跳过测试");
            return;
        }
        
        int registered = AutoRegistrar.registerParameterless(registry);

        System.out.println("\n注册了 " + registered + " 个无参数类型");

        assertTrue(registered > 15, "应该至少注册 15+ 个无参数类型，实际: " + registered);
        assertTrue(registry.hasResolver(java.util.UUID.class));
    }

    @Test
    @DisplayName("listAutoRegisterableTypes - 列出可注册类型")
    void testListAutoRegisterableTypes() {
        System.out.println("\n=== 测试列出可注册类型 ===");
        AutoRegistrar.listAutoRegisterableTypes();
    }

    @Test
    @DisplayName("注册后可以获取 resolver")
    void testGetResolverAfterRegistration() {
        var discovered = AutoRegistrar.discoverAllTypes();
        if (discovered.isEmpty()) {
            System.out.println("警告: Paper ArgumentTypes 类不可用，跳过测试");
            return;
        }
        
        AutoRegistrar.registerAll(registry);

        var uuidResolver = registry.getResolver(java.util.UUID.class);
        assertNotNull(uuidResolver, "应该能获取 UUID resolver");

        var gameModeResolver = registry.getResolver(org.bukkit.GameMode.class);
        assertNotNull(gameModeResolver, "应该能获取 GameMode resolver");
    }

    @Test
    @DisplayName("重复注册应该覆盖")
    void testDuplicateRegistration() {
        var discovered = AutoRegistrar.discoverAllTypes();
        if (discovered.isEmpty()) {
            System.out.println("警告: Paper ArgumentTypes 类不可用，跳过测试");
            return;
        }
        
        int first = AutoRegistrar.registerAll(registry);
        int second = AutoRegistrar.registerAll(registry);

        assertEquals(first, second, "第二次注册应该返回相同数量");
    }

    @Test
    @DisplayName("注册性能测试")
    void testRegistrationPerformance() {
        var discovered = AutoRegistrar.discoverAllTypes();
        if (discovered.isEmpty()) {
            System.out.println("警告: Paper ArgumentTypes 类不可用，跳过测试");
            return;
        }
        
        long start = System.currentTimeMillis();
        int registered = AutoRegistrar.registerAll(registry);
        long elapsed = System.currentTimeMillis() - start;

        System.out.println("\n注册 " + registered + " 个类型耗时: " + elapsed + "ms");

        assertTrue(elapsed < 100, "注册应该在 100ms 内完成");
    }
}

