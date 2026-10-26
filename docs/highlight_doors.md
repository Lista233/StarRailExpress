# 门高亮指令使用说明（`/tmm:highlightDoors`）

用于地图制作和排查：在指定区域内找出住宿门，并把每扇门的坐标、开门条件显示在聊天栏，同时在客户端画出红色/绿色线框。

本指令**只做可视化**：不锁门、不发钥匙、不写任务点，也不会影响其他玩家。

---

## 一、权限与执行者

| 项目 | 说明 |
| --- | --- |
| 权限等级 | `2`（OP） |
| 执行者 | 必须是玩家；控制台 / 命令方块执行会提示 `只有玩家可以执行此指令` |
| 可见范围 | 高亮与聊天结果只发给执行指令的玩家本人 |

---

## 二、指令结构

```
/tmm:highlightDoors <x1> <y1> <z1> <x2> <y2> <z2> [room|all]
/tmm:highlightDoors off
/tmm:highlightDoors
```

| 参数 | 说明 |
| --- | --- |
| `<x1> <y1> <z1>` | 区域第一个角点，支持 `~` 相对坐标 |
| `<x2> <y2> <z2>` | 区域第二个角点，支持 `~` 相对坐标 |
| `room` | 可选，默认模式：只高亮住宿用小木门（不含玻璃门） |
| `all` | 可选：高亮所有小门类方块（含玻璃门、列车门、自定义门） |
| `off` | 关闭当前高亮 |
| 无参数 | 等同于 `off` |

两个角点的先后顺序无所谓，服务端会自动取最小/最大坐标组成区域。

---

## 三、扫描模式

| 模式 | 包含的方块 |
| --- | --- |
| `room`（默认） | 只含住宿用小木门：`trainmurdermystery:small_wood_door`（**不含玻璃门**） |
| `all` | 所有继承 `SmallDoorBlock` 的小门：小木门、小玻璃门（`trainmurdermystery:small_glass_door`）、列车门（如 `trainmurdermystery:anthracite_steel_door`）、自定义门（如 `starrailexpress:small_paper_door`、`starrailexpress:scp_door`）、上翻门/飞机门等 |

注意：

- 只统计双格门的**下半部分**，同一扇门不会重复出现。
- `all` 也会包含玻璃门、列车门、装饰门等功能性/非住宿门，只想看住宿门时不要加 `all`。

---

## 四、颜色与开锁条件

| 显示 | 颜色 | 判定条件 |
| --- | --- | --- |
| 需要开锁器 | 红色 `#FF0000` | `keyName` 去掉 `locked:` / `alarmed:` / `reinforced:` 前缀后非空 |
| 无需开锁器 | 绿色 `#00FF00` | `keyName` 为空，空手即可打开 |

说明：

- `keyName` 存放的是房间名，例如 `room_101`；锁门、警报、加固状态会以 `locked:room_101` 这样的前缀叠加，因此判断前会先循环剥离这些前缀。
- `keyName` 非空时，开门需要匹配房间的钥匙或任意开锁器（`lockpick`、`master_key` 等）；为空时没有钥匙也能直接开，因此分别对应红色与绿色。
- 已加载的区块每帧都会重新读取方块实体，因此扫描之后门被绑定或解锁，颜色会**实时变化**。
- 未加载的区块无法读取方块实体，会使用服务端扫描时的快照和 1×2 的门框兜底显示（颜色可能停留在扫描时的状态）。

---

## 五、聊天输出与传送

示例：

```
[门高亮] 区域 -12, 60, 34 ~ 8, 72, 54（住宿小木门）：共 6 扇门，需开锁器 4（红），无需开锁器 2（绿）
[101, 64, 40] 需开锁器: room_101
[103, 64, 40] 无需开锁器
...
```

- 门按与执行者的距离从近到远排序。
- 坐标带下划线，**点击即可传送**到门上方：`/tp @s <x+0.5> <y+2> <z+0.5>`，避免落进门方块的碰撞箱。
- 把鼠标悬停在坐标上会显示：传送提示、方块 ID、开锁条件。
- 模式、区域、总数和需/无需开锁器数量都在标题行中。

---

## 六、限制

| 项目 | 限制 |
| --- | --- |
| 区域体积 | 最多 `2,000,000` 个方块；超出会提示缩小区域 |
| 客户端渲染 | 最多显示距离最近的 `1024` 扇门 |
| 聊天列表 | 最多列出前 `50` 条，其余提示“请缩小区域查看” |
| 区块加载 | 只扫描已加载的区块，不会为了扫描强制加载区块 |
| 房间名长度 | 超过 `80` 字符会被截断（防止网络包超限） |

---

## 七、常用示例

```mcfunction
# 检查自己周围 32×16×32 的范围（只找住宿小木门）
/tmm:highlightDoors ~-16 ~-8 ~-16 ~16 ~8 ~16

# 检查当前整节车厢 / 一块地图区域
/tmm:highlightDoors 0 0 0 200 100 200

# 连玻璃门、装饰门、列车门、自定义门一起检查
/tmm:highlightDoors 0 0 0 200 100 200 all

# 关闭高亮
/tmm:highlightDoors off
```

---

## 八、与其他功能的关系

- `/tmm:giveRoomKey <roomName>` 只负责给钥匙；本指令只负责显示门是否已经绑定。
- 需要找食物盘 / 饮料盘 / 自动售货机 / 讲台时用 `/tmm:highlightTasks <from> <to>`：同样用两个角点圈区域，颜色与任务透视一致（食物盘 `#00FF00`、饮料盘 `#EA5858`、讲台 `#FF7F27`、自动售货机 `#FFAEC9`）；`/tmm:highlightTasks all` 可显示全部任务点，`/tmm:highlightTasks all <from> <to>` 则只显示指定区域内的任务点。
- 本指令不会写入 `GameUtils.taskBlocks`、`RoomDoorPositions` 等全局地图数据，因此不会影响任务、地图 UI 或其他玩家。
- 需要真正锁门或批量锁门时，请使用现有的锁门流程；本指令不会改变门的锁定状态。
- 切换维度、重连或重新进入服务器时，客户端会清空高亮，需要重新执行指令。

---

## 九、常见问题

**Q：执行后一扇门都没找到？**

1. 确认区域内有已加载的区块（先靠近目标区域）。
2. 默认只找住宿小木门；玻璃门、自定义门或列车门请追加 `all`。
3. 检查 y 范围是否覆盖了门的下半部分。

**Q：门显示红色，但我没有发过钥匙？**

只要 `keyName` 在剥离前缀后非空就会判定为“需要开锁器”。锁门流程、地图存档或手动设置过 `keyName` 都会造成这种情况；只有把该门的 `keyName` 清空才会变绿。

**Q：为什么传送后站在门上方，而不是门中心？**

传送目标是 `y + 2`，刻意抬高避免卡进门方块。需要精确位置时可在传送后自行调整。

**Q：其他玩家能看到高亮吗？**

不能。服务端只把结果发给执行者本人，客户端也只保存自己收到的这一批门。

**Q：扫描非常慢或提示区域过大？**

按区块逐个检查方块，区域越大耗时越长。请缩小范围，或分多次扫描。

---

## 十、实现位置（开发者）

| 内容 | 文件 |
| --- | --- |
| 指令注册与区域扫描 | `src/main/java/io/wifi/starrailexpress/content/command/HighlightDoorsCommand.java` |
| 服务端 → 客户端数据包 | `src/main/java/io/wifi/starrailexpress/network/packet/DoorHighlightS2CPacket.java` |
| 客户端高亮状态与渲染 | `src/main/java/io/wifi/starrailexpress/client/door/DoorHighlightClient.java` |
| `keyName` 前缀剥离 | `src/main/java/io/wifi/starrailexpress/util/KeyNameUtils.java` |
| 线框渲染基础设施 | `src/main/java/org/agmas/noellesroles/client/TaskBlockOverlayRenderer.java` |
| 客户端接入点 | `src/main/java/org/agmas/noellesroles/client/NoellesrolesClient.java` |
| 文本翻译 | `src/main/resources/assets/starrailexpress/lang/{zh_cn,zh_tw,en_us}.json` |
