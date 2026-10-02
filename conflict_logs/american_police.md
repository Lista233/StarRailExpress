# 美国警察 (american_police / 亦_无悔) 冲突日志

> 记录该角色涉及的所有共享文件修改点，rebase 上游更新时参考此文档排查冲突。

## 基本信息

- 角色 ID: `qust:american_police`
- 翻译键前缀: 使用 `id.getPath()` = `american_police`（不含 `qust` 命名空间）
- 包路径: `org.agmas.noellesroles.role.qust.roles.american_police`
- 迁移来源: 全新创建（非迁移）
- 特殊机制:
  - **义警阵营**: 初始没有枪，完成 `americanPoliceTasksForGun` 个任务后获得左轮手枪（默认3，当前 JSON 配置为1）
  - **技能**: 标记一名玩家，将其皮肤变为 black_man（slim/Alex 模型），冷却60秒，初始1次使用次数
  - **小脑豁免**: 击杀被标记的玩家没有小脑惩罚
  - **击杀奖励**: 击杀杀手或中立角色增加一次技能使用次数
  - **任务奖励**: 每完成7个任务可以获得一次技能使用次数
  - **商店**: 可购买手铐（`americanPoliceHandcuffPrice`，默认150金币，参考义警/警卫）

---

## 修改的共享文件清单

### 1. QUSTRoles.java（⭐ 低风险）

**改动**: 添加 AMERICAN_POLICE_ID、AMERICAN_POLICE_SKILL_ID 和 AMERICAN_POLICE 职业注册

```java
public static final ResourceLocation AMERICAN_POLICE_ID = id("american_police");
public static final ResourceLocation AMERICAN_POLICE_SKILL_ID = id("american_police_skill");

public static SRERole AMERICAN_POLICE = TMMRoles.registerRole(
        new AmericanPoliceRole(AMERICAN_POLICE_ID, ...))
    .setCanSeeCoin(true)
    .setVigilanteTeam(true)
    .setComponentKey(AmericanPolicePlayerComponent.KEY)
    .setTaskReward(QUSTConfig.instance().americanPoliceTasksForGun, 1, TMMItems.REVOLVER.getDefaultInstance())
    .setTaskRewardMessage("message.american_police.revolver_received")
    .setDefaultMax(1)
    .setDefaultEnableChance(5000);
```

**冲突排查**: 上游新增角色时可能修改同一文件，保留双方即可。

---

### 2. QUSTConfig.java（⭐ 低风险）

**改动**: 添加 2 个 american_police 配置字段

| 字段名 | 类型 | 默认值 | 用途 |
|--------|------|--------|------|
| `americanPoliceSkillCooldownSeconds` | int | 60 | 技能冷却时间(秒) |
| `americanPoliceTasksPerCharge` | int | 7 | 每完成多少个任务奖励一次技能使用次数 |
| `americanPoliceTasksForGun` | int | 3 | 完成多少个任务后获得左轮手枪（当前 JSON 设为1） |
| `americanPoliceHandcuffPrice` | int | 150 | 手铐商店价格 |

**冲突排查**: 上游如果修改 QUSTConfig 结构可能影响，但字段为追加式，风险低。

---

### 3. ModComponents.java（⭐ 低风险）

**改动**: 添加 AMERICAN_POLICE ComponentKey 声明 + 工厂注册

```java
// 字段声明
public static final ComponentKey<AmericanPolicePlayerComponent> AMERICAN_POLICE =
    AmericanPolicePlayerComponent.KEY;

// 工厂注册
registry.beginRegistration(Player.class, AMERICAN_POLICE)
    .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
    .end(AmericanPolicePlayerComponent::new);
```

**冲突排查**: 上游新增组件时可能在附近插入代码，保留双方即可。

---

### 4. QUSTHandlers.java（⭐ 中风险）

**改动**: 
- 添加 `registerAmericanPoliceSkills()` 注册技能（带目标选择器）
- 添加 `registerAmericanPoliceEvents()` 注册击杀事件（OnKillPlayerTriggered）

```java
// 技能注册
RoleSkill.register(QUSTRoles.AMERICAN_POLICE,
    RoleSkill.skill(QUSTRoles.AMERICAN_POLICE_SKILL_ID, ...)
        .withTarget()
        .cooldownSeconds(60)
        .charges(1)
        .build()
);

// 击杀事件
OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, deathReason, forceDeath) -> {
    if (!RoleUtils.isPlayerTheJob(killer, QUSTRoles.AMERICAN_POLICE))
        return TrueFalseResult.PASS;
    AmericanPolicePlayerComponent.KEY.get(killer).onKillPlayer(victim);
    return TrueFalseResult.PASS;
});
```

**冲突排查**: 
- 上游如果修改 `OnKillPlayerTriggered` 事件签名需要同步修改
- 上游如果修改 `RoleSkill` 注册方式需要同步修改

---

### 5. QUSTClient.java（⭐ 极低风险）

**改动**: 添加两行注册调用

```java
AmericanPoliceHud.register();
AmericanPoliceClientState.register();
```

**冲突排查**: 上游新增客户端注册时可能在此附近插入，保留双方即可。

---

### 6. zh_cn.json（⭐ 低风险，追加式）

**改动**: 在文件末尾 `}` 前追加美国警察翻译条目

**翻译键清单**:
| 键名 | 用途 |
|------|------|
| `announcement.star.role.american_police` | 职业名称（亦_无悔） |
| `announcement.star.goals.american_police` | 目标描述 |
| `info.screen.roleid.american_police` | 详细介绍 |
| `info.screen.roleid.american_police.simple` | 简短介绍 |
| `skill.noellesroles.american_police.mark` | 技能名称 |
| `message.american_police.revolver_received` | 获得左轮手枪提示 |
| `message.american_police.marked` | 标记成功提示 |
| `message.american_police.you_marked` | 被标记提示 |
| `message.american_police.bonus_charge` | 击杀奖励提示 |
| `message.american_police.no_target` | 无目标提示 |
| `hud.noellesroles.american_police.charges` | 技能次数 HUD |
| `hud.noellesroles.american_police.cooldown` | 冷却时间 HUD |
| `hud.noellesroles.american_police.tasks` | 任务进度 HUD |

---

## 潜在冲突风险点

### 高风险（如果上游添加同名角色）

如果上游未来添加名为 `american_police` 的角色：
- **翻译键冲突**: `announcement.star.role.american_police` 等键会共用，导致显示混乱
- **解决方案**: 修改我们的角色路径（如改为 `qust_american_police`），或在 QUSTRoles.java 中修改 `id()` 调用

### 中风险

- **`MorphApi.morphToTexture()`**: 皮肤变更依赖此 API，如果上游修改签名或行为需要同步修改
- **`OnKillPlayerTriggered`**: 击杀事件依赖此事件，如果事件签名或触发机制变更需同步修改
- **`RoleSkill.withTarget()`**: 技能目标选择器依赖此功能，如果 API 变更需同步修改
- **`setTaskReward()`**: 任务奖励系统依赖此功能，如果上游修改任务奖励机制需同步修改

### 低风险

- **`TMMItems.REVOLVER`**: 左轮手枪物品，较稳定
- **`SREAbilityPlayerComponent.addSkillCharges()`**: 技能次数管理，API 较稳定
- **`OnGettingPlayerSkin`**: 皮肤事件，API 较稳定

---

## 资源文件

### 皮肤纹理

- **路径**: `assets/noellesroles/textures/entity/player/black_man.png`
- **模型**: slim (Alex)
- **用途**: 被美国警察技能标记的玩家皮肤

---

## 迁移修正记录

| 日期 | 修正内容 | 原因 |
|------|---------|------|
| 2026-09-30 | `Tag.createStringTag()` 改为 `StringTag.valueOf()` | 当前项目 NBT API 版本不同 |
| 2026-09-30 | `io.wifi.starrailexpress.api.RoleUtils` 改为 `org.agmas.noellesroles.utils.RoleUtils` | RoleUtils 类位置不同 |
| 2026-10-03 | `AmericanPoliceRole.onFinishQuest` 补上 `super.onFinishQuest()` 调用 | 覆写时未调父类，导致 `giveGeneralTaskAwards` 从不执行，完成任务拿不到枪 |
| 2026-10-03 | 标记数据模型由警察组件的 `List<UUID> markedPlayers` 改为**被标记者自身组件**的 `boolean marked`（含 sync） | 客户端皮肤处理器 `AmericanPoliceClientState` 与 `canXiaonao` 都读取被标记者自己的组件，旧模型永远为空导致标记技能不生效 |
| 2026-10-03 | `QUSTRoles` 的 `setTaskReward(3,...)` 改为读取 `QUSTConfig.instance().americanPoliceTasksForGun` | 支持通过 JSON 配置给枪所需任务数，无需改源码 |
| 2026-10-03 | `AmericanPoliceRole` 新增 `getShopEntries()` 售卖手铐（150金币） | 该角色应可购买手铐（参考义警） |
