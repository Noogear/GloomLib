package gloomlib.command.benchmark;

import gloomlib.command.processor.MethodInvoker;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
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

    @Benchmark
    public void directCall(Blackhole bh) {
        ((TestCommand) instance).execute("test", 123);
    }

    @Benchmark
    public void reflectionCall(Blackhole bh) throws Exception {
        method.invoke(instance, args);
    }

    @Benchmark
    public void methodInvokerCall(Blackhole bh) throws Throwable {
        invoker.invoke(instance, args);
    }

    public static class TestCommand {
        public int execute(String arg1, int arg2) {
            return arg1.length() + arg2;
        }
    }
}
