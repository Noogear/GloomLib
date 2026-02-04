package gloomlib.command.benchmark;

import gloomlib.command.processor.MethodInvoker;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 命令方法调用基准测试。
 * 
 * <p>对比直接调用、反射调用和 MethodInvoker 优化调用的性能。</p>
 * 
 * <p>此测试用于验证 MethodInvoker 相比传统反射的性能提升。</p>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class CommandBenchmark {

    private Method method;
    private MethodInvoker invoker;
    private Object instance;
    private Object[] args;

    @Setup
    public void setup() throws Exception {
        instance = new TestCommand();
        method = TestCommand.class.getMethod("execute", String.class, int.class);
        invoker = MethodInvoker.of(method);
        args = new Object[] { "test", 123 };
    }

    /**
     * 基线：直接方法调用
     */
    @Benchmark
    public int directCall(Blackhole bh) {
        return ((TestCommand) instance).execute("test", 123);
    }

    /**
     * 传统反射调用
     */
    @Benchmark
    public Object reflectionCall(Blackhole bh) throws Exception {
        return method.invoke(instance, args);
    }

    /**
     * MethodInvoker 优化调用
     */
    @Benchmark
    public Object methodInvokerCall(Blackhole bh) throws Throwable {
        return invoker.invoke(instance, args);
    }

    /**
     * 测试命令类
     */
    public static class TestCommand {
        public int execute(String arg1, int arg2) {
            return arg1.length() + arg2;
        }
    }
}
