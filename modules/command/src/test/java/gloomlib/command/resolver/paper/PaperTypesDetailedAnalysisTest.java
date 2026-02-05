package gloomlib.command.resolver.paper;

import gloomlib.command.resolver.registry.AutoRegistrar;
import org.junit.jupiter.api.Test;

/**
 * 详细分析 Paper ArgumentTypes 的完整信息。
 */
class PaperTypesDetailedAnalysisTest {

    @Test
    void analysisCompleteTypeInfo() throws Exception {
        var types = AutoRegistrar.discoverAllTypes();
        
        var outputPath = java.nio.file.Path.of("build", "paper-types-detailed.txt");
        java.nio.file.Files.createDirectories(outputPath.getParent());
        
        try (var writer = new java.io.PrintWriter(outputPath.toFile())) {
            writer.println("=".repeat(80));
            writer.println("Paper ArgumentTypes 完整分析");
            writer.println("=".repeat(80) + "\n");
            
            for (var info : types) {
                writer.println("方法名: " + info.methodName() + "()");
                writer.println("  目标类型: " + (info.targetType() != null ? 
                    info.targetType().getName() : "Unknown"));
                
                // 获取返回类型的完整信息
                var method = info.method();
                writer.println("  返回类型: " + method.getGenericReturnType());
                
                if (info.parameterCount() > 0) {
                    writer.println("  参数: " + info.getParameterDescription());
                    for (int i = 0; i < info.parameterCount(); i++) {
                        writer.println("    [" + i + "] " + 
                            info.parameterTypes()[i].getName());
                    }
                }
                
                writer.println("  可自动调用: " + info.canAutoInvoke());
                writer.println();
            }
            
            writer.println("=".repeat(80));
            writer.println("总计: " + types.size() + " 个类型");
        }
        
        System.out.println("详细分析已保存到: build/paper-types-detailed.txt");
        
        // 打印到控制台
        String content = java.nio.file.Files.readString(outputPath);
        System.out.println(content);
    }
    
    @Test
    void findSpecificTypes() {
        var types = AutoRegistrar.discoverAllTypes();
        
        String[] interestingTypes = {
            "world", "player", "entity", "blockPosition", 
            "itemStack", "component"
        };
        
        System.out.println("\n=== 重点类型详细信息 ===\n");
        
        for (String name : interestingTypes) {
            types.stream()
                .filter(t -> t.methodName().equals(name))
                .findFirst()
                .ifPresent(info -> {
                    System.out.println("【" + info.methodName() + "()】");
                    System.out.println("  目标类型: " + 
                        (info.targetType() != null ? info.targetType().getName() : "Unknown"));
                    System.out.println("  返回类型: " + info.method().getGenericReturnType());
                    
                    // 尝试获取实际的 ArgumentType
                    try {
                       Object result = info.method().invoke(null);
                        System.out.println("  实际类型: " + result.getClass().getName());
                    } catch (Exception e) {
                        System.out.println("  无法实例化: " + e.getMessage());
                    }
                    System.out.println();
                });
        }
    }
}
