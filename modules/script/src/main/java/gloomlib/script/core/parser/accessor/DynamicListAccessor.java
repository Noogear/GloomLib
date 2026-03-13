package gloomlib.script.core.parser.accessor;

import com.google.common.reflect.TypeToken;
import gloomlib.script.api.ScriptErrorHandler;
import gloomlib.script.core.CompilationContext;
import gloomlib.script.core.ScriptIR.IRType;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;

/**
 * 运行时变量索引的 List 取值器。
 * <p>
 * 与 {@link ListAccessor}（编译期常量索引）互补，支持 {@code items[{idx}]} 语法。
 * 字节码序列：ILOAD idxSlot → INVOKEINTERFACE List.get(int) → CHECKCAST E
 * <p>
 * varName 引用的变量必须为 INT 类型。
 */
public record DynamicListAccessor(
        String varName,
        TypeToken<?> returnType
) implements PropertyAccessor {

    @Override
    public void emitLoad(MethodVisitor mv, CompilationContext ctx) {
        IRType idxType = ctx.getType(varName);
        if (!idxType.isNumeric()) {
            throw gloomlib.script.api.ScriptCompileException.create(ctx.scriptId(), null,
                    gloomlib.diagnostic.DiagnosticCategory.TYPE,
                    "Dynamic List index variable '" + varName + "' must be numeric (int/long/double), but got: " + idxType);
        }
        int idxSlot = ctx.getSlot(varName);

        // List.get(int) — 索引必须是 int，其他类型截断
        if (idxType == IRType.LONG) {
            mv.visitVarInsn(Opcodes.LLOAD, idxSlot);
            mv.visitInsn(Opcodes.L2I);
        } else if (idxType == IRType.DOUBLE) {
            ScriptErrorHandler.warning(
                    "Dynamic List index variable '" + varName + "' is DOUBLE — will be truncated to int via D2I. "
                            + "Consider using an INT variable for index access.");
            mv.visitVarInsn(Opcodes.DLOAD, idxSlot);
            mv.visitInsn(Opcodes.D2I);
        } else {
            mv.visitVarInsn(Opcodes.ILOAD, idxSlot);
        }

        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get",
                "(I)Ljava/lang/Object;", true);

        if (returnType.getRawType() != Object.class) {
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(returnType.getRawType()));
        }
    }
}
