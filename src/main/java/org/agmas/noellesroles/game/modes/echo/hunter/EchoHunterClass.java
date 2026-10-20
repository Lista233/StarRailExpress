package org.agmas.noellesroles.game.modes.echo.hunter;

import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoSkill;
import org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/**
 * 追猎者（{@link EchoFaction#INTERCEPTOR}）阵营内的作战职业。
 *
 * <p>与 {@link EchoSpecialization} 的区别是刻意的：
 * <ul>
 * <li>专长（HUNTER / SABOTEUR）只调整数值——蓄力、冷却、污染速度；</li>
 * <li>职业改变**打法**本身：攻击方式、被动判定与主动技能三者都不同。</li>
 * </ul>
 *
 * <p>本枚举只提供身份、文本键与技能映射，全部战斗数值放在各自的纯状态机里
 * （{@link EchoRendCombo}、{@link EchoWebBolt}、{@link EchoWebField}），
 * 因此不需要读世界、不发包，可以完全被单元测试覆盖。
 *
 * <p>职业只对 {@code HUNTER} 专长生效；{@code SABOTEUR} 保持原版割刀手感不变。
 */
public enum EchoHunterClass {
    /** 裂爪：近战三段连击，第三段是带出血终结技；被动「血腥味」在流血目标附近快速锁定。 */
    REND(EchoSkill.REND_DASH),
    /** 织网：远程蛛丝弹，命中远距离击倒并留下黏网；被动「丝线感知」对静止目标锁定更快。 */
    WEAVER(EchoSkill.WEB_NEST);

    private final EchoSkill skill;

    EchoHunterClass(EchoSkill skill) { this.skill = skill; }

    /** 本职业的主动技能；与专长一一对应的旧技能（如 LOCK_ON）不再由职业使用。 */
    public EchoSkill skill() { return skill; }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String nameKey() { return "echo.hunter." + id() + ".name"; }
    public String descriptionKey() { return "echo.hunter." + id() + ".desc"; }
    public String passiveNameKey() { return "echo.hunter." + id() + ".passive.name"; }
    public String passiveDescriptionKey() { return "echo.hunter." + id() + ".passive.desc"; }
    public String attackNameKey() { return "echo.hunter." + id() + ".attack.name"; }
    public String attackDescriptionKey() { return "echo.hunter." + id() + ".attack.desc"; }

    /** 职业是否有远程攻击方式（决定 HUD 准星与收刀动作）。 */
    public boolean ranged() { return this == WEAVER; }

    public static Optional<EchoHunterClass> byId(String id) {
        if (id == null) return Optional.empty();
        String normalized = id.toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).filter(value -> value.id().equals(normalized)).findFirst();
    }

    /** 按追猎者出场序号轮流分配，保证同一局的两个追猎者不会拿到同一个职业。 */
    public static EchoHunterClass defaultFor(int index) {
        EchoHunterClass[] values = values();
        return values[Math.floorMod(index, values.length)];
    }

    /** 只有追猎者阵营拥有职业；其余阵营返回 {@code null}，调用方据此走原版分支。 */
    public static EchoHunterClass faction(EchoFaction faction) {
        return faction == EchoFaction.INTERCEPTOR ? defaultFor(0) : null;
    }

    /** 该专长是否接受职业分支；SABOTEUR 刻意保留原版割刀。 */
    public static boolean accepts(EchoSpecialization specialization) {
        return specialization == EchoSpecialization.HUNTER;
    }
}
