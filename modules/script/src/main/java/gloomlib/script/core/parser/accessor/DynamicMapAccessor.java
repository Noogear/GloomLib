package gloomlib.script.core.parser.accessor;

import com.google.common.reflect.TypeToken;
import gloomlib.script.core.CompilationContext;
import gloomlib.script.core.ScriptIR.IRType;
import gloomlib.script.core.codegen.ASMUtils;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * 运行时变量键的 Map 取值器。
 * <p>
 * 与 {@link MapAccessor}（编译期常量键）互补，支持 {@code metadata[{someVar}]} 语法。
 * 字节码序列：ALOAD varSlot → (装箱) → INVOKEINTERFACE Map.get(Object) → CHECKCAST V
 * <p>
 * 编译期会校验变量类型与 Map 键类型的兼容性，类型不匹配时抛出 {@link gloomlib.script.api.ScriptCompileException}。
 *
 * @param varName         变量名（运行时提供键值）
 * @param expectedKeyType Map 键的期望类型（编译期已知，用于类型校验）
 * @param returnType      Map 值的类型
 */
public record DynamicMapAccessor(
        String varName,
        TypeToken<?> expectedKeyType,
        TypeToken<?> returnType
) implements PropertyAccessor {

    @Override
    public void emitLoad(MethodVisitor mv, CompilationContext ctx) {
        // 编译期类型校验：变量类型必须与 Map 键类型兼容
        IRType varIRType = ctx.getType(varName);
        Class<?> varRaw = com.google.common.primitives.Primitives.wrap(varIRType.getToken().getRawType());
        Class<?> keyRaw = com.google.common.primitives.Primitives.wrap(expectedKeyType.getRawType());
        if (keyRaw != Object.class && !keyRaw.isAssignableFrom(varRaw)) {
            throw gloomlib.script.api.ScriptCompileException.create(ctx.scriptId(), null,
                    gloomlib.diagnostic.DiagnosticCategory.TYPE,
                    "Dynamic Map key variable '" + varName + "' type mismatch: "
                            + "Map key expects " + keyRaw.getSimpleName()
                            + " but variable is " + varIRType);
        }

        int keySlot = ctx.getSlot(varName);
        IRType keyType = varIRType;

        if (keyType.isPrimitive()) {
            int loadOp = switch (keyType.base()) {
                case INT, BOOLEAN -> Opcodes.ILOAD;
                case LONG -> Opcodes.LLOAD;
                case DOUBLE -> Opcodes.DLOAD;
                default -> Opcodes.ALOAD;
            };
            mv.visitVarInsn(loadOp, keySlot);
            ASMUtils.emitBox(mv, keyType);
        } else {
            mv.visitVarInsn(Opcodes.ALOAD, keySlot);
        }

        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "get",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true);

        if (returnType.getRawType() != Object.class) {
            mv.visitTypeInsn(Opcodes.CHECKCAST,
                    Type.getInternalName(returnType.getRawType()));
        }
    }
}
