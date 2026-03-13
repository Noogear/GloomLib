package gloomlib.script.core.parser.accessor;

import com.google.common.reflect.TypeToken;
import gloomlib.script.core.CompilationContext;
import org.objectweb.asm.MethodVisitor;

/**
 * 属性存取器接口。
 * 代表从某一级数据源对象中提取下一级数据的操作抽象。
 */
public interface PropertyAccessor {

    /**
     * @return 当前取值操作返回的数据的确切类型（带泛型信息）
     */
    TypeToken<?> returnType();

    /**
     * 在 ASM 字节码生成期间，向栈上写入提取该数据的具体指令。
     * <p>
     * 调用此方法前，栈顶必须已经压入其宿主对象（Owner）。
     * 调用结束后，栈顶将变为提取到的返回值对象。
     * <p>
     * 静态 Accessor（key/index 为编译期常量）忽略 ctx；
     * 动态 Accessor（key/index 为运行时变量）通过 ctx 查询槽位。
     *
     * @param mv  方法访问器
     * @param ctx 编译上下文
     */
    void emitLoad(MethodVisitor mv, CompilationContext ctx);
}
