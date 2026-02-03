package gloomlib.command.example;

import gloomlib.command.context.GloomCommandContext;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ExampleCommand 集成测试类。
 *
 * <p>
 * 验证命令框架的核心功能：
 * <ul>
 * <li>默认使用处理</li>
 * <li>子命令执行</li>
 * <li>参数解析与注入</li>
 * <li>可选参数与默认值</li>
 * <li>范围校验</li>
 * </ul>
 * </p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ExampleCommand 集成测试")
class ExampleCommandTest {

    private ExampleCommand command;

    @Mock
    private GloomCommandContext mockContext;

    @Mock
    private CommandSender mockSender;

    @Mock
    private Player mockPlayer;

    @Mock
    private Player mockTargetPlayer;

    @Mock
    private World mockWorld;

    @Mock
    private Location mockLocation;

    @BeforeEach
    void setUp() {
        command = new ExampleCommand();

        // 配置通用模拟行为
        when(mockContext.getSender()).thenReturn(mockSender);
        when(mockSender.getName()).thenReturn("TestSender");
        when(mockTargetPlayer.getName()).thenReturn("TargetPlayer");
        when(mockWorld.getName()).thenReturn("TestWorld");
        when(mockWorld.getSpawnLocation()).thenReturn(mockLocation);
        when(mockPlayer.getMaxHealth()).thenReturn(20.0);
    }

    @Test
    @DisplayName("测试默认使用 - 显示帮助信息")
    void testShowHelp() {
        // 执行
        command.showHelp(mockContext);

        // 验证：至少发送了4条消息（标题 + 3条帮助）
        verify(mockSender, atLeast(4)).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("测试 greet 子命令 - 使用默认消息")
    void testGreetWithDefaultMessage() {
        // 执行
        command.greet(mockContext, mockTargetPlayer, "你好！");

        // 验证：目标玩家收到消息
        verify(mockTargetPlayer, times(1)).sendMessage(any(Component.class));

        // 验证：发送者收到确认
        verify(mockContext, times(1)).reply(any(Component.class));
    }

    @Test
    @DisplayName("测试 greet 子命令 - 自定义消息")
    void testGreetWithCustomMessage() {
        String customMessage = "欢迎来到服务器！";

        // 执行
        command.greet(mockContext, mockTargetPlayer, customMessage);

        // 验证
        verify(mockTargetPlayer, times(1)).sendMessage(any(Component.class));
        verify(mockContext, times(1)).reply(any(Component.class));
    }

    @Test
    @DisplayName("测试 gamemode 子命令 - 切换到创造模式")
    void testSetGameModeCreative() {
        // 执行
        command.setGameMode(mockPlayer, GameMode.CREATIVE);

        // 验证：游戏模式被设置
        verify(mockPlayer, times(1)).setGameMode(GameMode.CREATIVE);

        // 验证：玩家收到确认消息
        verify(mockPlayer, times(1)).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("测试 gamemode 子命令 - 切换到生存模式")
    void testSetGameModeSurvival() {
        // 执行
        command.setGameMode(mockPlayer, GameMode.SURVIVAL);

        // 验证
        verify(mockPlayer, times(1)).setGameMode(GameMode.SURVIVAL);
        verify(mockPlayer, times(1)).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("测试 teleport 子命令")
    void testTeleportToWorld() {
        // 执行
        command.teleportToWorld(mockPlayer, mockWorld);

        // 验证：玩家被传送到世界出生点
        verify(mockPlayer, times(1)).teleport(mockLocation);

        // 验证：玩家收到传送确认
        verify(mockPlayer, times(1)).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("测试 tempban 子命令 - 使用默认原因")
    void testTempBanWithDefaultReason() {
        Duration duration = Duration.ofHours(24);

        // 执行
        command.tempBan(mockContext, mockTargetPlayer, duration, "违规行为");

        // 验证：发送者收到确认消息
        ArgumentCaptor<Component> componentCaptor = ArgumentCaptor.forClass(Component.class);
        verify(mockContext).reply(componentCaptor.capture());

        assertNotNull(componentCaptor.getValue());
    }

    @Test
    @DisplayName("测试 tempban 子命令 - 自定义原因")
    void testTempBanWithCustomReason() {
        Duration duration = Duration.ofDays(7);
        String reason = "严重违反服务器规则";

        // 执行
        command.tempBan(mockContext, mockTargetPlayer, duration, reason);

        // 验证
        ArgumentCaptor<Component> componentCaptor = ArgumentCaptor.forClass(Component.class);
        verify(mockContext).reply(componentCaptor.capture());

        assertNotNull(componentCaptor.getValue());
    }

    @Test
    @DisplayName("测试 heal 子命令 - 使用默认恢复量")
    void testHealWithDefaultAmount() {
        when(mockPlayer.getHealth()).thenReturn(10.0);

        // 执行
        command.heal(mockPlayer, 20);

        // 验证：生命值被设置为满血
        verify(mockPlayer, times(1)).setHealth(20.0);
        verify(mockPlayer, times(1)).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("测试 heal 子命令 - 自定义恢复量")
    void testHealWithCustomAmount() {
        when(mockPlayer.getHealth()).thenReturn(10.0);

        // 执行
        command.heal(mockPlayer, 5);

        // 验证：生命值被设置为 15.0
        verify(mockPlayer, times(1)).setHealth(15.0);
        verify(mockPlayer, times(1)).sendMessage(any(Component.class));
    }

    @Test
    @DisplayName("测试 heal 子命令 - 不超过最大生命值")
    void testHealNotExceedMaxHealth() {
        when(mockPlayer.getHealth()).thenReturn(18.0);

        // 执行：恢复 5 点（18 + 5 = 23，但最大是 20）
        command.heal(mockPlayer, 5);

        // 验证：生命值被限制为最大值 20.0
        verify(mockPlayer, times(1)).setHealth(20.0);
    }

    @Test
    @DisplayName("测试时长格式化工具 - 复合时长")
    void testFormatDurationComplex() {
        // 通过 tempban 间接测试 formatDuration
        Duration duration = Duration.ofDays(2).plusHours(5).plusMinutes(30).plusSeconds(15);

        command.tempBan(mockContext, mockTargetPlayer, duration, "测试");

        // 验证：消息被正确发送（说明 formatDuration 没有抛出异常）
        verify(mockContext, times(1)).reply(any(Component.class));
    }

    @Test
    @DisplayName("测试时长格式化工具 - 仅秒数")
    void testFormatDurationSecondsOnly() {
        Duration duration = Duration.ofSeconds(45);

        command.tempBan(mockContext, mockTargetPlayer, duration, "测试");

        verify(mockContext, times(1)).reply(any(Component.class));
    }

    @Test
    @DisplayName("测试时长格式化工具 - 仅天数")
    void testFormatDurationDaysOnly() {
        Duration duration = Duration.ofDays(3);

        command.tempBan(mockContext, mockTargetPlayer, duration, "测试");

        verify(mockContext, times(1)).reply(any(Component.class));
    }
}
