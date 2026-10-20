package org.agmas.noellesroles.game.modes.echo.state;

import java.util.Locale;

/** 专长主动技能：一次性效果用 pending 窗口，持续增益用 buff 窗口；冷却按比赛 tick。 */
public enum EchoSkill {
    STEADY_HAUL(EchoSpecialization.PORTER, 1800, 160, false),
    SWIFT_PICK(EchoSpecialization.SCOUT, 1200, 200, true),
    /** 狩猎本能：持续增益（不是一次性），窗口内锁定充满、加速、免后摇、蓄力压缩。 */
    LOCK_ON(EchoSpecialization.HUNTER, 1500, 60, false),
    SURGE(EchoSpecialization.SABOTEUR, 1600, 200, true),
    DARK_CACHE(EchoSpecialization.SMUGGLER, 1800, 240, true),
    BROKER_DEAL(EchoSpecialization.FIXER, 1200, 0, false);

    private final EchoSpecialization specialization;
    private final int cooldownTicks;
    private final int windowTicks;
    private final boolean pending;

    EchoSkill(EchoSpecialization specialization, int cooldownTicks, int windowTicks, boolean pending) {
        this.specialization = specialization;
        this.cooldownTicks = cooldownTicks;
        this.windowTicks = windowTicks;
        this.pending = pending;
    }

    public EchoSpecialization specialization() { return specialization; }
    public int cooldownTicks() { return cooldownTicks; }
    public int windowTicks() { return windowTicks; }
    /** true 表示一次性效果（用后消耗），false 表示持续增益。 */
    public boolean pending() { return pending; }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String nameKey() { return "echo.skill." + id() + ".name"; }
    public String descriptionKey() { return "echo.skill." + id() + ".desc"; }
    public String icon() { return "noellesroles:textures/gui/echo/skills/" + id() + ".png"; }

    public static EchoSkill of(EchoSpecialization specialization) {
        for (var skill : values()) if (skill.specialization == specialization) return skill;
        return STEADY_HAUL;
    }
}
