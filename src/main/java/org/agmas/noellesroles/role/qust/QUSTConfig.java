package org.agmas.noellesroles.role.qust;

import io.wifi.ConfigCompact.ConfigClassHandler;
import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

/**
 * QUST 自定义职业的独立配置文件。
 * <p>
 * 生成路径：{@code config/qust_roles.json}，服主可直接编辑该文件调整数值。
 * <p>
 * 使用方式：
 * <pre>{@code
 * QUSTConfig cfg = QUSTConfig.instance();
 * int cooldown = cfg.myFirstSkillCooldown;
 * }</pre>
 * <p>
 * 注意：此文件独立于 {@code NoellesRolesConfig}，不会与上游产生冲突。
 */
@Config(name = "qust_roles")
public class QUSTConfig implements ConfigData {

    public static ConfigClassHandler<QUSTConfig> HANDLER = new ConfigClassHandler<>(QUSTConfig.class);

    public static QUSTConfig instance() {
        return HANDLER.instance();
    }

    // ==================== 龙娘 (Dragon Girl) ====================

    /** 魅惑技能冷却（秒） */
    public int dragonGirlCharmCooldownSeconds = 30;

    /** 魅惑持续时间（秒） */
    public int dragonGirlCharmDurationSeconds = 4;

    /** 魅惑范围（方块） */
    public int dragonGirlCharmRange = 5;

    /** 魅惑拉扯速度 */
    public double dragonGirlCharmPullSpeed = 0.3;

    /** 咆哮技能冷却（秒） */
    public int dragonGirlRoarCooldownSeconds = 40;

    /** 咆哮引导时间（秒） */
    public double dragonGirlRoarChannelDurationSeconds = 1.5;

    /** 咆哮粒子扩散范围 */
    public int dragonGirlRoarParticleRange = 6;

    /** 咆哮击退最大范围（方块） */
    public int dragonGirlRoarKnockbackMaxRange = 6;

    /** 咆哮Y轴判定范围 */
    public int dragonGirlRoarYRange = 2;

    /** 咆哮最大击退距离（方块） */
    public int dragonGirlRoarKnockbackMax = 9;

    /** 咆哮最小击退距离（方块） */
    public int dragonGirlRoarKnockbackMin = 4;

    // ==================== 吉祥物 (Mascot) ====================

    /** 完成任务所需数量 */
    public int mascotRequiredTaskCount = 10;

    /** 首次发光倒计时（秒） */
    public int mascotGlowCutDownSeconds = 90;

    /** 发光间隔冷却（秒） */
    public int mascotGlowCutDownCDSeconds = 12;

    /** 单次发光持续时间（秒） */
    public int mascotGlowDurationSeconds = 2;

    /** 完成任务后给周围玩家恢复心情/金币的范围（格） */
    public double mascotBuffRange = 15.0;

    // ==================== 美国警察 (American Police) ====================

    /** 技能冷却时间（秒） */
    public int americanPoliceSkillCooldownSeconds = 60;

    /** 每完成多少个任务奖励一次技能使用次数 */
    public int americanPoliceTasksPerCharge = 7;

    /** 完成多少个任务后获得左轮手枪（枪） */
    public int americanPoliceTasksForGun = 3;

    /** 手铐商店价格 */
    public int americanPoliceHandcuffPrice = 150;

    // ==================== 压力怪 (Pressure Monster) ====================

    /** 技能冷却时间（秒） */
    public int pressureMonsterSkillCooldownSeconds = 180;

    /** 初始冷却时间（秒） */
    public int pressureMonsterInitialCooldownSeconds = 60;

    /** san值降低百分比（0.6 = 降低当前 60%） */
    public double pressureMonsterSanReductionPercent = 0.6;

    /** 技能影响范围（方块） */
    public double pressureMonsterSkillRadius = 6.0;

    /** 击杀减少冷却时间（秒） */
    public int pressureMonsterKillCdReductionSeconds = 30;

    /** 小脑事件减少冷却时间（秒） */
    public int pressureMonsterXiaonaoCdReductionSeconds = 90;

    // ==================== 小游戏达人 (Minigame Master / 这个_骇客) ====================

    /** 小游戏券商店价格 */
    public int minigameMasterTicketPrice = 50;

    /** 击退剑商店价格 */
    public int minigameMasterKnockbackSwordPrice = 200;

    /** 完成小游戏奖励金币数 */
    public int minigameMasterCoinReward = 25;

    /** 完成华容道奖励金币数 */
    public int minigameMasterKlotskiCoinReward = 250;

    /** 华容道挑战价格（通关可直接赢得比赛） */
    public int minigameMasterKlotskiChallengePrice = 1200;

    // ==================== 超级记录员 (Super Recorder / 超级记录员_时星) ====================

    /** 真相之书商店价格 */
    public int superRecorderTruthBookPrice = 150;

    /** 记录笔记商店价格 */
    public int superRecorderNotePrice = 50;

    /** 死亡感知范围（方块） */
    public double superRecorderDeathSenseRange = 15.0;

    /** 做任务获得的基础金币（与平民一致） */
    public int superRecorderTaskCoinReward = 50;

    // ==================== 超级医生 (Super Doctor / 医生_花艺) ====================

    /** G 键治疗技能冷却（秒） */
    public int superDoctorHealCooldownSeconds = 30;

    /** G 键治疗范围（方块） */
    public double superDoctorHealRange = 6.0;

    /** G 键恢复 san 值量（0.0-1.0） */
    public double superDoctorHealAmount = 0.1;

    /** 悔改之枪商店价格 */
    public int superDoctorRepentanceGunPrice = 350;

    // ==================== 黑客 (Hacker / 林然) ====================

    /** 干扰芯片技能冷却（秒） */
    public int hackerMarkCooldownSeconds = 5;

    /** 发送终端技能冷却（秒） */
    public int hackerSendCooldownSeconds = 5;
}
