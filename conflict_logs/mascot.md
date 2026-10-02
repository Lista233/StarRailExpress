# 吉祥物 (mascot) 冲突日志

> 记录该角色涉及的所有共享文件修改点，rebase 上游更新时参考此文档排查冲突。

## 基本信息

- 角色 ID: `qust:mascot`
- 翻译键前缀: 使用 `id.getPath()` = `mascot`（不含 `qust` 命名空间）
- 包路径: `org.agmas.noellesroles.role.qust.roles.mascot`
- 迁移来源: `D:\Code\McMod\StarRailExpress-master` (源项目已注册)
- 特殊机制:
  - **自定义胜利方式**: 完成任务数达标 → 乘客胜利；发光时被刀/棍杀 → 杀手胜利
  - **本能透视**: 杀手看发光中的吉祥物显示金色高亮（`TARGET_HIGHLIGHT_EVENT`）
  - **周期性发光**: 90秒后开始 2秒发光/12秒冷却 循环
  - **商店**: 防御药剂限购1次

---

## 修改的共享文件清单

### 1. QUSTRoles.java（⭐ 低风险）

**改动**: 添加 MASCOT_ID 和 MASCOT 职业注册

```java
public static final ResourceLocation MASCOT_ID = id("mascot");
public static final SRERole MASCOT = register(new MascotRole(MASCOT_ID, ...));
// .setComponentKey(MascotPlayerComponent.KEY)
// .setDefaultMax(1).setDefaultEnableChance(6000).setDefaultEnableNeededPlayerCount(8)
```

**冲突排查**: 上游新增角色时可能修改同一文件，保留双方即可。

---

### 2. QUSTConfig.java（⭐ 低风险）

**改动**: 添加 5 个 mascot 配置字段

| 字段名 | 类型 | 默认值 | 用途 |
|--------|------|--------|------|
| `mascotRequiredTaskCount` | int | 10 | 完成任务数达标则乘客胜利 |
| `mascotGlowCutDownSeconds` | int | 90 | 游戏开始多久后开始发光(秒) |
| `mascotGlowCutDownCDSeconds` | int | 12 | 发光冷却时间(秒) |
| `mascotGlowDurationSeconds` | int | 2 | 每次发光持续时间(秒) |
| `mascotBuffRange` | double | 15.0 | 完成任务时给周围玩家加buff的范围 |

**冲突排查**: 上游如果修改 QUSTConfig 结构可能影响，但字段为追加式，风险低。

---

### 3. ModComponents.java（⭐ 低风险）

**改动**: 添加 MASCOT ComponentKey 声明 + 工厂注册

```java
// 字段声明
public static final ComponentKey<MascotPlayerComponent> MASCOT =
    org.agmas.noellesroles.role.qust.roles.mascot.MascotPlayerComponent.KEY;

// 工厂注册
registry.beginRegistration(Player.class, MASCOT)
    .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
    .end(MascotPlayerComponent::new);
```

**冲突排查**: 上游新增组件时可能在附近插入代码，保留双方即可。

---

### 4. QUSTClient.java（⭐ 极低风险）

**改动**: 添加两行注册调用

```java
MascotHud.register();       // ← HUD 显示
MascotInstincts.register(); // ← 本能透视
```

**冲突排查**: 上游新增客户端注册时可能在此附近插入，保留双方即可。

---

### 5. zh_cn.json（⭐ 低风险，追加式）

**改动**: 在文件末尾 `}` 前追加吉祥物翻译条目

**冲突排查**:
- 上游如果在文件末尾追加新条目，rebase 时保留双方追加
- 确保 JSON 逗号正确
- 注意：翻译键使用 `id.getPath()`（不含命名空间），所以键名是 `mascot` 而非 `qust.mascot`

**翻译键清单**:
| 键名 | 用途 |
|------|------|
| `announcement.star.role.mascot` | 职业名称 |
| `announcement.star.goals.mascot` | 目标描述 |
| `info.screen.roleid.mascot` | 详细介绍 |
| `info.screen.roleid.mascot.simple` | 简短介绍 |
| `win.mascot` | 乘客胜利文本 |
| `win.mascot_lose` | 杀手胜利文本 |
| `hud.noellesroles.mascot.task_progress` | 任务进度 HUD |
| `hud.noellesroles.mascot.glowing` | 发光中 HUD |
| `hud.noellesroles.mascot.glow_cutdown` | 发光倒计时 HUD |
| `message.mascot.cant_buy` | 限购提示 |

---

## 潜在冲突风险点

### 高风险（如果上游添加同名角色）

如果上游未来添加名为 `mascot` 的角色：
- **翻译键冲突**: `announcement.star.role.mascot` 等键会共用，导致显示混乱
- **解决方案**: 修改我们的角色路径（如改为 `qust_mascot`），或在 QUSTRoles.java 中修改 `id()` 调用

### 中风险

- **`SREGameRoundEndComponent.RoundEndData`**: 是非静态内部类，必须通过 `endComponent.new RoundEndData(...)` 创建。上游如果改为静态类或修改构造函数签名，需要同步修改 `MascotPlayerComponent`
- **`SRERole.onDeath` 签名**: 当前项目有 5 个参数（含 `boolean forceDeath`），上游如果再次修改参数列表，需同步更新 `MascotRole.onDeath` 和 `MascotPlayerComponent.callOnDeath`
- **`GameUtils.stopGame()` / `GameUtils.WinStatus.CUSTOM`**: 自定义胜利依赖这些 API，变更需同步
- **`RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT`**: 本能透视依赖此事件，如果事件签名或触发机制变更需同步修改 `MascotInstincts`

### 低风险

- **`MobEffects.GLOWING`**: 原版药水效果，极不可能变更
- **`SoundEvents.PLAYER_LEVELUP`**: 原版音效，极不可能变更
- **`GameConstants.DeathReasons.KNIFE / BAT`**: 死亡原因常量，较稳定
- **`DynamicShopComponent`**: 商店组件，API 较稳定

---

## 迁移修正记录

| 日期 | 修正内容 | 原因 |
|------|---------|------|
| 2026-09-30 | `TrueFalseAndCustomResult` import 改为 `io.wifi.starrailexpress.util` | 当前项目包路径与源项目不同 |
| 2026-09-30 | `onDeath` 添加 `boolean forceDeath` 参数 | 当前项目 SRERole.onDeath 签名有 5 个参数 |
| 2026-09-30 | `RoundEndData` 改为 `endComponent.new RoundEndData(...)` | 非静态内部类需要封闭实例 |
| 2026-09-30 | `isKillerTeam()` 改为 `SRERole.isKillerTeam()` 实例方法 | `SREGameWorldComponent.isKillerTeam()` 不接受 SRERole 参数 |
