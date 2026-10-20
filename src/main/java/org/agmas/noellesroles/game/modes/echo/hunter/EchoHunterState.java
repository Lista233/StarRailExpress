package org.agmas.noellesroles.game.modes.echo.hunter;

import java.util.ArrayList;
import java.util.List;

/**
 * 单个追猎者的职业运行时状态：连段、在飞的蛛丝弹、自己布下的黏网。
 *
 * <p>放在会话成员里而不是静态表里，是因为这三样都必须跟「这一局、这个人」绑定：
 * 掉线重连要保留，结算要一次清空，跨局不能残留。
 *
 * <p>本类只持有纯状态机，不读世界、不发包；判定与表现全部由
 * {@code EchoCombatService} 驱动。
 */
public final class EchoHunterState {
    /** 裂爪被动「血腥味」：附近有流血猎物时锁定增益倍率。 */
    public static final double REND_FOCUS_BOOST = 1.5D;
    /** 织网被动「丝线感知」：对静止猎物的锁定增益倍率。 */
    public static final double WEAVER_FOCUS_BOOST = 1.5D;
    /** 织网被动判定「静止」所需的连续 tick（3 秒）。 */
    public static final int WEAVER_STILL_TICKS = 60;
    /** 织网被动的感知半径（格）。 */
    public static final double WEAVER_STILL_RANGE = 20.0D;
    /** 织网命中被缠住的猎物时追加的倒地时长（tick）= 2 秒。 */
    public static final int WEAVER_BOUND_BLEED_TICKS = 40;

    private final EchoRendCombo combo = new EchoRendCombo();
    private final List<EchoWebBolt> bolts = new ArrayList<>(2);
    private final List<EchoWebField> fields = new ArrayList<>(2);

    public EchoRendCombo combo() { return combo; }
    public List<EchoWebBolt> bolts() { return bolts; }
    public List<EchoWebField> fields() { return fields; }

    public void addBolt(EchoWebBolt bolt) { if (bolt != null) bolts.add(bolt); }
    public void addField(EchoWebField field) { if (field != null) fields.add(field); }

    /** 同一时刻最多允许的在飞弹数，防止连点堆包。 */
    public static final int MAX_BOLTS = 3;

    public boolean canThrow() { return bolts.size() < MAX_BOLTS; }

    /** 该点是否被本人布下的蛛网阵覆盖（锁定不衰减判定用）。 */
    public boolean holdsFocusAt(long now, double x, double z) {
        for (EchoWebField field : fields) if (field.holdsFocus() && field.covers(now, x, z)) return true;
        return false;
    }

    /** 该点是否落在本人任意一块黏网上（命中追加缠住的判定用）。 */
    public int bindTicksAt(long now, double x, double z) {
        int ticks = 0;
        for (EchoWebField field : fields) if (field.covers(now, x, z)) ticks = Math.max(ticks, field.bindTicks());
        return ticks;
    }

    /** 清掉已结束的弹与过期的场地；每 tick 调用一次。 */
    public void prune(long now) {
        bolts.removeIf(EchoWebBolt::finished);
        fields.removeIf(field -> field.expired(now));
    }

    public void clear() {
        combo.clear();
        bolts.clear();
        fields.clear();
    }

    // ───────────────────────── 被动（纯函数，可单测） ─────────────────────────

    /** 裂爪被动：附近有流血猎物时把锁定增益放大；没有目标时原样返回 0。 */
    public static double rendGain(double baseGain, boolean bleedingNearby) {
        if (baseGain <= 0D) return baseGain;
        return bleedingNearby ? baseGain * REND_FOCUS_BOOST : baseGain;
    }

    /**
     * 织网被动：对在感知半径内静止够久的猎物锁定更快；其它情况原样返回。
     *
     * @param baseGain  本 tick 的基础锁定增益
     * @param stillTicks 该猎物连续静止的 tick 数
     * @param distance   与该猎物的水平距离（格）
     */
    public static double weaverGain(double baseGain, int stillTicks, double distance) {
        if (baseGain <= 0D) return baseGain;
        if (distance > WEAVER_STILL_RANGE) return baseGain;
        return stillTicks >= WEAVER_STILL_TICKS ? baseGain * WEAVER_FOCUS_BOOST : baseGain;
    }

    /** 命中被缠住的猎物时追加的倒地时长；未缠住返回 0。 */
    public static int boundBleed(boolean bound) { return bound ? WEAVER_BOUND_BLEED_TICKS : 0; }
}
