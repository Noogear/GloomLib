# GloomLib GUI 2.0 快速入门指南

## 新功能快速参考

### 1. 背包链接组件 📦

将玩家背包或箱子槽位直接嵌入 GUI：

```java
import gloomlib.gui.component.builtin.InventoryLinkComponent;

// 链接玩家背包槽位
GloomGuiBuilder.create()
    .title(Component.text("我的背包"))
    .rows(6)
    .structure(
        "xxxxxxxxx",
        "xxxxxxxxx",
        "xxxxxxxxx",
        "ppppppppp",  // p = 玩家背包
        "ppppppppp",
        "ppppppppp"
    )
    .setComponent('x', someComponent)
    // 链接玩家背包的每个槽位
    .setItem(27, new InventoryLinkComponent(player.getInventory(), 0))
    .setItem(28, new InventoryLinkComponent(player.getInventory(), 1))
    // ... 或使用循环
    .build(player)
    .open();

// 只读背包链接（展示用）
new InventoryLinkComponent(chest, 0, false)  // false = 只读
```

### 2. GUI 冻结机制 ❄️

在加载数据时临时禁用所有交互：

```java
GloomGui gui = GloomGuiBuilder.create()
    .title(Component.text("数据加载中..."))
    .rows(3)
    .build(player);

// 初始冻结状态
gui.setFrozen(true);

// 异步加载数据
AsyncState<List<Item>> dataState = AsyncState.of(
    () -> loadDataFromDatabase(),  // 耗时操作
    Collections.emptyList(),        // 加载中的值
    Collections.emptyList(),        // 错误时的值
    player
);

// 加载完成后解冻
dataState.subscribe(data -> {
    if (!dataState.isLoading()) {
        gui.setFrozen(false);
    }
});

gui.open();
```

### 3. 背景物品 🎨

为空槽位设置默认背景：

```java
ItemStack backgroundItem = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
ItemMeta meta = backgroundItem.getItemMeta();
meta.displayName(Component.text(" "));  // 空名称
backgroundItem.setItemMeta(meta);

GloomGui gui = GloomGuiBuilder.create()
    .title(Component.text("商店"))
    .rows(3)
    .structure(
        "....x....",  // . = 空槽位（会显示背景）
        ".x.x.x.x.",  // x = 商品
        "....x...."
    )
    .setComponent('x', itemComponent)
    .build(player);

// 设置背景
gui.setBackground(backgroundItem);
gui.open();
```

### 4. 虚拟线程异步加载 🚀

Java 21 虚拟线程提升异步性能：

```java
import gloomlib.gui.state.AsyncState;

// 旧方式（仍然支持）
AsyncState<List<Item>> state1 = AsyncState.ofFuture(
    () -> CompletableFuture.supplyAsync(() -> fetchItems()),
    loadingValue, errorValue, player
);

// 新方式（更简洁，自动使用虚拟线程）
AsyncState<List<Item>> state2 = AsyncState.of(
    () -> {
        // 这段代码会在虚拟线程中执行
        // 可以直接写阻塞代码，不会影响主线程
        return database.query("SELECT * FROM items").getResults();
    },
    loadingValue,
    errorValue,
    player
);

// 响应式订阅
state2.subscribe(items -> {
    System.out.println("加载了 " + items.size() + " 个物品");
});
```

### 5. 增强的交互检测 🖱️

更精确的点击类型判断：

```java
GloomComponent.builder()
    .icon(icon)
    .onClick(ctx -> {
        // 基础检测
        if (ctx.isLeftClick()) { }
        if (ctx.isRightClick()) { }
        if (ctx.isShiftClick()) { }
        
        // 新增高级检测
        if (ctx.isDoubleClick()) {
            // 玩家双击收集相同物品
        }
        if (ctx.isOffhandSwap()) {
            // 玩家按 F 键交换副手
        }
        if (ctx.isMiddleClick()) {
            // 创造模式中键复制
        }
        if (ctx.isNumberKey()) {
            // 数字键 1-9 切换到快捷栏
        }
        
        // 动作类型检测
        if (ctx.isPlaceAction()) {
            // 正在放置物品
        }
        if (ctx.isPickupAction()) {
            // 正在拾取物品
        }
        if (ctx.isMoveToOtherInventory()) {
            // Shift+点击移动到其他背包
        }
        
        // 调试信息
        System.out.println(ctx.getDescription());
    })
    .build();
```

### 6. 响应式状态弱引用 🧠

自动防止内存泄漏：

```java
ReactiveState<Integer> counter = ReactiveState.of(0);

// 旧代码仍然工作，但现在使用弱引用
counter.subscribe(value -> {
    updateDisplay(value);
});

// 新增：查看活跃监听器数量
System.out.println("监听器数量: " + counter.getListenerCount());

// 新增：清除所有监听器
counter.clearListeners();

// 监听器被垃圾回收后会自动清理
// 不再需要手动 unsubscribe（虽然仍然推荐）
```

### 7. 改进的分页组件 📄

脏标志现在正确工作：

```java
ReactiveState<List<Item>> items = ReactiveState.of(initialItems);

PagedComponent<Item> paged = new PagedComponent<>(
    items,
    9,  // 每页 9 个物品
    item -> item.toItemStack(),
    (ctx, item) -> player.sendMessage("点击了: " + item.getName())
);

// 更新数据会自动触发重新渲染
items.set(newItems);  // ✅ 现在会正确更新显示

// 翻页按钮
GloomComponent nextButton = GloomComponent.builder()
    .icon(new ItemStack(Material.ARROW))
    .onClick(ctx -> {
        if (paged.hasNext()) {
            paged.nextPage();
        }
    })
    .build();
```

### 8. Folia 兼容性 🌍

完全支持多线程区域服务器：

```java
// 无需任何代码更改！
// GloomLib 会自动检测并使用正确的调度器：
// - Folia: 使用 EntityScheduler
// - 传统服务器: 使用 BukkitScheduler

GloomGui gui = GloomGuiBuilder.create()
    .title(Component.text("Folia 兼容 GUI"))
    .enableAnimations(5)  // ✅ 在 Folia 上也能正常工作
    .rows(3)
    .build(player);

gui.open();
```

## 完整示例：商店系统

```java
public class ShopGui {
    
    public void openShop(Player player) {
        // 异步加载商品数据
        AsyncState<List<ShopItem>> items = AsyncState.of(
            () -> database.getShopItems(),  // 虚拟线程执行
            Collections.emptyList(),
            Collections.emptyList(),
            player
        );
        
        // 创建分页组件
        PagedComponent<ShopItem> pagedItems = new PagedComponent<>(
            items.map(list -> list),  // 响应式映射
            21,  // 每页 21 个物品
            item -> item.toItemStack(),
            this::handlePurchase
        );
        
        // 构建 GUI
        GloomGui gui = GloomGuiBuilder.create()
            .title(Component.text("商店").color(NamedTextColor.GOLD))
            .rows(6)
            .structure(
                "xxxxxxxxx",
                "xxxxxxxxx",
                "xxxxxxxxx",
                "---------",
                "..<.p.>..",
                "ppppppppp"
            )
            .setComponent('x', pagedItems)  // 商品区域
            .setComponent('<', createPrevButton(pagedItems))
            .setComponent('>', createNextButton(pagedItems))
            .setComponent('p', new InventoryLinkComponent(
                player.getInventory(), 0
            ))
            .build(player);
        
        // 设置背景
        gui.setBackground(createBackground());
        
        // 加载中时冻结
        gui.setFrozen(items.isLoading());
        items.subscribe(data -> {
            gui.setFrozen(items.isLoading());
        });
        
        gui.open();
    }
    
    private void handlePurchase(InteractionContext ctx, ShopItem item) {
        Player player = ctx.player();
        
        if (ctx.isLeftClick()) {
            // 购买 1 个
            buyItem(player, item, 1);
        } else if (ctx.isShiftClick() && ctx.isLeftClick()) {
            // Shift+左键购买 64 个
            buyItem(player, item, 64);
        } else if (ctx.isRightClick()) {
            // 右键查看详情
            showItemDetails(player, item);
        }
    }
    
    private ItemStack createBackground() {
        ItemStack bg = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = bg.getItemMeta();
        meta.displayName(Component.empty());
        bg.setItemMeta(meta);
        return bg;
    }
    
    private GloomComponent createNextButton(PagedComponent<?> paged) {
        return GloomComponent.builder()
            .icon(new ItemStack(Material.ARROW))
            .onClick(ctx -> {
                if (paged.hasNext()) {
                    paged.nextPage();
                } else {
                    ctx.player().playSound(
                        ctx.player().getLocation(),
                        Sound.ENTITY_VILLAGER_NO,
                        1.0f, 1.0f
                    );
                }
            })
            .build();
    }
    
    private GloomComponent createPrevButton(PagedComponent<?> paged) {
        return GloomComponent.builder()
            .icon(new ItemStack(Material.ARROW))
            .onClick(ctx -> {
                if (paged.hasPrev()) {
                    paged.prevPage();
                } else {
                    ctx.player().playSound(
                        ctx.player().getLocation(),
                        Sound.ENTITY_VILLAGER_NO,
                        1.0f, 1.0f
                    );
                }
            })
            .build();
    }
}
```

## 性能最佳实践

### ✅ 推荐做法

1. **使用虚拟线程进行 I/O 操作**
```java
AsyncState.of(() -> {
    // 数据库查询、文件读取等
    return expensiveOperation();
}, loadingValue, errorValue, player);
```

2. **为静态物品使用单例组件**
```java
private static final GloomComponent BACKGROUND = 
    GloomComponent.builder().icon(backgroundItem).build();
```

3. **合理设置 tick 速率**
```java
.enableAnimations(5)  // 每 5 ticks 更新一次（100ms）
                      // 而不是每 tick（50ms）
```

4. **及时清理监听器**
```java
@Override
public void dispose() {
    dataState.clearListeners();
    super.dispose();
}
```

### ❌ 避免的做法

1. **不要在主线程执行阻塞操作**
```java
// ❌ 错误
ReactiveState<Data> state = ReactiveState.of(
    database.query()  // 阻塞主线程！
);

// ✅ 正确
AsyncState<Data> state = AsyncState.of(
    () -> database.query(),  // 在虚拟线程中执行
    loadingValue, errorValue, player
);
```

2. **不要创建过多的 tick 任务**
```java
// ❌ 错误：100 个组件都在 tick
for (int i = 0; i < 100; i++) {
    builder.setComponent(i, tickingComponent);
}

// ✅ 正确：使用响应式状态
ReactiveState<T> state = ...;
GloomComponent component = GloomComponent.builder()
    .onRender(value -> render(value), state)
    .build();
```

3. **不要忘记处理 GUI 关闭**
```java
.onClose(event -> {
    // 清理资源
    dataState.clearListeners();
    cancelPendingTasks();
})
```

## 迁移检查清单

从旧版本升级时检查以下项目：

- [ ] 更新 `AsyncState.ofFuture()` 调用添加 `player` 参数
- [ ] 自定义 `Window` 实现添加 `getViewer()` 和 `isClosed()` 方法
- [ ] 移除对 `InventoryAction.HOTBAR_MOVE_AND_READD` 的引用（已弃用）
- [ ] 检查是否需要使用新的 `InventoryLinkComponent`
- [ ] 考虑使用 `frozen` 状态改善用户体验
- [ ] 为大型 GUI 添加背景物品
- [ ] 升级到 Java 21 以获得虚拟线程性能提升

---

**需要帮助？** 查看 [REFACTORING_SUMMARY.md](REFACTORING_SUMMARY.md) 了解完整的技术细节和参考资料。
