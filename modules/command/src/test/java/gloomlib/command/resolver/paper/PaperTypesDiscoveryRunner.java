package gloomlib.command.resolver.paper;

import gloomlib.command.resolver.registry.AutoRegistrar;

/**
 * 独立运行的 Paper 类型发现工具。
 */
public class PaperTypesDiscoveryRunner {

    public static void main(String[] args) {
        System.out.println("开始扫描 Paper ArgumentTypes...\n");
        
        try {
            AutoRegistrar.printAllTypes();
        } catch (Exception e) {
            System.err.println("扫描失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
