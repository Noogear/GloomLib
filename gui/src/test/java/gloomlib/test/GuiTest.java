package gloomlib.test;

import gloomlib.gui.component.GloomComponent;
import gloomlib.gui.interaction.InteractionContext;
import gloomlib.gui.state.ReactiveState;
import gloomlib.gui.util.GuiItemUtils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.*;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GloomLib GUI 核心功能测试
 */
@DisplayName("GUI核心功能测试")
class GuiTest {

    protected static ServerMock server;
    private Player player;
    private Inventory inventory;

    // 显式构造函数（消除 IDE 警告）
    public GuiTest() {
    }

    @BeforeAll
    static void setUpMockBukkit() {
        server = MockBukkit.mock();
    }

    @AfterAll
    static void tearDownMockBukkit() {
        MockBukkit.unmock();
    }

    @BeforeEach
    void setUp() {
        player = server.addPlayer();
        inventory = server.createInventory(null, 27);
    }

    // ==================== 组件测试 ====================

    @Test
    @DisplayName("静态组件应该正确渲染")
    void staticComponentShouldRender() {
        ItemStack icon = new ItemStack(Material.DIAMOND);
        GloomComponent component = GloomComponent.builder()
                .icon(icon)
                .build();

        ItemStack rendered = component.render(0);
        assertEquals(Material.DIAMOND, rendered.getType());
    }

    @Test
    @DisplayName("响应式组件应该响应状态变化")
    void reactiveComponentShouldRespondToStateChange() {
        ReactiveState<Boolean> state = new ReactiveState<>(false);
        GloomComponent component = GloomComponent.builder()
                .onRender(active -> new ItemStack(active ? Material.GREEN_WOOL : Material.RED_WOOL), state)
                .build();

        assertEquals(Material.RED_WOOL, component.render(0).getType());

        state.set(true);
        assertTrue(component.onTick());
        assertEquals(Material.GREEN_WOOL, component.render(0).getType());
    }

    @Test
    @DisplayName("组件应该支持克隆")
    void componentShouldBeCloneable() {
        GloomComponent original = GloomComponent.builder()
                .icon(new ItemStack(Material.DIAMOND_SWORD))
                .build();

        GloomComponent cloned = original.clone();
        assertNotNull(cloned);
        assertNotSame(original, cloned);
        assertEquals(Material.DIAMOND_SWORD, cloned.render(0).getType());
    }

    // ==================== 交互测试 ====================

    @Test
    @DisplayName("InteractionContext应该正确识别点击类型")
    void interactionContextShouldIdentifyClickTypes() {
        InteractionContext leftClick = new InteractionContext(
                player, ClickType.LEFT, InventoryAction.PICKUP_ALL, 0, null, 0
        );
        assertTrue(leftClick.isLeftClick());
        assertFalse(leftClick.isRightClick());

        InteractionContext rightClick = new InteractionContext(
                player, ClickType.RIGHT, InventoryAction.PICKUP_HALF, 0, null, 0
        );
        assertTrue(rightClick.isRightClick());
        assertFalse(rightClick.isLeftClick());

        InteractionContext shiftClick = new InteractionContext(
                player, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY, 0, null, 0
        );
        assertTrue(shiftClick.isShiftClick());
    }

    // ==================== 背包操作测试 ====================

    @Test
    @DisplayName("物品应该能正确放入背包")
    void itemsShouldBePlacedInInventory() {
        ItemStack item = new ItemStack(Material.STONE, 10);
        inventory.setItem(0, item);

        assertNotNull(inventory.getItem(0));
        assertEquals(Material.STONE, inventory.getItem(0).getType());
        assertEquals(10, inventory.getItem(0).getAmount());
    }

    // ==================== 工具类测试 ====================

    @Test
    @DisplayName("GuiItemUtils应该正确判断空物品")
    void guiItemUtilsShouldDetectEmptyItems() {
        assertTrue(GuiItemUtils.isEmpty(null));
        assertTrue(GuiItemUtils.isEmpty(new ItemStack(Material.AIR)));
        assertFalse(GuiItemUtils.isEmpty(new ItemStack(Material.DIAMOND, 1)));
    }

    @Test
    @DisplayName("GuiItemUtils应该正确判断可堆叠性")
    void guiItemUtilsShouldDetectStackability() {
        ItemStack a = new ItemStack(Material.IRON_INGOT, 10);
        ItemStack b = new ItemStack(Material.IRON_INGOT, 20);
        ItemStack c = new ItemStack(Material.GOLD_INGOT, 10);

        assertTrue(GuiItemUtils.canStackWith(a, b));
        assertFalse(GuiItemUtils.canStackWith(a, c));
        assertFalse(GuiItemUtils.canStackWith(a, null));
    }

    @Test
    @DisplayName("GuiItemUtils应该找到第一个空槽位")
    void guiItemUtilsShouldFindFirstEmptySlot() {
        inventory.setItem(0, new ItemStack(Material.STONE, 64));
        inventory.setItem(1, new ItemStack(Material.DIRT, 32));
        inventory.setItem(3, new ItemStack(Material.GRAVEL, 16));

        int slot = GuiItemUtils.findFirstEmptySlot(inventory, 0, 4);
        assertEquals(2, slot);
    }

    @Test
    @DisplayName("GuiItemUtils应该找到可堆叠槽位")
    void guiItemUtilsShouldFindStackableSlot() {
        inventory.setItem(0, new ItemStack(Material.APPLE, 32));
        inventory.setItem(1, new ItemStack(Material.GOLDEN_APPLE, 64)); // 满
        inventory.setItem(2, new ItemStack(Material.APPLE, 50));

        ItemStack item = new ItemStack(Material.APPLE, 10);
        int slot = GuiItemUtils.findFirstStackableSlot(inventory, item, 0, 4);
        assertEquals(0, slot); // 第一个可堆叠的槽位
    }

    // ==================== 状态管理测试 ====================

    @Test
    @DisplayName("ReactiveState应该正确存储和更新值")
    void reactiveStateShouldStoreAndUpdateValue() {
        ReactiveState<Integer> state = new ReactiveState<>(0);
        assertEquals(0, state.get());

        state.set(10);
        assertEquals(10, state.get());
    }

    @Test
    @DisplayName("ReactiveState应该能处理不同类型")
    void reactiveStateShouldHandleDifferentTypes() {
        ReactiveState<String> stringState = new ReactiveState<>("test");
        assertEquals("test", stringState.get());

        ReactiveState<Boolean> boolState = new ReactiveState<>(true);
        assertTrue(boolState.get());

        boolState.set(false);
        assertFalse(boolState.get());
    }

    // ==================== 性能测试 ====================

    @Test
    @DisplayName("大量组件渲染应该在合理时间内完成")
    void massiveRenderingShouldCompleteInTime() {
        GloomComponent[] components = new GloomComponent[1000];
        for (int i = 0; i < 1000; i++) {
            components[i] = GloomComponent.builder()
                    .icon(new ItemStack(Material.STONE))
                    .build();
        }

        long start = System.currentTimeMillis();
        for (GloomComponent component : components) {
            component.render(0);
        }
        long duration = System.currentTimeMillis() - start;

        assertTrue(duration < 1000, "1000个组件渲染应该在1秒内完成");
    }

    @Test
    @DisplayName("响应式组件缓存应该提高性能")
    void reactiveComponentCachingShouldImprovePerformance() {
        ReactiveState<Integer> state = new ReactiveState<>(0);
        int[] renderCount = {0};

        GloomComponent component = GloomComponent.builder()
                .onRender(val -> {
                    renderCount[0]++;
                    return new ItemStack(Material.STONE, Math.max(1, val));
                }, state)
                .build();

        // 多次渲染但状态未变
        for (int i = 0; i < 100; i++) {
            component.render(0);
        }

        assertEquals(1, renderCount[0], "缓存生效，应该只渲染一次");
    }
}
