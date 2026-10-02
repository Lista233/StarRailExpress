# QUST 职业开发指南（冲突避免 & 包结构规范）

> 本文档面向 QUST 分支的独立职业开发。核心目标：**所有职业代码集中在 `role/qust/` 包内，与上游零冲突或极低冲突**。
> 配套阅读：`docs/AI创建新职业攻略.md`（通用职业开发流程）、`AGENT.md`（项目铁律）。

---

## 0. 核心原则

1. **所有 QUST 职业代码集中在 `org.agmas.noellesroles.role.qust` 包下**，不跨包散放。
2. **只碰一个共享文件** `AAAHandlerFather.java`（加一行调用），其余全是你自己的文件。
3. **语言文件只改 `zh_cn.json`**，追加在末尾，跳过 `zh_tw.json` 和 `en_us.json`。
4. **不改 `io/wifi/` 下的任何代码**，只用 API。

---

## 1. 包结构（每个角色独立子包）

```
src/main/java/org/agmas/noellesroles/role/qust/
├── QUSTRoles.java              ← 职业注册（类似 THMiscRoles.java）
├── QUSTHandlers.java           ← 技能/事件注册入口（类似 TouhouHandlers.java）
├── QUSTConfig.java             ← 独立配置文件（运行时可调数值，不改上游配置）
├── roles/                      ← 每个角色一个子包
│   └── dragon_girl/            ← 龙娘（示例）
│       ├── DragonGirlRole.java
│       ├── DragonGirlPlayerComponent.java
│       ├── DragonGirlHud.java
│       └── DragonGirlClientState.java
├── client/                     ← 客户端统一入口
│   └── QUSTClient.java         ← HUD/皮肤等客户端注册
└── items/                      ← 专属物品（按需）
    └── MyItem.java
```

**为什么这样组织**：
- 每个角色放在 `roles/<角色名>/` 子包下，所有相关文件（Role、Component、Hud、ClientState）集中管理
- 所有 QUST 相关的代码都在 `role/qust/` 下，不会散落到 `handler/`、`client/`、`init/` 等包
- 上游更新不会碰你的包，你的包也不会影响上游

---

## 2. 文件模板

### 2.1 QUSTRoles.java（职业注册）

```java
package org.agmas.noellesroles.role.qust;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.TouhouRole; // 或 NormalRole / EggRole / CustomWinnerRole
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.NormalRole.RoleType;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.role.qust.roles.*;

public class QUSTRoles {
    public static final String NAMESPACE = "qust";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    // ── 职业注册 ──
    public static SRERole MY_FIRST_ROLE = TMMRoles.registerRole(
            new MyFirstRole(id("my_first_role"),
                    new java.awt.Color(100, 200, 150).getRGB(),
                    true, false, MoodType.REAL,
                    TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false))
            .setDefaultEnableChance(5000)
            .setAddedVersion("4.5");

    public static void init() {
    }
}
```

### 2.2 QUSTHandlers.java（技能/事件注册入口）

```java
package org.agmas.noellesroles.role.qust;

import org.agmas.noellesroles.role.qust.roles.MyFirstRole;

public class QUSTHandlers {

    public static void register() {
        QUSTConfig.HANDLER.load();       // 确保配置文件生成
        QUSTRoles.init();                // 触发类加载 → 静态字段注册职业
        MyFirstRole.registerSkills();    // 注册技能
        MyFirstRole.registerEvents();    // 注册事件
        // 新职业在这里加两行：
        // MySecondRole.registerSkills();
        // MySecondRole.registerEvents();
    }
}
```

### 2.3 角色类（以 MyFirstRole 为例）

```java
package org.agmas.noellesroles.role.qust.roles;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.api.TouhouRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.InstinctType;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.SRE;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.role.qust.QUSTRoles;

import java.util.ArrayList;
import java.util.List;

public class MyFirstRole extends TouhouRole {

    public MyFirstRole(ResourceLocation identifier, int color, boolean isInnocent,
            boolean canUseKiller, MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    // ── 商店 ──
    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        // shop.add(new ShopEntry(...));
        return shop;
    }

    // ── XiaoNao 豁免（零冲突方式）──
    @Override
    public boolean canBeXiaonao(Player victim, Player killer, ResourceLocation deathReason) {
        return false; // 别人误杀我不受罚
    }

    @Override
    public boolean canXiaonao(Player victim, Player killer, ResourceLocation deathReason) {
        return false; // 我误杀别人不受罚
    }

    // ── 技能注册（static 方法，由 QUSTHandlers 调用）──
    public static void registerSkills() {
        RoleSkill.register(QUSTRoles.MY_FIRST_ROLE,
                RoleSkill.skill(SRE.id("my_first_skill"), "skill.qust.my_first_skill", (ctx) -> {
                    ServerPlayer player = ctx.player();
                    if (player.isSpectator()) return false;
                    // 技能逻辑...
                    return true;
                }).cooldownSeconds(60).showOnHud(true).announceToSelf().build());
    }

    // ── 事件注册（static 方法，由 QUSTHandlers 调用）──
    public static void registerEvents() {
        // 按需注册事件监听
        // OnPlayerDeath.EVENT.register(...)
    }
}
```

### 2.4 客户端本能注册（client/QUSTInstincts.java）

```java
package org.agmas.noellesroles.role.qust.client;

import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import pro.fazeclan.river.stupid_express.api.TrueFalseAndCustomResult;

import java.awt.*;

public class QUSTInstincts {

    public static void registerEvents() {
        // 「我看别人」的高亮
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(
                QUSTRoles.MY_FIRST_ROLE.getIdentifier(),
                (client, self, target, hasInstinct) -> {
                    if (!hasInstinct) return TrueFalseAndCustomResult.pass();
                    // 自定义高亮逻辑...
                    return TrueFalseAndCustomResult.pass();
                });

        // 「别人看我」的高亮
        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(
                QUSTRoles.MY_FIRST_ROLE.getIdentifier(),
                (client, self, target, hasInstinct) -> {
                    return TrueFalseAndCustomResult.pass();
                });
    }
}
```

---

## 3. 唯一需要修改的共享文件

### AAAHandlerFather.java（只加一行）

```java
public static void register(){
    TouhouHandlers.register();
    BounsHandlers.register();
    AnimeHandlers.register();
    QUSTHandlers.register();  // ← 只加这一行
}
```

### 客户端本能注册入口（在 RoleInstinctRegister.registerSpecialLogic() 中加一行）

```java
public static void registerSpecialLogic() {
    TouhouInstincts.registerEvents();
    QUSTInstincts.registerEvents();  // ← 只加这一行
    // ...
}
```

> 如果不需要自定义本能透视，这一行也可以不加。

---

## 4. 冲突避免策略详解

### 4.1 XiaoNao 惩罚（不需要改 XiaoNaoHandler.java）

| 需求 | 做法 | 冲突 |
|------|------|------|
| 我的角色不受小脑惩罚 | 覆写 `canBeXiaonao()` 返回 `false` | ✅ 无 |
| 我的角色误杀不罚 | 覆写 `canXiaonao()` 返回 `false` | ✅ 无 |
| 简单开关 | 注册时 `.setCanBeXiaonao(false)` / `.setCanXiaonao(false)` | ✅ 无 |
| 复杂条件判断 | 在自己的 Role 类 `registerEvents()` 里监听 `OnTeammateKilledTeammate.EVENT` | ✅ 无 |

### 4.2 本能/Instinct（不需要改 RoleInstinctRegister.java 的主体逻辑）

| 需求 | 做法 | 冲突 |
|------|------|------|
| 配置式本能颜色 | 注册时 `.setInstinctType()` / `.setBeSeenInstinctType()` | ✅ 无 |
| 自定义看人高亮 | 在 `QUSTInstincts.java` 用 `OBSERVER_HIGHLIGHT_EVENT.register(myRoleId, ...)` | ✅ 无 |
| 自定义被看高亮 | 在 `QUSTInstincts.java` 用 `TARGET_HIGHLIGHT_EVENT.register(myRoleId, ...)` | ✅ 无 |

### 4.3 语言文件（只改 zh_cn.json）

**策略**：只写 `zh_cn.json`，追加在文件**末尾**，用注释分隔。

**重要：翻译键使用 `id.getPath()`（不含命名空间前缀）**

代码中翻译键的拼接方式是 `"info.screen.roleid." + id.getPath()`，
其中 `id` 是 `qust:dragon_girl`，`getPath()` 返回 `dragon_girl`（不含 `qust`）。
所以翻译键**不要**加 `qust.` 前缀！

| 用途 | 代码拼接方式 | 正确键名 | 错误键名 |
|------|-------------|----------|----------|
| 职业名 | `"announcement.star.role." + path` | `announcement.star.role.dragon_girl` | ~~`announcement.star.role.qust.dragon_girl`~~ |
| 目标 | `"announcement.star.goals." + path` | `announcement.star.goals.dragon_girl` | ~~`announcement.star.goals.qust.dragon_girl`~~ |
| 详细介绍 | `"info.screen.roleid." + path` | `info.screen.roleid.dragon_girl` | ~~`info.screen.roleid.qust.dragon_girl`~~ |
| 简短介绍 | `"info.screen.roleid." + path + ".simple"` | `info.screen.roleid.dragon_girl.simple` | ~~`info.screen.roleid.qust.dragon_girl.simple`~~ |

```json
{
    ...上游的几百个条目...（不要动）

    "// --- QUST 自定义职业 ---": "",
    "announcement.star.role.dragon_girl": "龙娘",
    "announcement.star.goals.dragon_girl": "龙娘就是🐉",
    "info.screen.roleid.dragon_girl": "平民阵营\\n...详细介绍...",
    "info.screen.roleid.dragon_girl.simple": "平民阵营\\n...简短介绍...",
    "skill.noellesroles.dragon_girl.charm": "龙娘魅惑"
}
```

跳过 `zh_tw.json` 和 `en_us.json`：
- 中文客户端会正确显示 zh_cn 的翻译
- 其他语言的客户端会显示键名（可接受，因为本应用只面向中国玩家）
- 少改两个文件 = 少两份冲突风险

### 4.4 动态配置（不需要改 NoellesRolesConfig.java）

**不要**往上游的 `NoellesRolesConfig.java` 里加字段（会冲突）。使用独立的 `QUSTConfig.java`：

```java
// role/qust/QUSTConfig.java（已创建）
@Config(name = "qust_roles")
public class QUSTConfig implements ConfigData {
    public static ConfigClassHandler<QUSTConfig> HANDLER = new ConfigClassHandler<>(QUSTConfig.class);
    public static QUSTConfig instance() { return HANDLER.instance(); }

    public int myFirstSkillCooldown = 60;
    public double myFirstSkillRange = 8.0;
    // ... 按需添加，永远不会和上游冲突
}
```

使用方式：

```java
// 在 Role 类或 Handler 中
QUSTConfig cfg = QUSTConfig.instance();
RoleSkill.register(QUSTRoles.MY_FIRST_ROLE,
    RoleSkill.skill(SRE.id("my_skill"), "skill.qust.my_skill", (ctx) -> {
        return doSkill(ctx.player(), cfg.myFirstSkillRange);
    }).cooldownSeconds(cfg.myFirstSkillCooldown).build());
```

配置文件生成在 `config/qust_roles.json`，服主可直接编辑。

| 方式 | 冲突 | 可热调 | 推荐场景 |
|------|------|--------|----------|
| 常量写在 Role 类 | ✅ 无 | ❌ 需重编译 | 简单/调试阶段 |
| 独立 QUSTConfig | ✅ 无 | ✅ 改 JSON | 需要服主调数值（推荐） |
| 改 NoellesRolesConfig | ⭐ 会 | ✅ | ❌ 不推荐 |

### 4.5 冲突面总结

| 文件 | 是否修改 | 冲突风险 |
|------|---------|---------|
| `role/qust/**`（你的所有文件，含 QUSTConfig） | ✅ 改 | ✅ 无（你的独有包，上游不会有） |
| `AAAHandlerFather.java` | 加一行 | ⭐ 极低 |
| `RoleInstinctRegister.java` | 加一行（可选） | ⭐ 极低 |
| `zh_cn.json` | 末尾追加 | ⭐ 低（追加式，rebase 易解决） |
| `zh_tw.json` / `en_us.json` | ❌ 不改 | ✅ 无 |
| `NoellesRolesConfig.java` | ❌ 不改（用 QUSTConfig） | ✅ 无 |
| `ModRoles.java` / `BounsRoles.java` 等 | ❌ 不改 | ✅ 无 |
| `io/wifi/**` | ❌ 不改 | ✅ 无 |

---

## 5. Git 同步上游工作流

```bash
# 推送你的改动到 fork
git push

# 同步官方库最新代码
git fetch upstream
git rebase upstream/master

# 如果 AAAHandlerFather.java 有冲突：
# 保留上游新增的 Handler 行 + 你的 QUSTHandlers.register() 行

# 如果 zh_cn.json 有冲突：
# 保留上游新增的条目 + 你末尾追加的条目
# 确保 JSON 格式正确（逗号、引号）

# 解决冲突后
git add .
git rebase --continue
git push --force-with-lease
```

---

## 6. 新职业开发清单

每创建一个新 QUST 职业，按此清单检查：

- [ ] 角色类写在 `role/qust/roles/<角色名>/` 子包下（每个角色独立子包）
- [ ] 在 `QUSTRoles.java` 注册职业
- [ ] 在 `QUSTHandlers.java` 添加技能/事件注册调用
- [ ] 客户端入口（HUD/皮肤）在 `QUSTClient.java` 注册
- [ ] XiaoNao 豁免用 `canBeXiaonao()` / `canXiaonao()` 覆写（不改 XiaoNaoHandler）
- [ ] 本能逻辑写在 `client/QUSTInstincts.java`（不改 RoleInstinctRegister 主体）
- [ ] 翻译键使用 `id.getPath()`（**不含命名空间前缀**），加在 `zh_cn.json` 末尾
- [ ] `info.screen.roleid.<path>` 和 `.simple` 两个都要写
- [ ] 可调数值使用 `QUSTConfig` 或常量（不改 NoellesRolesConfig）
- [ ] 没有改 `io/wifi/` 下的代码
- [ ] 没有改 `ModRoles.java` / `BounsRoles.java` / `TouhouHandlers.java` 等共享文件
- [ ] 编译通过：`./gradlew compileJava --offline`
- [ ] 语言文件 JSON 格式正确
- [ ] 冲突日志写在 `conflict_logs/<角色名>.md`
