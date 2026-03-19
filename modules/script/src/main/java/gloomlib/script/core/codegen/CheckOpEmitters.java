package gloomlib.script.core.codegen;

import com.google.common.collect.ImmutableList;
import gloomlib.math.api.MathNode;
import gloomlib.math.core.MathNodeEmitter;
import gloomlib.script.core.CheckOp;
import gloomlib.script.core.CompilationContext;
import gloomlib.script.core.ScriptIR.BaseType;
import gloomlib.script.core.ScriptIR.FlowNode;
import gloomlib.script.core.ScriptIR.IRType;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.EnumMap;

/**
 * 全部 {@link CheckOp} 操作符的字节码发射策略实现 + 注册表。
 * <p>
 * 从 {@code CheckNodeHandler} 中提取的纯字节码生成逻辑，消除 Handler 对具体发射细节的耦合。
 * 新增操作符只需在 {@link #STRATEGIES} 注册对应函数引用，无需修改 Handler 的控制流。
 *
 * @see CheckOp
 */
public final class CheckOpEmitters {

    private static final EnumMap<CheckOp, Strategy> STRATEGIES = new EnumMap<>(CheckOp.class);

    // 共享策略实例（避免方法引用每次创建不同 lambda，便于身份比较和调试）
    private static final Strategy STRING_OP_STRATEGY = CheckOpEmitters::emitStringOp;

    private static final Strategy COMPARISON_STRATEGY = CheckOpEmitters::emitComparison;

    static {
        STRATEGIES.put(CheckOp.NULL, (mv, op, slot, type, node, ctx) -> emitNullCheck(mv, slot));
        STRATEGIES.put(CheckOp.INSTANCEOF, (mv, op, slot, type, node, ctx) -> emitInstanceof(mv, slot, node));
        STRATEGIES.put(CheckOp.CONTAINS, CheckOpEmitters::emitContains);
        STRATEGIES.put(CheckOp.CONTAINS_VALUE, CheckOpEmitters::emitContainsValue);
        STRATEGIES.put(CheckOp.EMPTY, CheckOpEmitters::emitEmpty);
        STRATEGIES.put(CheckOp.STARTS_WITH, STRING_OP_STRATEGY);
        STRATEGIES.put(CheckOp.ENDS_WITH, STRING_OP_STRATEGY);
        STRATEGIES.put(CheckOp.MATCHES, (mv, op, slot, type, node, ctx) -> emitMatches(mv, slot, node));
        STRATEGIES.put(CheckOp.IN, CheckOpEmitters::emitIn);
        STRATEGIES.put(CheckOp.BETWEEN, CheckOpEmitters::emitBetween);
        // 数值/相等操作符共享比较策略
        for (CheckOp cop : new CheckOp[]{CheckOp.EQ, CheckOp.NEQ, CheckOp.GT, CheckOp.GTE, CheckOp.LT, CheckOp.LTE}) {
            STRATEGIES.put(cop, COMPARISON_STRATEGY);
        }
    }
    private CheckOpEmitters() {
    }

    /**
     * 获取指定操作符的发射策略。
     *
     * @throws IllegalStateException 若操作符无注册策略
     */
    public static Strategy forOp(CheckOp op) {
        Strategy s = STRATEGIES.get(op);
        if (s == null) {
            throw new IllegalStateException("No emission strategy registered for: " + op);
        }
        return s;
    }

    private static int emitNullCheck(MethodVisitor mv, int slot) {
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        return CheckOp.NULL.nullJumpInsn(); // 非 null 时跳过 fail
    }


    private static int emitInstanceof(MethodVisitor mv, int slot, FlowNode node) {
        String className = node.<String>getRequiredAttr("value").replace('.', '/');
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        mv.visitTypeInsn(Opcodes.INSTANCEOF, className);
        return Opcodes.IFNE; // instanceof 为 true 时继续
    }


    @SuppressWarnings("unused")
    private static int emitContains(MethodVisitor mv, CheckOp op, int slot, IRType type,
                                    FlowNode node, CompilationContext ctx) {
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        Object value = node.getRequiredAttr("value");

        if (type == IRType.STRING) {
            // String.contains(CharSequence)
            mv.visitLdcInsn(value);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains",
                    "(Ljava/lang/CharSequence;)Z", false);
        } else if (type.base() == gloomlib.script.core.ScriptIR.BaseType.MAP) {
            // Map.containsKey(Object) — 'contains' on Map checks key membership
            ASMUtils.emitLiteral(mv, value);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "containsKey",
                    "(Ljava/lang/Object;)Z", true);
        } else if (type.base() == gloomlib.script.core.ScriptIR.BaseType.COLLECTION) {
            if (type.getToken().getRawType().isArray()) {
                // 数组路径：线性搜索 for(i=0; i<len; i++) if(arr[i].equals(value)) → true
                return emitArrayContains(mv, slot, value, type, ctx);
            }
            // Collection.contains(Object)
            ASMUtils.emitLiteral(mv, value);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);
        } else {
            // fallback: toString().contains()
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Object", "toString",
                    "()Ljava/lang/String;", false);
            mv.visitLdcInsn(value);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "contains",
                    "(Ljava/lang/CharSequence;)Z", false);
        }
        return Opcodes.IFNE; // contains 为 true 时继续
    }


    /**
     * 数组线性搜索：遍历数组元素，逐一与 value 做 equals 比较。
     * <p>
     * 对象数组使用 {@code AALOAD + equals}，基本类型数组使用原生比较指令。
     */
    private static int emitArrayContains(MethodVisitor mv, int slot, Object value,
                                         IRType type, CompilationContext ctx) {
        // 栈顶此时已有 ALOAD slot（由 emitContains 压入），先弹掉——我们需要索引循环
        mv.visitInsn(Opcodes.POP);

        Class<?> component = type.getToken().getRawType().getComponentType();
        Label loopStart = new Label();
        Label trueLabel = new Label();
        Label falseLabel = new Label();
        Label endLabel = new Label();

        // int len = arr.length
        // slot + 1 保证不与数组变量槽冲突（属性下沉路径中 ctx.nextSlot() == slot）
        int lenSlot = Math.max(ctx.nextSlot(), slot + 1);
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitVarInsn(Opcodes.ISTORE, lenSlot);

        // int i = 0
        int iSlot = lenSlot + 1;
        ASMUtils.emitIntConst(mv, 0);
        mv.visitVarInsn(Opcodes.ISTORE, iSlot);

        mv.visitLabel(loopStart);
        mv.visitVarInsn(Opcodes.ILOAD, iSlot);
        mv.visitVarInsn(Opcodes.ILOAD, lenSlot);
        mv.visitJumpInsn(Opcodes.IF_ICMPGE, falseLabel);

        // element = arr[i]
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        mv.visitVarInsn(Opcodes.ILOAD, iSlot);

        if (component == int.class) {
            mv.visitInsn(Opcodes.IALOAD);
            ASMUtils.emitIntConst(mv, ((Number) value).intValue());
            mv.visitJumpInsn(Opcodes.IF_ICMPEQ, trueLabel);
        } else if (component == long.class) {
            mv.visitInsn(Opcodes.LALOAD);
            ASMUtils.emitLongConst(mv, ((Number) value).longValue());
            mv.visitInsn(Opcodes.LCMP);
            mv.visitJumpInsn(Opcodes.IFEQ, trueLabel);
        } else if (component == double.class) {
            mv.visitInsn(Opcodes.DALOAD);
            ASMUtils.emitDoubleConst(mv, ((Number) value).doubleValue());
            mv.visitInsn(Opcodes.DCMPG);
            mv.visitJumpInsn(Opcodes.IFEQ, trueLabel);
        } else {
            // 对象数组：value.equals(arr[i])——将常量值置于接收端，避免 null 元素 NPE
            mv.visitInsn(Opcodes.AALOAD);
            ASMUtils.emitLiteral(mv, value);
            mv.visitInsn(Opcodes.SWAP);
            ASMUtils.emitEquals(mv);
            mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
        }

        // i++
        mv.visitIincInsn(iSlot, 1);
        mv.visitJumpInsn(Opcodes.GOTO, loopStart);

        mv.visitLabel(trueLabel);
        ASMUtils.emitIntConst(mv, 1);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);

        mv.visitLabel(falseLabel);
        ASMUtils.emitIntConst(mv, 0);

        mv.visitLabel(endLabel);
        return Opcodes.IFNE;
    }


    @SuppressWarnings("unused")
    private static int emitContainsValue(MethodVisitor mv, CheckOp op, int slot, IRType type,
                                         FlowNode node, CompilationContext ctx) {
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        Object value = node.getRequiredAttr("value");
        ASMUtils.emitLiteral(mv, value);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map", "containsValue",
                "(Ljava/lang/Object;)Z", true);
        return Opcodes.IFNE;
    }


    @SuppressWarnings("unused")
    private static int emitEmpty(MethodVisitor mv, CheckOp op, int slot, IRType type,
                                 FlowNode node, CompilationContext ctx) {
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        if (type.base() == gloomlib.script.core.ScriptIR.BaseType.MAP) {
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Map",
                    "isEmpty", "()Z", true);
        } else if (type.base() == gloomlib.script.core.ScriptIR.BaseType.COLLECTION) {
            if (type.getToken().getRawType().isArray()) {
                // 数组：ARRAYLENGTH == 0
                mv.visitInsn(Opcodes.ARRAYLENGTH);
                // ARRAYLENGTH == 0 时 isEmpty 为 true，IFEQ 跳（与 IFNE 语义统一）
                return Opcodes.IFEQ;
            }
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Collection",
                    "isEmpty", "()Z", true);
        } else { // STRING
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String",
                    "isEmpty", "()Z", false);
        }
        return Opcodes.IFNE; // isEmpty == true 时条件成立
    }


    @SuppressWarnings("unused")
    private static int emitStringOp(MethodVisitor mv, CheckOp op, int slot, IRType type,
                                    FlowNode node, CompilationContext ctx) {
        String value = node.getRequiredAttr("value");
        mv.visitVarInsn(Opcodes.ALOAD, slot);

        // 单字符优化：startsWith("x") → charAt(0) == 'x'
        if (value.length() == 1 && (op == CheckOp.STARTS_WITH || op == CheckOp.ENDS_WITH)) {
            if (op == CheckOp.STARTS_WITH) {
                ASMUtils.emitIntConst(mv, 0);
            } else {
                // endsWith → length()-1
                mv.visitInsn(Opcodes.DUP);
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "length", "()I", false);
                ASMUtils.emitIntConst(mv, 1);
                mv.visitInsn(Opcodes.ISUB);
            }
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "charAt", "(I)C", false);
            ASMUtils.emitIntConst(mv, value.charAt(0));
            return Opcodes.IF_ICMPEQ; // charAt == target 时继续
        }

        // 通用路径
        mv.visitLdcInsn(value);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", op.javaStringMethodName(),
                "(Ljava/lang/String;)Z", false);
        return Opcodes.IFNE;
    }


    private static int emitMatches(MethodVisitor mv, int slot, FlowNode node) {
        String hoistedField = node.getAttrOrDefault("_hoistedField", null);
        if (hoistedField != null) {
            // 外置常量池路径：invokedynamic → ConstantCallSite，JIT 折叠为常量
            // name 参数不可包含 '/' 等保留字符（JVMS §4.2.2），实际键通过 bsm args 传递
            mv.visitInvokeDynamicInsn("const", "()Ljava/util/regex/Pattern;",
                    BytecodeCompiler.CONST_BOOTSTRAP_HANDLE, hoistedField);
            mv.visitVarInsn(Opcodes.ALOAD, slot);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/regex/Pattern", "matcher",
                    "(Ljava/lang/CharSequence;)Ljava/util/regex/Matcher;", false);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/util/regex/Matcher", "matches", "()Z", false);
            return Opcodes.IFNE;
        }

        // 退化路径：String.matches()
        String pattern = node.getRequiredAttr("value");
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        mv.visitLdcInsn(pattern);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "matches",
                "(Ljava/lang/String;)Z", false);
        return Opcodes.IFNE;
    }


    @SuppressWarnings("unchecked")
    private static int emitIn(MethodVisitor mv, CheckOp op, int slot, IRType type,
                              FlowNode node, CompilationContext ctx) {
        ImmutableList<?> valueList = node.getAttrOrDefault("valueList", null);
        if (valueList == null) valueList = node.getRequiredAttr("value");

        // COLLECTION IN: "does the collection contain any of the listed values?"
        if (type.base() == BaseType.COLLECTION) {
            return emitCollectionIn(mv, slot, (ImmutableList<Object>) valueList);
        }

        if (valueList.size() <= CheckOp.IN_SET_THRESHOLD) {
            // ≤IN_SET_THRESHOLD 项 → 展开为多路比较（避免集合开销）
            return emitInExpanded(mv, slot, (ImmutableList<Object>) valueList, type);
        }

        // >3 项 → Set.of(...).contains(var)
        return emitInSet(mv, slot, (ImmutableList<Object>) valueList, type, node);
    }


    /**
     * COLLECTION IN: 依次调用 {@code Collection.contains(value)}，任一命中即为 true。
     */
    private static int emitCollectionIn(MethodVisitor mv, int slot, ImmutableList<Object> values) {
        Label trueLabel = new Label();
        Label endLabel = new Label();

        for (Object val : values) {
            mv.visitVarInsn(Opcodes.ALOAD, slot);
            ASMUtils.emitLiteral(mv, val);
            mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Collection", "contains",
                    "(Ljava/lang/Object;)Z", true);
            mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
        }

        ASMUtils.emitIntConst(mv, 0);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);

        mv.visitLabel(trueLabel);
        ASMUtils.emitIntConst(mv, 1);

        mv.visitLabel(endLabel);
        return Opcodes.IFNE;
    }


    /**
     * 展开式 in：var==v1 || var==v2 || var==v3
     */
    private static int emitInExpanded(MethodVisitor mv, int slot,
                                      ImmutableList<Object> values, IRType type) {
        Label trueLabel = new Label();
        Label endLabel = new Label();

        for (int i = 0; i < values.size(); i++) {
            Object val = values.get(i);
            if (type == IRType.INT) {
                mv.visitVarInsn(Opcodes.ILOAD, slot);
                ASMUtils.emitIntConst(mv, ((Number) val).intValue());
                mv.visitJumpInsn(Opcodes.IF_ICMPEQ, trueLabel);
            } else if (type == IRType.LONG) {
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                ASMUtils.emitLongConst(mv, ((Number) val).longValue());
                mv.visitInsn(Opcodes.LCMP);
                mv.visitJumpInsn(Opcodes.IFEQ, trueLabel);
            } else if (type.base() == BaseType.ENUM) {
                mv.visitVarInsn(Opcodes.ALOAD, slot);
                ASMUtils.emitEnumName(mv);
                mv.visitLdcInsn(val.toString());
                ASMUtils.emitEquals(mv);
                mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, slot);
                ASMUtils.emitLiteral(mv, val);
                ASMUtils.emitEquals(mv);
                mv.visitJumpInsn(Opcodes.IFNE, trueLabel);
            }
        }

        // 全部不匹配
        ASMUtils.emitIntConst(mv, 0);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);

        mv.visitLabel(trueLabel);
        ASMUtils.emitIntConst(mv, 1);

        mv.visitLabel(endLabel);
        return Opcodes.IFNE; // in 结果为 true 时继续
    }

    /**
     * Set.of() 方式 in：生成不可变集合 + contains。
     */
    private static int emitInSet(MethodVisitor mv, int slot,
                                 ImmutableList<Object> values, IRType type, FlowNode node) {
        String hoistedField = node.getAttrOrDefault("_hoistedField", null);

        if (hoistedField != null) {
            // 外置常量池路径：invokedynamic → ConstantCallSite
            // 提升后的 Set 统一包含 String 类型（STRING_SET），变量侧也需转为 String 匹配
            mv.visitInvokeDynamicInsn("const", "()Ljava/util/Set;",
                    BytecodeCompiler.CONST_BOOTSTRAP_HANDLE, hoistedField);
            if (type == IRType.INT) {
                mv.visitVarInsn(Opcodes.ILOAD, slot);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf",
                        "(I)Ljava/lang/String;", false);
            } else if (type == IRType.LONG) {
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/String", "valueOf",
                        "(J)Ljava/lang/String;", false);
            } else if (type.base() == BaseType.ENUM) {
                mv.visitVarInsn(Opcodes.ALOAD, slot);
                ASMUtils.emitEnumName(mv);
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, slot);
            }
        } else {
            // 退化路径：动态创建 Set.of()
            int count = values.size();
            ASMUtils.emitIntConst(mv, count);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
            for (int i = 0; i < count; i++) {
                mv.visitInsn(Opcodes.DUP);
                ASMUtils.emitIntConst(mv, i);
                Object val = values.get(i);
                // 归一化数值类型，防止 Long.equals(Integer) == false
                if (type == IRType.LONG && val instanceof Number n) {
                    ASMUtils.emitLiteral(mv, n.longValue());
                } else {
                    ASMUtils.emitLiteral(mv, val);
                }
                mv.visitInsn(Opcodes.AASTORE);
            }
            mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/util/Set", "of",
                    "([Ljava/lang/Object;)Ljava/util/Set;", true);

            // set.contains(var) — 动态路径使用类型安全装箱
            if (type == IRType.INT) {
                mv.visitVarInsn(Opcodes.ILOAD, slot);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf",
                        "(I)Ljava/lang/Integer;", false);
            } else if (type == IRType.LONG) {
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf",
                        "(J)Ljava/lang/Long;", false);
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, slot);
            }
        }
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/Set", "contains",
                "(Ljava/lang/Object;)Z", true);

        return Opcodes.IFNE;
    }

    @SuppressWarnings("unused")
    private static int emitBetween(MethodVisitor mv, CheckOp op, int slot, IRType type,
                                   FlowNode node, CompilationContext ctx) {
        String hoistedField = node.getAttrOrDefault("_hoistedField", null);
        boolean useArray = hoistedField != null && (type == IRType.DOUBLE || type == IRType.LONG);

        ImmutableList<?> range = node.getAttrOrDefault("valueList", null);
        if (range == null) range = node.getRequiredAttr("value");

        Label failLabel = new Label();
        Label endLabel = new Label();

        if (type == IRType.INT) {
            int iLow = ((Number) range.get(0)).intValue();
            int iHigh = ((Number) range.get(1)).intValue();
            // var >= low
            mv.visitVarInsn(Opcodes.ILOAD, slot);
            ASMUtils.emitIntConst(mv, iLow);
            mv.visitJumpInsn(Opcodes.IF_ICMPLT, failLabel);
            // var <= high
            mv.visitVarInsn(Opcodes.ILOAD, slot);
            ASMUtils.emitIntConst(mv, iHigh);
            mv.visitJumpInsn(Opcodes.IF_ICMPGT, failLabel);
        } else if (type == IRType.LONG) {
            if (useArray) {
                // 外置常量池路径：提升器统一生成 double[]，运行时通过 D2L 转换
                // var >= arr[0]
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                mv.visitInvokeDynamicInsn("const", "()[D",
                        BytecodeCompiler.CONST_BOOTSTRAP_HANDLE, hoistedField);
                ASMUtils.emitIntConst(mv, 0);
                mv.visitInsn(Opcodes.DALOAD);
                mv.visitInsn(Opcodes.D2L);
                mv.visitInsn(Opcodes.LCMP);
                mv.visitJumpInsn(Opcodes.IFLT, failLabel);
                // var <= arr[1]
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                mv.visitInvokeDynamicInsn("const", "()[D",
                        BytecodeCompiler.CONST_BOOTSTRAP_HANDLE, hoistedField);
                ASMUtils.emitIntConst(mv, 1);
                mv.visitInsn(Opcodes.DALOAD);
                mv.visitInsn(Opcodes.D2L);
                mv.visitInsn(Opcodes.LCMP);
                mv.visitJumpInsn(Opcodes.IFGT, failLabel);
            } else {
                long lLow = ((Number) range.get(0)).longValue();
                long lHigh = ((Number) range.get(1)).longValue();
                // var >= low
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                ASMUtils.emitLongConst(mv, lLow);
                mv.visitInsn(Opcodes.LCMP);
                mv.visitJumpInsn(Opcodes.IFLT, failLabel);
                // var <= high
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                ASMUtils.emitLongConst(mv, lHigh);
                mv.visitInsn(Opcodes.LCMP);
                mv.visitJumpInsn(Opcodes.IFGT, failLabel);
            }
        } else {
            // double
            if (useArray) {
                // 从 RANGE_x 数组中获取边界值
                // var >= arr[0]
                mv.visitVarInsn(Opcodes.DLOAD, slot);
                mv.visitInvokeDynamicInsn("const", "()[D",
                        BytecodeCompiler.CONST_BOOTSTRAP_HANDLE, hoistedField);
                ASMUtils.emitIntConst(mv, 0);
                mv.visitInsn(Opcodes.DALOAD);
                mv.visitInsn(Opcodes.DCMPG);
                mv.visitJumpInsn(Opcodes.IFLT, failLabel);

                // var <= arr[1]
                mv.visitVarInsn(Opcodes.DLOAD, slot);
                mv.visitInvokeDynamicInsn("const", "()[D",
                        BytecodeCompiler.CONST_BOOTSTRAP_HANDLE, hoistedField);
                ASMUtils.emitIntConst(mv, 1);
                mv.visitInsn(Opcodes.DALOAD);
                mv.visitInsn(Opcodes.DCMPL);
                mv.visitJumpInsn(Opcodes.IFGT, failLabel);
            } else {
                double dLow = ((Number) range.get(0)).doubleValue();
                double dHigh = ((Number) range.get(1)).doubleValue();
                // 退化路径：常量拼接
                mv.visitVarInsn(Opcodes.DLOAD, slot);
                ASMUtils.emitDoubleConst(mv, dLow);
                mv.visitInsn(Opcodes.DCMPG);
                mv.visitJumpInsn(Opcodes.IFLT, failLabel);

                mv.visitVarInsn(Opcodes.DLOAD, slot);
                ASMUtils.emitDoubleConst(mv, dHigh);
                mv.visitInsn(Opcodes.DCMPL);
                mv.visitJumpInsn(Opcodes.IFGT, failLabel);
            }
        }

        // 在范围内
        ASMUtils.emitIntConst(mv, 1);
        mv.visitJumpInsn(Opcodes.GOTO, endLabel);

        mv.visitLabel(failLabel);
        ASMUtils.emitIntConst(mv, 0);

        mv.visitLabel(endLabel);
        return Opcodes.IFNE; // between 满足时继续
    }


    @SuppressWarnings("unused")
    private static int emitComparison(MethodVisitor mv, CheckOp op, int slot, IRType type,
                                      FlowNode node, CompilationContext ctx) {
        if (type == IRType.BOOLEAN) {
            return emitBooleanComparison(mv, slot, node, op);
        } else if (type == IRType.DOUBLE) {
            return emitDoubleComparison(mv, slot, node, op, ctx);
        } else if (type == IRType.INT) {
            return emitIntComparison(mv, slot, node, op);
        } else if (type == IRType.LONG) {
            return emitLongComparison(mv, slot, node, op);
        } else if (type.base() == BaseType.ENUM) {
            emitEnumEquals(mv, slot, node);
            return op.afterEqualsJump();
        } else {
            return emitObjectComparison(mv, slot, node, op);
        }
    }


    /**
     * boolean：无 value → 直接检测；有 value → 调整。
     * NEQ 通过 {@link ASMUtils#invertJump} 翻转跳转方向。
     */
    private static int emitBooleanComparison(MethodVisitor mv, int slot, FlowNode node, CheckOp op) {
        mv.visitVarInsn(Opcodes.ILOAD, slot);
        Object value = node.getAttrOrDefault("value", null);
        // 基准跳转：IFNE 表示"为 true 则条件成立"（EQ 语义）；value=false 时取 IFEQ。
        int baseJump = (value == null || Boolean.TRUE.equals(value))
                ? Opcodes.IFNE
                : Opcodes.IFEQ;
        // NEQ 相对于 EQ 翻转跳转方向。
        return (op == CheckOp.NEQ) ? ASMUtils.invertJump(baseJump) : baseJump;
    }

    /**
     * double 零装箱比较
     */
    private static int emitDoubleComparison(MethodVisitor mv, int slot, FlowNode node, CheckOp op,
                                            CompilationContext ctx) {
        mv.visitVarInsn(Opcodes.DLOAD, slot);
        return emitDoubleComparisonOnStack(mv, node, op, ctx);
    }

    /**
     * 栈顶已有 left double 时，发射 right 值 + DCMPG + 返回跳转 opcode。
     * <p>
     * 供 Comparison 策略内部和 {@code CheckNodeHandler.emitCondition} 的 conditionAction/MATH 路径共用。
     *
     * @param mv   MethodVisitor
     * @param node CHECK 节点（可能含 {@code valueNode} 属性）
     * @param op   比较操作符（决定 DCMPG 后的跳转方向）
     * @param ctx  编译上下文
     * @return 条件成立时的 JVM 跳转 opcode
     */
    public static int emitDoubleComparisonOnStack(MethodVisitor mv, FlowNode node, CheckOp op,
                                                  CompilationContext ctx) {
        MathNode valueNode = node.getAttrOrDefault("valueNode", null);
        if (valueNode != null) {
            MathNodeEmitter.emit(valueNode, mv, ctx.toVariableEmitter());
        } else {
            ASMUtils.emitDoubleConst(mv, node.numericValue());
        }
        mv.visitInsn(Opcodes.DCMPG);
        return op.cmpJump();
    }

    /**
     * int 零装箱比较
     */
    private static int emitIntComparison(MethodVisitor mv, int slot, FlowNode node, CheckOp op) {
        mv.visitVarInsn(Opcodes.ILOAD, slot);
        ASMUtils.emitIntConst(mv, (int) node.numericValue());
        return op.intCmpJump();
    }

    /**
     * long 比较
     */
    private static int emitLongComparison(MethodVisitor mv, int slot, FlowNode node, CheckOp op) {
        mv.visitVarInsn(Opcodes.LLOAD, slot);
        ASMUtils.emitLongConst(mv, (long) node.numericValue());
        mv.visitInsn(Opcodes.LCMP);
        return op.cmpJump();
    }

    /**
     * 枚举引用比较（Enum.name().equals()，安全可靠）
     */
    private static int emitEnumEquals(MethodVisitor mv, int slot, FlowNode node) {
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        ASMUtils.emitEnumName(mv);
        String enumValue = node.getRequiredAttr("value").toString();
        mv.visitLdcInsn(enumValue);
        ASMUtils.emitEquals(mv);
        return Opcodes.IFNE;
    }

    /**
     * 对象 equals 比较
     */
    private static int emitObjectComparison(MethodVisitor mv, int slot, FlowNode node, CheckOp op) {
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        Object value = node.getAttrOrDefault("value", null);
        ASMUtils.emitLiteral(mv, value);
        ASMUtils.emitEquals(mv);
        return op.afterEqualsJump();
    }

    /**
     * CHECK 操作符的字节码发射策略。
     * <p>
     * 每个 {@link CheckOp} 枚举值对应一个策略实现，负责将该操作符的语义转换为 JVM 字节码。
     * 新增操作符时只需：1）在 {@link CheckOp} 添加枚举值；2）在 {@link CheckOpEmitters} 注册策略。
     */
    @FunctionalInterface
    public interface Strategy {

        /**
         * 发射操作符的条件检查字节码。
         *
         * @param mv   当前方法的 MethodVisitor
         * @param op   操作符枚举值
         * @param slot 被检查变量的本地变量槽位
         * @param type 变量的 IR 类型
         * @param node CHECK FlowNode（含 value、valueList 等属性）
         * @param ctx  编译上下文
         * @return "条件成立时应跳转"的 JVM 条件跳转 opcode
         */
        int emit(MethodVisitor mv, CheckOp op, int slot, IRType type, FlowNode node, CompilationContext ctx);
    }
}
