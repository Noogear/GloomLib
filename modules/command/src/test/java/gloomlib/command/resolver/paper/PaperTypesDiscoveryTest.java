package gloomlib.command.resolver.paper;

import gloomlib.command.resolver.registry.AutoRegistrar;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

/**
 * 测试 Paper ArgumentTypes 发现功能。
 */
class PaperTypesDiscoveryTest {

    @Test
    void testDiscoverAllTypes() {
        List<AutoRegistrar.TypeInfo> types = AutoRegistrar.discoverAllTypes();
        
        System.out.println("\n=== 发现的所有 Paper ArgumentTypes ===");
        System.out.println("总数: " + types.size() + "\n");
        
        for (AutoRegistrar.TypeInfo info : types) {
            System.out.printf("%-30s → %-35s [%s]%n",
                info.methodName() + "()",
                info.targetType() != null ? info.targetType().getName() : "Unknown",
                info.getParameterDescription()
            );
        }
    }

    @Test
    void testGroupByCategory() {
        Map<String, List<AutoRegistrar.TypeInfo>> grouped = 
            AutoRegistrar.groupByCategory();
        
        System.out.println("\n=== 按类别分组的 Paper ArgumentTypes ===\n");
        
        grouped.forEach((category, types) -> {
            System.out.println("【" + category + "】 共 " + types.size() + " 个:");
            types.forEach(info -> {
                String params = info.parameterCount() > 0 
                    ? " (" + info.getParameterDescription() + ")" 
                    : "";
                System.out.println("  - " + info.methodName() + params + 
                    " → " + (info.targetType() != null ? info.targetType().getSimpleName() : "?"));
            });
            System.out.println();
        });
    }

    @Test
    void testPrintAllTypes() throws Exception {
        // 同时输出到控制台和文件
        var outputPath = java.nio.file.Path.of("build", "paper-types-discovery.txt");
        java.nio.file.Files.createDirectories(outputPath.getParent());
        
        try (var writer = new java.io.PrintWriter(outputPath.toFile())) {
            // 捕获输出
            var baos = new java.io.ByteArrayOutputStream();
            var originalOut = System.out;
            
            try (var ps = new java.io.PrintStream(new java.io.OutputStream() {
                @Override
                public void write(int b) {
                    baos.write(b);
                    originalOut.write(b);
                }
            })) {
                System.setOut(ps);
                AutoRegistrar.printAllTypes();
            } finally {
                System.setOut(originalOut);
            }
            
            // 写入文件
            writer.println(baos);
        }
        
        System.out.println("\n输出已保存到: build/paper-types-discovery.txt");
    }

    @Test
    void testFilterAutoInvokable() {
        List<AutoRegistrar.TypeInfo> autoInvokable = 
            AutoRegistrar.discoverAllTypes().stream()
                .filter(AutoRegistrar.TypeInfo::canAutoInvoke)
                .toList();
        
        System.out.println("\n=== 可以自动调用的类型（无参数或有默认值）===");
        System.out.println("总数: " + autoInvokable.size() + "\n");
        
        autoInvokable.forEach(info -> {
            System.out.printf("%-30s → %s%n",
                info.methodName() + "()",
                info.targetType() != null ? info.targetType().getSimpleName() : "Unknown"
            );
        });
    }

    @Test
    void testParameterizedTypes() {
        List<AutoRegistrar.TypeInfo> parameterized = 
            AutoRegistrar.discoverAllTypes().stream()
                .filter(info -> info.parameterCount() > 0)
                .toList();
        
        System.out.println("\n=== 需要参数的类型 ===");
        System.out.println("总数: " + parameterized.size() + "\n");
        
        parameterized.forEach(info -> {
            System.out.printf("%-30s → %-30s [%s]%n",
                info.methodName() + "()",
                info.targetType() != null ? info.targetType().getSimpleName() : "Unknown",
                info.getParameterDescription()
            );
        });
    }
}
