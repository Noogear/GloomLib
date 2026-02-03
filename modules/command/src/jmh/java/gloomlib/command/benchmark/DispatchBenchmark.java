package gloomlib.command.benchmark;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import gloomlib.command.annotation.Arg;
import gloomlib.command.annotation.Command;
import gloomlib.command.annotation.Usage;
import gloomlib.command.registry.CommandRegistry;
import gloomlib.command.resolver.ArgumentResolverRegistry;
import gloomlib.command.processor.ProcessorPipeline;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.*;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DispatchBenchmark {

    private CommandDispatcher<CommandSourceStack> dispatcher;
    private CommandSourceStack source;
    private JavaPlugin plugin;
    private CommandRegistry registry;

    @Setup
    public void setup() {
        // Mock environment
        plugin = mock(JavaPlugin.class);
        source = mock(CommandSourceStack.class);
        CommandSender sender = mock(CommandSender.class);
        when(source.getSender()).thenReturn(sender);

        // Setup GloomCommand components
        ArgumentResolverRegistry resolverRegistry = new ArgumentResolverRegistry();
        resolverRegistry.register(String.class, new gloomlib.command.resolver.resolvers.StringResolver());
        resolverRegistry.register(int.class, new gloomlib.command.resolver.resolvers.IntegerResolver());

        ProcessorPipeline pipeline = new ProcessorPipeline();
        registry = new CommandRegistry(plugin, resolverRegistry, pipeline);

        // Setup Brigadier Dispatcher
        dispatcher = new CommandDispatcher<>();

        // Mock Paper Commands to redirect registration to our local dispatcher
        Commands commands = mock(Commands.class);
        // We can't easily mock the internal registration logic of Paper Commands
        // without complex setup
        // But CommandRegistry uses Commands.register() which takes a
        // LiteralArgumentBuilder.
        // We need to intercept that and register to our 'dispatcher' instead.

        // Workaround: We will manually register the command node to our dispatcher
        // by reflecting into CommandRegistry? No, better to refactor Registry to accept
        // Dispatcher?
        // Or simply use the internal builder logic.

        // For this benchmark, we'll verify the internal invocation overhead.
        // But to test "Dispatch", we really want to go through Brigadier.

        // Actually, CommandRegistry.registerCommand(obj, commands)
        // -> builds tree -> commands.register(node, ...)

        // We can mock 'Commands' and capture the node, then register it to our
        // dispatcher.
        doAnswer(invocation -> {
            com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> node = invocation.getArgument(0);
            dispatcher.getRoot().addChild(node);
            return null;
        }).when(commands).register(any(), anyString(), anyList());

        // Register Command
        registry.registerCommand(new BenchCommand(), commands);
    }

    @Benchmark
    public int executeSimple() throws CommandSyntaxException {
        return dispatcher.execute("bench 123", source);
    }

    @Command("bench")
    public static class BenchCommand {

        @Usage
        public void run(CommandSender sender, @Arg int value) {
            // No-op
        }
    }
}
