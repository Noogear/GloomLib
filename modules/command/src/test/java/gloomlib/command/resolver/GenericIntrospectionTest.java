package gloomlib.command.resolver;

import com.mojang.brigadier.arguments.ArgumentType;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.UUID;

/**
 * 测试是否能通过反射获取 ArgumentTypes 的泛型信息。
 */
class GenericIntrospectionTest {

    @Test
    void testRuntimeGenericErasure() {
        System.out.println("=== 运行时泛型擦除测试 ===\n");
        
        try {
            Method uuidMethod = ArgumentTypes.class.getMethod("uuid");
            
            // 1. 获取返回类型
            Class<?> returnType = uuidMethod.getReturnType();
            System.out.println("返回类型: " + returnType); // ArgumentType
            
            // 2. 尝试获取泛型信息
            Type genericReturnType = uuidMethod.getGenericReturnType();
            System.out.println("泛型返回类型: " + genericReturnType);
            
            if (genericReturnType instanceof ParameterizedType) {
                ParameterizedType pt = (ParameterizedType) genericReturnType;
                Type[] typeArgs = pt.getActualTypeArguments();
                
                if (typeArgs.length > 0) {
                    System.out.println("✅ 泛型参数: " + typeArgs[0]); // 应该是 UUID
                    System.out.println("✅ 成功！可以获取泛型信息");
                    
                    // 验证是否匹配
                    if (typeArgs[0] == UUID.class) {
                        System.out.println("✅ 类型匹配: UUID.class");
                    }
                } else {
                    System.out.println("❌ 没有泛型参数");
                }
            } else {
                System.out.println("❌ 不是 ParameterizedType");
            }
            
        } catch (Exception e) {
            System.err.println("❌ 异常: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Test
    void testMultipleMethods() {
        System.out.println("\n=== 批量测试泛型信息获取 ===\n");
        
        String[] methodNames = {
            "uuid", "gameMode", "world", "time", "angle",
            "entity", "entities", "player", "blockPosition"
        };
        
        int successCount = 0;
        int failCount = 0;
        
        for (String methodName : methodNames) {
            try {
                Method method = ArgumentTypes.class.getMethod(methodName);
                Type genericType = method.getGenericReturnType();
                
                if (genericType instanceof ParameterizedType) {
                    ParameterizedType pt = (ParameterizedType) genericType;
                    Type[] typeArgs = pt.getActualTypeArguments();
                    
                    if (typeArgs.length > 0) {
                        System.out.printf("✅ %-20s -> %s%n", 
                            methodName, typeArgs[0].getTypeName());
                        successCount++;
                    } else {
                        System.out.printf("❌ %-20s -> 无泛型参数%n", methodName);
                        failCount++;
                    }
                } else {
                    System.out.printf("⚠️  %-20s -> 原始类型: %s%n", 
                        methodName, genericType);
                    failCount++;
                }
                
            } catch (Exception e) {
                System.out.printf("❌ %-20s -> 异常: %s%n", methodName, e.getMessage());
                failCount++;
            }
        }
        
        System.out.printf("%n成功: %d, 失败: %d%n", successCount, failCount);
    }

    @Test
    void testResourceMethod() {
        System.out.println("\n=== 测试 resource() 方法的泛型 ===\n");
        
        try {
            Method[] methods = ArgumentTypes.class.getDeclaredMethods();
            
            for (Method method : methods) {
                if (method.getName().equals("resource")) {
                    System.out.println("方法签名: " + method);
                    
                    Type genericReturn = method.getGenericReturnType();
                    System.out.println("泛型返回类型: " + genericReturn);
                    
                    if (genericReturn instanceof ParameterizedType) {
                        ParameterizedType pt = (ParameterizedType) genericReturn;
                        Type[] typeArgs = pt.getActualTypeArguments();
                        System.out.println("泛型参数: " + java.util.Arrays.toString(typeArgs));
                    }
                    
                    System.out.println();
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
