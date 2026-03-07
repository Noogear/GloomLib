// 临时测试文件：分析 Paper ArgumentTypes API
// 看看是否有批量注册的可能

import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

class ArgumentTypesAnalyzer {
    public static void main(String[] args) {
        System.out.println("=== Paper ArgumentTypes 所有静态方法 ===\n");
        
        Method[] methods = ArgumentTypes.class.getDeclaredMethods();
        for (Method method : methods) {
            if (Modifier.isStatic(method.getModifiers()) 
                && Modifier.isPublic(method.getModifiers())
                && method.getParameterCount() == 0) {
                
                System.out.println(method.getName() + "()");
                System.out.println("  返回类型: " + method.getReturnType().getSimpleName());
            }
        }
    }
}
