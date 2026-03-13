package gloomlib.script.core.parser.accessor;

import com.google.common.reflect.TypeToken;
import gloomlib.script.api.ScriptErrorHandler;
import gloomlib.script.core.CompilationContext;
import gloomlib.script.core.ScriptIR.IRType;
import gloomlib.script.core.codegen.ASMUtils;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * 运行时变量索引的数组取值器。
 * <p>
 * 与 {@link ArrayAccessor}（编译期常量索引）互补，支持 {@code tags[{idx}]} 语法。
 * 字节码序列：ILOAD idxSlot → XALOAD → (CHECKCAST)
 * <p>
 * varName 引用的变量必须为 INT 类型。
 */
public record DynamicArrayAccessor(
        String varName,
        TypeToken<?> returnType
) implements PropertyAccessor {

    @Override
    public void emitLoad(MethodVisitor mv, CompilationContext ctx) {
        IRType idxType = ctx.getType(varName);
        if (!idxType.isNumeric()) {
            throw gloomlib.script.api.ScriptCompileException.create(ctx.scriptId(), null,
                    gloomlib.diagnostic.DiagnosticCategory.TYPE,
                    "Dynamic array index variable '" + varName + "' must be numeric (int/long/double), but got: " + idxType);
        }
        int idxSlot = ctx.getSlot(varName);

        if (idxType == IRType.LONG) {
            mv.visitVarInsn(Opcodes.LLOAD, idxSlot);
            mv.visitInsn(Opcodes.L2I);
        } else if (idxType == IRType.DOUBLE) {
            ScriptErrorHandler.warning(
                    "Dynamic array index variable '" + varName + "' is DOUBLE — will be truncated to int via D2I. "
                            + "Consider using an INT variable for index access.");
            mv.visitVarInsn(Opcodes.DLOAD, idxSlot);
            mv.visitInsn(Opcodes.D2I);
        } else {
            mv.visitVarInsn(Opcodes.ILOAD, idxSlot);
        }

        Class<?> component = returnType.getRawType();
        if (component.isPrimitive()) {
            mv.visitInsn(ASMUtils.arrayLoadOpcode(component));
        } else {
            mv.visitInsn(Opcodes.AALOAD);
            if (component != Object.class) {
                mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(component));
            }
        }
    }
}
