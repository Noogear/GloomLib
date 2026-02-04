package gloomlib.command.benchmark;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import gloomlib.command.annotation.Arg;
import gloomlib.command.annotation.Command;
import gloomlib.command.annotation.Usage;
import gloomlib.command.registry.CommandRegistry;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import gloomlib.command.resolver.resolvers.IntegerResolver;
import gloomlib.command.resolver.resolvers.StringResolver;
import gloomlib.command.processor.ProcessorPipeline;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

/**
 * 命令调度基准测试。
 * 
 * <p>测试通过 Brigadier 调度器执行命令的性能。</p>
 * 
 * <p>这是最接近实际使用场景的测试，包含完整的：</p>
 * <ul>
 *   <li>命令解析（Brigadier）</li>
 *   <li>参数解析</li>
 *   <li>命令执行</li>
 * </ul>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 10, time = 1)
@Fork(1)
public class DispatchBenchmark {

    private CommandDispatcher<CommandSourceStack> dispatcher;
    private CommandSourceStack source;

    @Setup
    public void setup() {
        // Mock environment
        JavaPlugin plugin = mock(JavaPlugin.class);
        source = mock(CommandSourceStack.class);
        CommandSender sender = mock(CommandSender.class);
        when(source.getSender()).thenReturn(sender);
        when(sender.hasPermission(anyString())).thenReturn(true);

        // Setup GloomCommand components
        ArgumentResolverRegistry resolverRegistry = new ArgumentResolverRegistry();
        resolverRegistry.register(String.class, new StringResolver());
        resolverRegistry.register(int.class, new IntegerResolver());

        ProcessorPipeline pipeline = new ProcessorPipeline();
        CommandRegistry registry = new CommandRegistry(plugin, resolverRegistry, pipeline);

        // Setup Brigadier Dispatcher
        dispatcher = new CommandDispatcher<>();

        // Mock Paper Commands and capture node registration
        Commands commands = mock(Commands.class);
        doAnswer(invocation -> {
            com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> node = invocation.getArgument(0);
            dispatcher.getRoot().addChild(node);
            return null;
        }).when(commands).register(any(), anyString(), anyList());

        // Register test commands
        registry.registerCommand(new SimpleCommand(), commands);
        registry.registerCommand(new CommandWithArgs(), commands);
    }

    /**
     * 测试简单命令（无参数）
     */
    @Benchmark
    public int executeSimpleCommand(Blackhole bh) throws CommandSyntaxException {
        return dispatcher.execute("simple", source);
    }

    /**
     * 测试带参数的命令
     */
    @Benchmark
    public int executeCommandWithArgs(Blackhole bh) throws CommandSyntaxException {
        return dispatcher.execute("withargs test 123", source);
    }

    // ============================================
    // 测试命令类
    // ============================================

    /**
     * 简单命令（无参数）
     */
    @Command("simple")
    public static class SimpleCommand {
        @Usage
        public void run(CommandSender sender) {
            // No-op for benchmark
        }
    }

    /**
     * 带参数的命令
     */
    @Command("withargs")
    public static class CommandWithArgs {
        @Usage
        public void run(CommandSender sender, @Arg String text, @Arg int value) {
            // No-op for benchmark
        }
    }
}
