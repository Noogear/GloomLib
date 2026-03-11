package gloomlib.script.core.parser.accessor;

import com.google.common.reflect.TypeToken;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import static org.objectweb.asm.Opcodes.CHECKCAST;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;

/**
 * 基于 {@link java.util.List} 数字索引取值的属性提取器。
 */
public record ListAccessor(int index, TypeToken<?> returnType) implements PropertyAccessor {

    @Override
    public void emitLoad(MethodVisitor mv) {
        gloomlib.script.core.codegen.ASMUtils.emitIntConst(mv, index);

        // List.get(int)
        mv.visitMethodInsn(INVOKEINTERFACE, "java/util/List", "get", "(I)Ljava/lang/Object;", true);

        // 强制泛型转正
        if (returnType.getRawType() != Object.class) {
            mv.visitTypeInsn(CHECKCAST, Type.getInternalName(returnType.getRawType()));
        }
    }
}
