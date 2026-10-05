package org.agmas.noellesroles.role.qust;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import net.minecraft.resources.ResourceLocation;

import org.agmas.noellesroles.role.qust.roles.american_police.AmericanPoliceRole;
import org.agmas.noellesroles.role.qust.roles.dragon_girl.DragonGirlRole;
// QUST 组件键通过 QUSTComponentKeys.Keys 延迟持有类访问
import org.agmas.noellesroles.role.qust.roles.mascot.MascotRole;

import org.agmas.noellesroles.role.qust.roles.pressure_monster.PressureMonsterRole;

import org.agmas.noellesroles.role.qust.roles.minigame_master.MinigameMasterRole;

import org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderRole;

import org.agmas.noellesroles.role.qust.roles.wanderer.WandererRole;

import org.agmas.noellesroles.role.qust.roles.bettor.BettorRole;
import org.agmas.noellesroles.role.qust.roles.super_doctor.SuperDoctorRole;

/**
 * QUST 职业注册。
 * <p>
 * 所有 QUST 分支新增的职业在此注册，使用独立 NAMESPACE 避免与上游冲突。
 */
public class QUSTRoles {
    public static final String NAMESPACE = "qust";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(NAMESPACE, path);
    }

    // ── 龙娘 (Dragon Girl) ──
    public static final ResourceLocation DRAGON_GIRL_ID = id("dragon_girl");

    public static SRERole DRAGON_GIRL = TMMRoles.registerRole(
            new DragonGirlRole(DRAGON_GIRL_ID,
                    new java.awt.Color(180, 130, 255).getRGB(),
                    true, false, MoodType.REAL,
                    TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false))
            .setDefaultEnableChance(5000)
            .setAddedVersion("4.5");

    // ── 吉祥物 (Mascot) ──
    public static final ResourceLocation MASCOT_ID = id("mascot");

    public static SRERole MASCOT = TMMRoles.registerRole(
            new MascotRole(MASCOT_ID,
                    new java.awt.Color(238, 221, 130).getRGB(),
                    true, false, MoodType.REAL,
                    (int) (TMMRoles.CIVILIAN.getMaxSprintTime() * 1.25), false))
            .setCanSeeCoin(true)
            .setComponentKey(QUSTComponentKeys.Keys.MASCOT)
            .setDefaultMax(1)
            .setDefaultEnableChance(6000)
            .setDefaultEnableNeededPlayerCount(8);

    // ── 美国警察 (American Police / 亦_无悔) ──
    public static final ResourceLocation AMERICAN_POLICE_ID = id("american_police");
    public static final ResourceLocation AMERICAN_POLICE_SKILL_ID = id("american_police_skill");

    public static SRERole AMERICAN_POLICE = TMMRoles.registerRole(
            new AmericanPoliceRole(AMERICAN_POLICE_ID,
                    new java.awt.Color(100, 149, 237).getRGB(),
                    true, false, MoodType.REAL,
                    TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false))
            .setCanSeeCoin(true)
            .setVigilanteTeam(true)
            .setComponentKey(QUSTComponentKeys.Keys.AMERICAN_POLICE)
            .setTaskReward(QUSTConfig.instance().americanPoliceTasksForGun, 1,
                    io.wifi.starrailexpress.index.TMMItems.REVOLVER.getDefaultInstance())
            .setTaskRewardMessage("message.american_police.revolver_received")
            .setDefaultMax(1)
            .setDefaultEnableChance(5000);

    // ── 压力怪 (Pressure Monster / 牢张) ──
    public static final ResourceLocation PRESSURE_MONSTER_ID = id("pressure_monster");
    public static final ResourceLocation PRESSURE_MONSTER_SKILL_ID = id("pressure_monster_skill");

    public static SRERole PRESSURE_MONSTER = TMMRoles.registerRole(
            new PressureMonsterRole(PRESSURE_MONSTER_ID,
                    new java.awt.Color(139, 0, 0).getRGB(),
                    false, true, MoodType.FAKE,
                    Integer.MAX_VALUE, true))
            .setComponentKey(QUSTComponentKeys.Keys.PRESSURE_MONSTER)
            .setDefaultMax(1)
            .setDefaultEnableChance(5000);

    // ── 小游戏达人 (Minigame Master / 这个_骇客) ──
    public static final ResourceLocation MINIGAME_MASTER_ID = id("minigame_master");

    public static SRERole MINIGAME_MASTER = TMMRoles.registerRole(
            new MinigameMasterRole(MINIGAME_MASTER_ID,
                    new java.awt.Color(0, 191, 255).getRGB(),
                    true, false, MoodType.REAL,
                    TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false))
            .setComponentKey(QUSTComponentKeys.Keys.MINIGAME_MASTER)
            .setDefaultMax(1)
            .setDefaultEnableChance(5000);

    // ── 超级记录员 (Super Recorder / 超级记录员_时星) ──
    public static final ResourceLocation SUPER_RECORDER_ID = id("super_recorder");

    public static SRERole SUPER_RECORDER = TMMRoles.registerRole(
            new SuperRecorderRole(SUPER_RECORDER_ID,
                    new java.awt.Color(100, 200, 255).getRGB(),
                    false, false, MoodType.FAKE,
                    TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, true))
            .setCanSeeCoin(true)
            .setCanSeeBodyItems(true)
            .setComponentKey(QUSTComponentKeys.Keys.SUPER_RECORDER)
            .setDefaultMax(1)
            .setDefaultEnableChance(5000)
            .setDefaultEnableNeededPlayerCount(12);

    // ── 游荡者 (Wanderer_QUST / 神权_Lista) ──
    public static final ResourceLocation WANDERER_ID = id("wanderer_qust");
    public static final ResourceLocation WANDERER_SKILL_ID = id("wanderer_qust_skill");

    public static SRERole WANDERER = TMMRoles.registerRole(
            new WandererRole(WANDERER_ID,
                    new java.awt.Color(200, 180, 255).getRGB(),
                    true, false, MoodType.REAL,
                    TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false))
            .setCanSeeCoin(true)
            .setComponentKey(QUSTComponentKeys.Keys.WANDERER)
            .setDefaultMax(1)
            .setDefaultEnableChance(5000);

    // ── 超级医生 (Super Doctor / 医生_花艺) ──
    public static final ResourceLocation SUPER_DOCTOR_ID = id("super_doctor");
    public static final ResourceLocation SUPER_DOCTOR_SKILL_ID = id("super_doctor_skill");

    public static SRERole SUPER_DOCTOR = TMMRoles.registerRole(
            new SuperDoctorRole(SUPER_DOCTOR_ID,
                    new java.awt.Color(144, 238, 144).getRGB(),
                    true, false, MoodType.REAL,
                    TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false))
            .setCanSeeCoin(true)
            .setComponentKey(QUSTComponentKeys.Keys.SUPER_DOCTOR)
            .setDefaultMax(1)
            .setDefaultEnableChance(5000);

    // ── 筹客 (Bettor) ──
    public static final ResourceLocation BETTOR_ID = id("bettor");

    public static SRERole BETTOR = TMMRoles.registerRole(
            new BettorRole(BETTOR_ID,
                    new java.awt.Color(255, 215, 0).getRGB(),
                    true, false, MoodType.REAL,
                    TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false))
            .setCanSeeCoin(true)
            .setComponentKey(QUSTComponentKeys.Keys.BETTOR)
            .setDefaultMax(1)
            .setDefaultEnableChance(5000);

    // ── 黑客 (Hacker / 林然) ──
    public static final ResourceLocation HACKER_ID = id("hacker");
    public static final ResourceLocation HACKER_MARK_SKILL_ID = id("hacker_mark");
    public static final ResourceLocation HACKER_SEND_SKILL_ID = id("hacker_send");

    public static SRERole HACKER = TMMRoles.registerRole(
            new org.agmas.noellesroles.role.qust.roles.hacker.HackerRole(HACKER_ID,
                    new java.awt.Color(100, 100, 100).getRGB(),
                    false, false, MoodType.FAKE,
                    TMMRoles.CIVILIAN_MAX_SPRINT_TICKS, false))
            .setNeutrals(true)
            .setComponentKey(QUSTComponentKeys.Keys.HACKER)
            .setDefaultMax(1)
            .setDefaultEnableChance(5000)
            .setDefaultEnableNeededPlayerCount(10);

    public static void init() {
        // 触发类加载，确保静态字段被初始化
        //
        // 修正：上面的职业均用 `registerRole(...).setComponentKey(...)` 链式写法注册，
        // 而 TMMRoles.registerRole 在 setComponentKey 之前执行，此刻 getComponentKey() 仍为 null，
        // 导致组件键没被收录进 TMMRoles.COMPONENT_KEYS。RoleMethodDispatcher.onStartGame/onEndGame
        // 只遍历 COMPONENT_KEYS 调 clear()，因此这些组件跨局不会被自动复位。
        // 这里显式补登记所有 QUST 组件键，确保每局边界 clear()→init() 复位状态。
        TMMRoles.addRoleComponents(QUSTComponentKeys.Keys.DRAGON_GIRL);
        TMMRoles.addRoleComponents(QUSTComponentKeys.Keys.MASCOT);
        TMMRoles.addRoleComponents(QUSTComponentKeys.Keys.AMERICAN_POLICE);
        TMMRoles.addRoleComponents(QUSTComponentKeys.Keys.PRESSURE_MONSTER);
        TMMRoles.addRoleComponents(QUSTComponentKeys.Keys.MINIGAME_MASTER);
        TMMRoles.addRoleComponents(QUSTComponentKeys.Keys.SUPER_RECORDER);
        TMMRoles.addRoleComponents(QUSTComponentKeys.Keys.WANDERER);
        TMMRoles.addRoleComponents(QUSTComponentKeys.Keys.SUPER_DOCTOR);
        TMMRoles.addRoleComponents(QUSTComponentKeys.Keys.BETTOR);
        TMMRoles.addRoleComponents(QUSTComponentKeys.Keys.HACKER);
    }
}
