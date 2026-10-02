# 游荡者 (wanderer_qust) 冲突日志

> 记录该角色涉及的所有共享文件修改点，rebase 上游更新时参考此文档排查冲突。

## 基本信息

- 角色 ID: `qust:wanderer_qust`（原名 `qust:wanderer`，已改名避免歧义）
- 翻译键前缀: `wanderer_qust`（使用 `id.getPath()` 不含 `qust` 命名空间）
- 包路径: `org.agmas.noellesroles.role.qust.roles.wanderer`
- 组件 Key: `WandererPlayerComponent.KEY`（注册于 `ModComponents.java`）

---

## 修改的共享文件清单

### 1. ModComponents.java（⭐ 低风险）

**改动**: 添加 WANDERER ComponentKey 声明 + 工厂注册

```java
// 字段声明（约 L124）
public static final ComponentKey<WandererPlayerComponent> WANDERER =
    WandererPlayerComponent.KEY;

// 工厂注册（约 L240）
registry.beginRegistration(Player.class, WANDERER)
    .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
    .end(WandererPlayerComponent::new);
```

**冲突排查**: 上游新增组件时可能在附近插入代码，保留双方即可。

---

### 2. SREPayloadRegister.java（⭐ 低风险）

**改动**: 注册 5 个网络包类型（SoulOutState, GhostVisibility, EnterGhostState, ToggleGhostVisibility, FinalDeath）

**冲突排查**: 上游新增 payload 时在附近插入，保留双方即可。注意包类型 ID 前缀为 `wanderer_qust_`。

---

### 3. MinigameQuestClientNetwork.java（⭐ 低风险）

**改动**: 添加游荡者客户端网络包接收器注册（SoulOutState, GhostVisibility, EnterGhostState）

**冲突排查**: 上游在此文件新增网络接收器时可能相邻，保留双方即可。

---

### 4. KeyBindingMixin.java（⭐⭐ 中风险 — 跳跃键抑制例外）

**改动**: 在 `shouldSuppressKey()` 方法中，跳跃键抑制逻辑处添加例外：当 `WandererClientHandlers.isFreeCamActive()` 为 true 时不抑制跳跃键。

```java
if (this.same(options.keyJump)) {
    // 灵魂出窍自由相机时不抑制跳跃键
    if (WandererClientHandlers.isFreeCamActive()) {
        return false;
    }
    // ... 原有抑制逻辑
}
```

**冲突排查**: 上游如果修改了 `shouldSuppressKey()` 中跳跃键相关逻辑，rebase 时需要保留我们的例外检查。这是最可能产生冲突的文件。

---

### 5. zh_cn.json（⭐ 低风险，追加式）

**改动**: 追加游荡者翻译条目

**冲突排查**:
- 上游如果在附近追加新条目，rebase 时保留双方追加
- 确保 JSON 逗号正确
- 翻译键使用 `wanderer_qust` 前缀

**翻译键清单**:
| 键名 | 用途 |
|------|------|
| `announcement.star.role.wanderer_qust` | 职业名称 |
| `announcement.star.goals.wanderer_qust` | 目标描述 |
| `info.screen.roleid.wanderer_qust` | 详细介绍 |
| `info.screen.roleid.wanderer_qust.simple` | 简短介绍 |
| `skill.qust.wanderer_qust.soul_out` | 技能1名称（灵魂出窍/显形） |
| `skill.qust.wanderer_qust.final_death` | 技能2名称（彻底死亡） |
| `hud.wanderer_qust.soul_out` | HUD: 灵魂出窍倒计时 |
| `hud.wanderer_qust.ghost_visible` | HUD: 幽灵显形状态 |
| `hud.wanderer_qust.ghost_hidden` | HUD: 幽灵隐身状态 |
| `message.wanderer_qust.soul_out_start` | 消息: 灵魂出窍开始 |
| `message.wanderer_qust.soul_out_active` | 消息: 灵魂出窍进行中 |
| `message.wanderer_qust.ghost_visible` | 消息: 幽灵显形 |
| `message.wanderer_qust.ghost_hidden` | 消息: 幽灵隐身 |
| `message.wanderer_qust.final_death` | 消息: 彻底死亡 |

---

## 独立文件（不与其他模块共享）

以下文件为游荡者角色独有，rebase 时不太可能产生冲突：

| 文件 | 用途 |
|------|------|
| `WandererRole.java` | 角色定义（onDeath 隐身平民化、onPickUpItem 禁止拾取、onUseGun/onUseKnife/onUseDerringer/leftClickEntity 死后禁止击杀） |
| `WandererPlayerComponent.java` | 玩家组件（灵魂出窍/隐身平民状态/隐身显形/商店管理/serverTick 兜底自愈） |
| `WandererPayload.java` | 网络包定义（5 种包类型） |
| `WandererHud.java` | 客户端 HUD 渲染 |
| `WandererClientHandlers.java` | 客户端自由相机处理 |

---

## 与幽灵状态解耦后新增的共享文件触点（⭐⭐ 中风险）

> 2026-10-03 起，游荡者**不再**使用 `GhostStateComponent` 与 `ModEffects.GHOST_STATE`，
> 改为 `WandererPlayerComponent` + 原版 `invisible` 标记。以下三处共享文件的判定已相应改为
> 直接读取 `WandererPlayerComponent`（`isGhost() && !isFinalDeath()`），rebase 时需保留：

### A. GameUtils.isPlayerEliminated（io/wifi，⭐⭐ 中风险）

**改动**: 游荡者「结算视同已死亡」的耦合判定，从 `hasEffect(GHOST_STATE) && GhostStateComponent.isGhost`
改为 `WandererPlayerComponent.isGhost() && !isFinalDeath()`。

**冲突排查**: 上游修改 `isPlayerEliminated` 时需保留这段游荡者分支（否则杀手杀完其他人无法获胜）。

### B. PlayerJoinUtils.isPlayerPositionRight（io/wifi，⭐ 低风险）

**改动**: 重连位置合法性判定，从 `GhostStateComponent.isGhost` 改为 `WandererPlayerComponent.isGhost() && !isFinalDeath()`。

### C. GhostStatePlayerRenderer.java（mixin，⭐ 低风险）

**改动**: 移除针对游荡者的 `isInvisible` / `isInvisibleTo` 分支（游荡者改用原版 invisible 标记，无需 mixin）；
保留仍依赖 `GhostStateComponent` 的其他幽灵使用者（如 ReturnTraveler）分支。

**冲突排查**: 上游若改动该 mixin，注意游荡者分支已删除，不要再引入 `WandererPlayerComponent` 判定。

---

## 潜在冲突风险点

### 中风险

- **KeyBindingMixin.java**: 跳跃键抑制例外是最可能产生冲突的点，上游修改跳跃相关逻辑时需手动合并
- **GameUtils.isPlayerEliminated / PlayerJoinUtils.isPlayerPositionRight**: 游荡者「结算视同死亡」「重连位置合法」耦合点，改为读取 `WandererPlayerComponent`，上游修改这两个方法时需保留分支
- ~~**GhostStateComponent / ModEffects.GHOST_STATE**~~: **2026-10-03 起游荡者已彻底解耦，不再依赖**（隐身改用原版 `invisible` 标记 + `WandererPlayerComponent`）。GHOST_STATE 效果仍被 ReturnTraveler 使用，但已与游荡者无关

### 低风险

- **GameUtils.teleportBackToRoom()**: 依赖上游工具方法，API 变更需同步修改
- **TrainVoicePlugin.addPlayer()**: 依赖上游语音聊天插件，API 变更需同步修改
- **SoundEvents / ParticleTypes**: 原版音效和粒子，极不可能变更

---

## 迁移/修改记录

| 日期 | 修正内容 | 原因 |
|------|---------|------|
| 2026-09-30 | 角色 ID 从 `wanderer` 改为 `wanderer_qust` | 避免与其他 wanderer 角色歧义 |
| 2026-09-30 | 所有翻译键前缀从 `wanderer` 改为 `wanderer_qust` | 跟随角色 ID 变更 |
| 2026-09-30 | 所有网络包 ID 前缀从 `wanderer_` 改为 `wanderer_qust_` | 跟随角色 ID 变更 |
| 2026-09-30 | KeyBindingMixin 添加跳跃键例外 | 灵魂出窍时空格键被游戏按键抑制逻辑拦截，导致无法上升 |
| 2026-09-30 | 灵魂出窍持续时间从 10s 改为 5s | 用户调整 |
| 2026-09-30 | 灵魂出窍冷却从 60s 改为 20s | 用户调整 |
| 2026-09-30 | 添加灵魂出窍时本体冻结逻辑 | 防止本体移动 |
| 2026-09-30 | 组件同步改为每 20tick 一次 | 减少网络负担 |
| 2026-10-03 | 死亡后不再无碰撞/无重力（移除 serverTick 中 isGhost 的 noPhysics/noGravity），改为正常物理可行走的隐身平民 | 用户反馈：死亡后像旁观者/会飞，应为不能飞的普通平民 |
| 2026-10-03 | onDeath 延迟逻辑补充 `GameUtils.releaseRoleFlight` + noPhysics/noGravity 归位 | 清除死亡切旁观残留的飞行能力，确保不能飞 |
| 2026-10-03 | 自动隐身侦测距离由 32 格改为 8 格 | 用户要求：仅 8 格内被看到才 0.8s 自动隐身 |
| 2026-10-03 | 与幽灵状态彻底解耦：移除 GhostStateComponent / GHOST_STATE 效果，隐身改用原版 `invisible` 标记（`WandererPlayerComponent.applyInvisibility`） | 用户反馈：死亡后仍是幽灵状态，要求彻底解耦并单独写隐身/显形逻辑 |
| 2026-10-03 | 死亡切旁观修复：`WandererPlayerComponent.serverTick` 增加兜底自愈——隐身平民被切成旁观时强制拉回冒险模式 | 用户反馈：死亡后仍直接切到旁观模式，未以冒险模式回房间 |
| 2026-10-03 | 死后禁止击杀其他玩家：新增 `onUseGun`/`onUseDerringer`/`onUseKnife` 返回 false、`leftClickEntity` 对玩家返回 CONSUME | 用户要求：死后一次不允许击杀其他玩家 |
| 2026-10-03 | `onPickUpItem` 保持禁止拾取（含枪械），并随解耦后 `isGhost()` 状态更稳定而可靠生效 | 用户要求：不允许捡枪 |
| 2026-10-03 | GhostStatePlayerRenderer mixin 移除游荡者分支；GameUtils.isPlayerEliminated、PlayerJoinUtils 改读 WandererPlayerComponent | 跟随幽灵状态解耦 |
| 2026-10-03 | `serverTick` 兜底自愈加 `isGameActive()` 守卫：游戏已结束（大厅）时中和残留 `isGhost`，强制可见、不改游戏模式 | 游荡者组件键未进 `TMMRoles.COMPONENT_KEYS`（`setComponentKey` 在 `registerRole` 之后链式调用），局末不会自动 `clear()`，`isGhost` 会经 NBT 残留，否则大厅里会强制隐身 |
| 2026-10-03 | **根因修复**：`QUSTRoles.init()` 新增 `TMMRoles.addRoleComponents(QUSTComponentKeys.Keys.WANDERER)`，让 `onStartGame/onEndGame` 自动 `clear()→init()` 复位 `isGhost` | 上一行守卫只处大厅视觉，残留 `isGhost` 会使下一局同玩家为其他角色时 `serverTick` 又重新隐身，且 `GameUtils.isPlayerEliminated` 将非游荡者误判为已淘汰；、`finalizeGame` 会调 `onEndGame`，补登记后根治 |
| 2026-10-03 | `isGameActive()` 由“游戏 ACTIVE 且 有角色”收紧为“游戏 ACTIVE 且 当前角色==QUSTRoles.WANDERER” | 防极端残留下误给非游荡者隐身 |
