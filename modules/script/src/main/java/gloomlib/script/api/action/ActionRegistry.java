package gloomlib.script.api.action;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 动作注册表，管理脚本可调用的动作定义。
 * <p>
 * 通过 {@link ScriptAction} 注解标注静态方法，调用 {@link #scanAndRegister(Class[])}
 * 自动扫描注册，无需手动硬编码。
 */
@SuppressWarnings("null")
public final class ActionRegistry {


    private final Map<String, ActionDef> actions = new HashMap<>();

    /**
     * 系统保留字（实例级，可动态扩展）。
     * 禁止将这些字符串作为动作名称，以防与 ScriptParser 动态推断规则发生碰撞。
     */
    private final Set<String> reservedKeys = new java.util.HashSet<>(Set.of(
            "type", "action", "return", "check", "switch", "args", "store", "priority", "event"));

    public ActionRegistry() {
    }

    /**
     * 动态添加一个保留字。已有的会被静默忽略（幂等）。
     *
     * @param key 需要保留的关键字（大小写不敏感，内部统一转小写存储）
     */
    public void addReservedKey(String key) {
        Preconditions.checkNotNull(key, "reserved key");
        reservedKeys.add(key.toLowerCase());
    }

    /**
     * 批量添加保留字。
     *
     * @param keys 需要添加的关键字集合
     */
    public void addReservedKeys(java.util.Collection<String> keys) {
        Preconditions.checkNotNull(keys, "reserved keys");
        keys.forEach(k -> reservedKeys.add(k.toLowerCase()));
    }

    /**
     * 返回当前保留字的不可变快照（用于调试/展示）。
     */
    public Set<String> reservedKeys() {
        return java.util.Collections.unmodifiableSet(reservedKeys);
    }

    /**
     * 扫描指定类中带 {@link ScriptAction} 注解的静态方法并注册。
     * <p>
     * 自动从方法签名推导 ASM owner/descriptor/invokeType/paramCount。
     * 若参数类型为原始 {@link Enum}（"开放枚举"），必须同时标注 {@link EnumClass}
     * 指定具体枚举类，否则抛出 {@link IllegalArgumentException}。
     */
    public void scanAndRegister(Class<?>... providerClasses) {
        for (Class<?> clazz : providerClasses) {
            String owner = Type.getInternalName(clazz);
            for (Method method : clazz.getDeclaredMethods()) {
                ScriptAction annotation = method.getAnnotation(ScriptAction.class);
                if (annotation == null)
                    continue;

                Preconditions.checkArgument(
                        Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()),
                        "@ScriptAction method must be public static: %s", method);

                String actionName = annotation.value();
                String descriptor = Type.getMethodDescriptor(method);
                int paramCount = method.getParameterCount();
                Class<?>[] paramTypes = method.getParameterTypes();
                java.lang.reflect.Type[] genericTypes = method.getGenericParameterTypes();
                com.google.common.reflect.TypeToken<?>[] genericParamTypes = new com.google.common.reflect.TypeToken<?>[paramCount];
                Class<?>[] enumHints = new Class<?>[paramCount];
                java.lang.reflect.Parameter[] params = method.getParameters();
                for (int i = 0; i < paramCount; i++) {
                    genericParamTypes[i] = com.google.common.reflect.TypeToken.of(genericTypes[i]);
                    EnumClass hint = params[i].getAnnotation(EnumClass.class);
                    if (hint != null) {
                        Preconditions.checkArgument(
                                hint.value().isEnum(),
                                "@EnumClass value must be a concrete enum class, but got '%s' on parameter %d of %s",
                                hint.value().getSimpleName(), i, method);
                        enumHints[i] = hint.value();
                    } else if (paramTypes[i] == Enum.class) {
                        throw new IllegalArgumentException(
                                "Parameter " + i + " of @ScriptAction '" + actionName
                                + "' has type Enum without @EnumClass annotation. "
                                + "Add @EnumClass(<YourEnum>.class) to specify the concrete enum type.");
                    }
                }
                Class<?> returnType = method.getReturnType();

                register(actionName, new ActionDef(
                        owner, method.getName(), descriptor,
                        Opcodes.INVOKESTATIC, paramCount, paramTypes, genericParamTypes, returnType, true,
                        annotation.consumesPayload(), enumHints));
            }
        }
    }

    /**
     * 手动注册动作。
     */
    public void register(String name, ActionDef def) {
        Preconditions.checkNotNull(name, "action name");
        Preconditions.checkArgument(!reservedKeys.contains(name.toLowerCase()),
                "Cannot register action using reserved keyword: %s", name);
        Preconditions.checkNotNull(def, "action definition");
        actions.put(name, def);
    }

    /**
     * 查找动作定义。
     */
    public ActionDef lookup(String name) {
        ActionDef def = actions.get(name);
        if (def == null) {
            throw gloomlib.script.api.ScriptCompileException.parse("Unknown action: " + name
                    + ". Available: " + actions.keySet());
        }
        return def;
    }

    /**
     * 检查动作是否已注册。
     */
    public boolean has(String name) {
        return actions.containsKey(name);
    }

    /**
     * 返回所有已注册动作的不可变快照。
     */
    public ImmutableMap<String, ActionDef> all() {
        return ImmutableMap.copyOf(actions);
    }

    /**
     * 标注一个方法参数，当参数类型为原始 {@link Enum}（即"开放枚举"）时，
     * 告知框架在编译期使用的具体枚举类。
     *
     * <h3>使用场景</h3>
     * <p>
     * 当一个 Action 需要接受来自不同 Event 的不同枚举字段时，可将参数类型声明为
     * {@code Enum<?>}，并为每个参数标注 {@code @EnumClass} 指定具体类。
     * 框架在编译期（字节码生成阶段）读取此注解，发射 {@code GETSTATIC}，
     * 因此运行期性能与具体枚举参数完全相同（零开销）。
     *
     * <h3>字面量参数</h3>
     * <p>
     * 若 YAML args 传入字面量（如 {@code ENDER_PEARL}），框架使用 {@code @EnumClass}
     * 声明的类进行编译期合法性校验，并在字节码中发射精确的 {@code GETSTATIC}。
     *
     * <h3>变量引用参数</h3>
     * <p>
     * 若 YAML args 传入变量引用（如 {@code {event.cause}}），框架忽略 {@code @EnumClass}
     * 直接加载变量（变量精确类型在编译期已知），注解此时仅作文档提示。
     *
     * <pre>{@code
     * @ScriptAction("fireEvent")
     * public static void fireEvent(
     *     Event event,
     *     @EnumClass(PlayerTeleportEvent.TeleportCause.class) Enum<?> cause
     * ) { ... }
     * }</pre>
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.PARAMETER)
    public @interface EnumClass {
        /** 该参数位置期望的具体枚举类（必须是 {@code isEnum() == true} 的类）。 */
        Class<? extends Enum<?>> value();
    }

    /**
     * 标注一个静态方法为脚本动作。
     * <p>
     * 方法必须为 {@code public static}，注册表将从方法签名自动推导
     * ASM 调用描述符和参数数量。
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface ScriptAction {
        /**
         * 动作名称（YAML 中使用的标识符）。
         */
        String value();

        /**
         * 是否将方法第一个参数视为脚本 payload 对象（由引擎自动注入，不需要在 YAML args 中提供）。
         * <p>
         * 默认 {@code true}：第一个参数是 payload，YAML {@code args} 从第二个参数开始对应。<br>
         * 设为 {@code false}：纯工具方法，所有参数均由 YAML {@code args} 提供，引擎不注入 payload。
         * <pre>{@code
         * // consumesPayload = true（默认）：第一位是 payload，YAML 只需传后续参数
         * @ScriptAction("sendMessage")
         * public static void sendMessage(Player target, String message) { ... }
         * // YAML: args: ["Hello"]
         *
         * // consumesPayload = false：纯工具函数，所有参数来自 YAML
         * @ScriptAction(value = "formatNumber", consumesPayload = false)
         * public static String formatNumber(int value, int digits) { ... }
         * // YAML: args: ["{hp}", "2"]
         * }</pre>
         */
        boolean consumesPayload() default true;
    }

    /**
     * 动作定义，存储目标方法的字节码调用信息。
     *
     * <p>{@code enumHints} 是与 {@code paramTypes} 等长的数组：
     * 当某参数类型为原始 {@link Enum}（开放枚举）时，对应槽位存放由
     * {@link EnumClass} 注解提供的具体枚举类；其余槽位为 {@code null}。
     */
    public record ActionDef(
            String owner,
            String method,
            String descriptor,
            int invokeType,
            int paramCount,
            Class<?>[] paramTypes,
            com.google.common.reflect.TypeToken<?>[] genericParamTypes,
            Class<?> returnType,
            boolean isBuiltin,
            boolean consumesPayload,
            Class<?>[] enumHints) {

        /**
         * 简易构造（非 builtin，consumesPayload=true，无开放枚举参数）。
         */
        public ActionDef(String owner, String method, String descriptor, int invokeType, int paramCount,
                         Class<?>[] paramTypes, com.google.common.reflect.TypeToken<?>[] genericParamTypes,
                         Class<?> returnType) {
            this(owner, method, descriptor, invokeType, paramCount, paramTypes, genericParamTypes,
                    returnType, false, true, new Class<?>[paramCount]);
        }

        /**
         * 简易构造（指定 isBuiltin，consumesPayload=true，无开放枚举参数）。
         */
        public ActionDef(String owner, String method, String descriptor, int invokeType, int paramCount,
                         Class<?>[] paramTypes, com.google.common.reflect.TypeToken<?>[] genericParamTypes,
                         Class<?> returnType, boolean isBuiltin) {
            this(owner, method, descriptor, invokeType, paramCount, paramTypes, genericParamTypes,
                    returnType, isBuiltin, true, new Class<?>[paramCount]);
        }

        /**
         * 简易构造（指定 isBuiltin + consumesPayload，无开放枚举参数）。
         */
        public ActionDef(String owner, String method, String descriptor, int invokeType, int paramCount,
                         Class<?>[] paramTypes, com.google.common.reflect.TypeToken<?>[] genericParamTypes,
                         Class<?> returnType, boolean isBuiltin, boolean consumesPayload) {
            this(owner, method, descriptor, invokeType, paramCount, paramTypes, genericParamTypes,
                    returnType, isBuiltin, consumesPayload, new Class<?>[paramCount]);
        }

        /**
         * 返回指定参数位置的开放枚举提示类；若该参数非开放枚举则返回 {@code null}。
         */
        public Class<?> enumHint(int paramIndex) {
            return (enumHints != null && paramIndex < enumHints.length) ? enumHints[paramIndex] : null;
        }
    }
}
