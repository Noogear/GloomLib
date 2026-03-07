package gloomlib.math.core;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * math 模块专用的 ASM 字节码生成辅助工具类。
 * 仅包含 math 模块所需的常量发射和跳转取反功能，不依赖 ScriptIR。
 */
public final class MathASMUtils {

    private MathASMUtils() {
    }

    // ======================== 常量加载工具 ========================

    public static void emitIntConst(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    public static void emitLongConst(MethodVisitor mv, long value) {
        if (value == 0L) {
            mv.visitInsn(Opcodes.LCONST_0);
        } else if (value == 1L) {
            mv.visitInsn(Opcodes.LCONST_1);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    public static void emitDoubleConst(MethodVisitor mv, double value) {
        if (value == 0.0d) {
            mv.visitInsn(Opcodes.DCONST_0);
        } else if (value == 1.0d) {
            mv.visitInsn(Opcodes.DCONST_1);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    public static void emitFloatConst(MethodVisitor mv, float value) {
        if (value == 0.0f) {
            mv.visitInsn(Opcodes.FCONST_0);
        } else if (value == 1.0f) {
            mv.visitInsn(Opcodes.FCONST_1);
        } else if (value == 2.0f) {
            mv.visitInsn(Opcodes.FCONST_2);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    // ======================== 跳转取反映射 ========================

    /**
     * 将 JVM 条件跳转 opcode 翻转为反义 opcode（如 IFEQ → IFNE, IFLT → IFGE）。
     *
     * @throws IllegalArgumentException 当 opcode 不是标准条件跳转时
     */
    public static int invertJump(int opcode) {
        return switch (opcode) {
            case Opcodes.IFEQ -> Opcodes.IFNE;
            case Opcodes.IFNE -> Opcodes.IFEQ;
            case Opcodes.IFLT -> Opcodes.IFGE;
            case Opcodes.IFGE -> Opcodes.IFLT;
            case Opcodes.IFGT -> Opcodes.IFLE;
            case Opcodes.IFLE -> Opcodes.IFGT;
            case Opcodes.IF_ICMPEQ -> Opcodes.IF_ICMPNE;
            case Opcodes.IF_ICMPNE -> Opcodes.IF_ICMPEQ;
            case Opcodes.IF_ICMPLT -> Opcodes.IF_ICMPGE;
            case Opcodes.IF_ICMPGE -> Opcodes.IF_ICMPLT;
            case Opcodes.IF_ICMPGT -> Opcodes.IF_ICMPLE;
            case Opcodes.IF_ICMPLE -> Opcodes.IF_ICMPGT;
            case Opcodes.IF_ACMPEQ -> Opcodes.IF_ACMPNE;
            case Opcodes.IF_ACMPNE -> Opcodes.IF_ACMPEQ;
            case Opcodes.IFNULL -> Opcodes.IFNONNULL;
            case Opcodes.IFNONNULL -> Opcodes.IFNULL;
            default -> throw new IllegalArgumentException("Cannot invert opcode: " + opcode);
        };
    }
}
