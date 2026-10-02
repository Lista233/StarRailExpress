# 龙娘 (dragon_girl) 冲突日志

> 记录该角色涉及的所有共享文件修改点，rebase 上游更新时参考此文档排查冲突。

## 基本信息

- 角色 ID: `qust:dragon_girl`
- 翻译键前缀: 使用 `id.getPath()` = `dragon_girl`（不含 `qust` 命名空间）
- 包路径: `org.agmas.noellesroles.role.qust.roles.dragon_girl`
- 迁移来源: `D:\Code\McMod\StarRailExpress-master` (源项目未注册，仅代码)

---

## 修改的共享文件清单

### 1. AAAHandlerFather.java（⭐ 极低风险）

**改动**: 添加一行 `QUSTHandlers.register();`

```java
public static void register(){
    TouhouHandlers.register();
    BounsHandlers.register();
    AnimeHandlers.register();
    QUSTHandlers.register();  // ← QUST 新增
}
```

**冲突排查**: 上游如果在此方法中新增 Handler 调用，rebase 时保留双方即可。

---

### 2. ModComponents.java（⭐ 低风险）

**改动**: 添加 DRAGON_GIRL ComponentKey 声明 + 工厂注册

```java
// 字段声明（约 L100）
public static final ComponentKey<org.agmas.noellesroles.role.qust.roles.dragon_girl.DragonGirlPlayerComponent> DRAGON_GIRL =
    org.agmas.noellesroles.role.qust.roles.dragon_girl.DragonGirlPlayerComponent.KEY;

// 工厂注册（约 L184）
registry.beginRegistration(Player.class, DRAGON_GIRL)
    .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
    .end(org.agmas.noellesroles.role.qust.roles.dragon_girl.DragonGirlPlayerComponent::new);
```

**冲突排查**: 上游新增组件时可能在附近插入代码，保留双方即可。注意全限定类名路径是否正确。

---

### 3. NoellesrolesClient.java（⭐ 极低风险）

**改动**: 添加一行 `QUSTClient.register();`

```java
FatFishSkinHandler.register();
QUSTClient.register();  // ← QUST 新增
```

**冲突排查**: 上游新增客户端注册时可能在此附近插入，保留双方即可。

---

### 4. zh_cn.json（⭐ 低风险，追加式）

**改动**: 在文件末尾 `}` 前追加龙娘翻译条目

**冲突排查**:
- 上游如果在文件末尾追加新条目，rebase 时保留双方追加
- 确保 JSON 逗号正确
- 注意：翻译键使用 `id.getPath()`（不含命名空间），所以键名是 `dragon_girl` 而非 `qust.dragon_girl`

**翻译键清单**:
| 键名 | 用途 |
|------|------|
| `announcement.star.role.dragon_girl` | 职业名称 |
| `announcement.star.goals.dragon_girl` | 目标描述 |
| `info.screen.roleid.dragon_girl` | 详细介绍 |
| `info.screen.roleid.dragon_girl.simple` | 简短介绍 |
| `skill.noellesroles.dragon_girl.charm` | 技能1名称 |
| `skill.noellesroles.dragon_girl.roar` | 技能2名称 |
| `hud.noellesroles.dragon_girl.*` | HUD文本 (6条) |
| `message.noellesroles.dragon_girl.*` | 消息文本 (3条) |

---

## 潜在冲突风险点

### 高风险（如果上游添加同名角色）

如果上游未来添加名为 `dragon_girl` 的角色（路径同为 `dragon_girl`）：
- **翻译键冲突**: `announcement.star.role.dragon_girl` 等键会共用，导致显示混乱
- **解决方案**: 修改我们的角色路径（如改为 `qust_dragon_girl`），或在 QUSTRoles.java 中修改 `id()` 调用

### 中风险

- **ModEffects.SKILL_BANED / INVENTORY_BANED**: 龙娘技能依赖这两个上游效果，如果上游重命名或删除会导致编译失败
- **GameUtils.isPlayerEliminated()**: 依赖上游工具方法，API 变更需同步修改

### 低风险

- **SoundEvents.ENDER_DRAGON_GROWL / GENERIC_EXPLODE**: 原版音效事件，极不可能变更
- **ParticleTypes.DRAGON_BREATH / SNOWFLAKE / CLOUD**: 原版粒子类型，极不可能变更
- **MobEffects.GLOWING / WEAKNESS**: 原版药水效果，极不可能变更

---

## 迁移修正记录

| 日期 | 修正内容 | 原因 |
|------|---------|------|
| 2026-09-30 | 翻译键从 `qust.dragon_girl` 改为 `dragon_girl` | 代码使用 `id.getPath()` 不含命名空间 |
| 2026-09-30 | 添加 `info.screen.roleid.dragon_girl.simple` | 缺少简短介绍翻译键 |
| 2026-09-30 | screen 详细介绍内容更新为源项目版本 | 原内容过于简略 |
| 2026-09-30 | 龙娘文件移入 `roles/dragon_girl/` 子包 | 每个角色独立子包的组织规范 |
