# 压力怪 (Pressure Monster) 冲突日志

## 新建文件

- `src/main/java/org/agmas/noellesroles/role/qust/roles/pressure_monster/PressureMonsterRole.java`
- `src/main/java/org/agmas/noellesroles/role/qust/roles/pressure_monster/PressureMonsterPlayerComponent.java`
- `src/main/java/org/agmas/noellesroles/role/qust/roles/pressure_monster/PressureMonsterHud.java`

## 修改的共享文件

| 文件 | 修改内容 | 冲突风险 |
|------|----------|----------|
| `ModComponents.java` | 添加 PRESSURE_MONSTER 组件注册 | 低（追加在末尾） |
| `QUSTRoles.java` | 添加 PRESSURE_MONSTER 职业注册 | 低（追加在末尾） |
| `QUSTHandlers.java` | 添加技能和事件注册 | 低（追加方法） |
| `QUSTConfig.java` | 添加 6 个配置字段 | 低（追加在末尾） |
| `QUSTClient.java` | 添加 HUD 注册 | 低（追加一行） |
| `zh_cn.json` | 添加 11 条翻译 | 低（追加在末尾） |

## 角色配置

- 阵营: 杀手 (isInnocent=false, canUseKiller=true)
- 心情: FAKE
- 体力: 无限 (Integer.MAX_VALUE)
- 默认最大数量: 1
- 默认刷新概率: 5000 (50%)

## 技能机制

- 技能名: 施压
- 效果: 降低周围 6 格内所有玩家当前 60% 的 san 值
- 音效: 监守者心跳声 (WARDEN_HEARTBEAT)
- 初始冷却: 60s
- 正常冷却: 180s
- 击杀减少: 30s CD
- 小脑事件减少: 90s CD（全场任意玩家触发小脑时生效）

## San 值阈值参考

- `MID_MOOD_THRESHOLD = 0.55f` - 低于此进入幻觉阶段
- `DEPRESSIVE_MOOD_THRESHOLD = 0.2f` - 低于此进入抑郁阶段
- 技能从满 san (1.0) 降到 0.4，刚好进入幻觉阶段
