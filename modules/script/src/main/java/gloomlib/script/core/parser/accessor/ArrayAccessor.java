package gloomlib.script.core.parser.accessor;

import com.google.common.reflect.TypeToken;
import gloomlib.script.core.codegen.ASMUtils;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * 基于 Java 数组下标取值的属性提取器。
 * <p>
 * 支持对象数组（{@code AALOAD + CHECKCAST}）和所有基本类型数组
 * （{@code IALOAD / LALOAD / DALOAD} 等）。
 * <p>
 * 整数索引压栈复用 {@link ASMUtils#emitIntConst}，数组加载操作码复用
 * {@link ASMUtils#arrayLoadOpcode}，无重复实现。
 */
public record ArrayAccessor(int index, TypeToken<?> returnType) implements PropertyAccessor {

    @Override
    public void emitLoad(MethodVisitor mv) {
        ASMUtils.emitIntConst(mv, index);

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
