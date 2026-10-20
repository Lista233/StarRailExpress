package org.agmas.noellesroles.game.modes.echo.combat;

/**
 * 追捕者「狩猎循环」的纯状态机（参考第五人格的蓄力—突进—后摇节奏与黎明杀机的恐惧半径压力）。
 *
 * <p>循环固定为四相，无限往复：
 * <pre>
 *   STALK ──开始蓄力──▶ CHARGE ──松手──▶ LUNGE ──命中──▶ RECOVER(短) ─┐
 *     ▲                   │                 └──挥空──▶ RECOVER(长) ───┤
 *     └───────────────────┴────────────────────────────────────────────┘
 * </pre>
 *
 * <p>「锁定值」在 STALK 中累积、在 CHARGE 中消耗、满值时把下一次出击升级为「扑击」。
 * 本类不读 Minecraft 世界、不发包、不改玩家属性，只接受调用方传入的比赛 tick 与目标系数，
 * 因此可以被单元测试完整覆盖。
 */
public final class EchoHuntLoop {
    public enum Phase { STALK, CHARGE, LUNGE, RECOVER }

    /** 锁定值上限。 */
    public static final double FOCUS_MAX = 100.0D;
    /** 锁定判定距离（格）。 */
    public static final double FOCUS_RANGE = 24.0D;
    /** 近距/中距分界，用于距离系数。 */
    public static final double FOCUS_NEAR = 8.0D, FOCUS_MID = 16.0D;
    /** 每 tick 基础锁定值；实际增益再乘距离与目标移动系数。 */
    public static final double FOCUS_RATE = 2.0D;
    /** 无目标时每 tick 衰减。 */
    public static final double FOCUS_DECAY = 3.0D;
    /** 蓄力时每 tick 消耗。 */
    public static final double CHARGE_DRAIN = 2.0D;
    /** 突进窗口（tick）。 */
    public static final int LUNGE_TICKS = 6;
    /** 挥空后摇、命中后摇（tick）。 */
    public static final int RECOVER_MISS_TICKS = 24, RECOVER_HIT_TICKS = 8;
    /** 突进窗口内的额外判定距离。 */
    public static final double LUNGE_RANGE_BONUS = 0.5D;
    /** 扑击（锁定满）额外判定距离。 */
    public static final double EMPOWERED_RANGE_BONUS = 2.0D;
    /** 扑击命中额外倒地时长（tick）。 */
    public static final int EMPOWERED_BLEED_BONUS = 200;
    /** 本能（专长主动技能）窗口与蓄力时间。 */
    public static final int INSTINCT_TICKS = 60, INSTINCT_CHARGE_TICKS = 8;
    /** 各相移速倍率。 */
    public static final double CHARGE_SPEED = 0.55D, RECOVER_SPEED = 0.85D, INSTINCT_SPEED = 1.20D;

    /** 一次出击的结算输入；由释放瞬间的锁定值决定是否升级为扑击。 */
    public record Strike(boolean empowered, double rangeBonus, int bleedBonus) { }

    public record Snapshot(Phase phase, long phaseUntil, int focus, boolean empowered, long instinctUntil) { }

    private Phase phase = Phase.STALK;
    private long phaseUntil;
    private double focus;
    private boolean empowered;
    private long instinctUntil;
    private long lockAnnouncedAt = -1;

    public Phase phase() { return phase; }
    public long phaseUntil() { return phaseUntil; }
    public int focus() { return (int) Math.round(focus); }
    public boolean empowered() { return empowered; }
    public long instinctUntil() { return instinctUntil; }
    public Snapshot snapshot() { return new Snapshot(phase, phaseUntil, focus(), empowered, instinctUntil); }
    public boolean instinct(long now) { return now < instinctUntil; }

    /** 只有回到 STALK 才能重新起手，避免后摇中连点。 */
    public boolean canCharge(long now) {
        advance(now);
        return phase == Phase.STALK;
    }

    public void onCharge(long now) {
        phase = Phase.CHARGE;
        phaseUntil = Long.MAX_VALUE;
    }

    /** 蓄力被打断（被冲撞、被控制、换槽、松手未达最短蓄力）。 */
    public void cancel(long now) {
        if (phase == Phase.CHARGE) { phase = Phase.STALK; phaseUntil = now; }
    }

    /** 松手瞬间结算：锁定满则升级为扑击并清空锁定值。 */
    public Strike beginLunge(long now) {
        empowered = focus >= FOCUS_MAX;
        if (empowered) focus = 0;
        phase = Phase.LUNGE;
        phaseUntil = now + LUNGE_TICKS;
        return new Strike(empowered, LUNGE_RANGE_BONUS + (empowered ? EMPOWERED_RANGE_BONUS : 0D),
                empowered ? EMPOWERED_BLEED_BONUS : 0);
    }

    /** 命中：扑击与本能期间免后摇，其余进入短后摇。 */
    public void onHit(long now, boolean empoweredStrike) {
        onHit(now, empoweredStrike, RECOVER_HIT_TICKS);
    }

    /**
     * 命中并指定后摇长度：裂爪的终结技收益最大，因此后摇也比普通命中更长。
     *
     * @param recoverTicks 非免后摇情况下应进入的后摇长度
     */
    public void onHit(long now, boolean empoweredStrike, int recoverTicks) {
        if (empoweredStrike || instinct(now)) { phase = Phase.STALK; phaseUntil = now; return; }
        phase = Phase.RECOVER;
        phaseUntil = now + Math.max(1, recoverTicks);
    }

    /** 挥空：固定长后摇，给目标真正的逃脱窗口。 */
    public void onMiss(long now) {
        phase = Phase.RECOVER;
        phaseUntil = now + RECOVER_MISS_TICKS;
    }

    /** 专长主动技能「狩猎本能」：锁定立即充满，并在窗口内加速与免后摇。 */
    public void activateInstinct(long now) {
        focus = FOCUS_MAX;
        instinctUntil = now + INSTINCT_TICKS;
        phase = Phase.STALK;
        phaseUntil = now;
    }

    /** 蓄力所需 tick；本能期间固定 8，否则用专长算出的值。 */
    public int chargeTicks(long now, int specializationTicks) {
        return instinct(now) ? Math.min(INSTINCT_CHARGE_TICKS, Math.max(1, specializationTicks)) : Math.max(1, specializationTicks);
    }

    /** 当前相位的移速倍率。 */
    public double speedFactor(long now) {
        advance(now);
        double base = switch (phase) {
            case CHARGE -> CHARGE_SPEED;
            case RECOVER -> RECOVER_SPEED;
            default -> 1.0D;
        };
        return instinct(now) ? base * INSTINCT_SPEED : base;
    }

    /** 由调用方给出「本 tick 最值得锁定的目标」的距离与移动状态；无目标传 0。 */
    public void tick(long now, double gain) {
        tick(now, gain, false);
    }

    /**
     * @param holdFocus true 表示本 tick 锁定值不衰减（织网的蛛网阵内）；蓄力相仍然照常消耗，
     *                  否则阵内蹲守会变成无限满锁定
     */
    public void tick(long now, double gain, boolean holdFocus) {
        advance(now);
        switch (phase) {
            case STALK -> {
                if (gain > 0) focus = Math.min(FOCUS_MAX, focus + gain);
                else if (!holdFocus) focus = Math.max(0, focus - FOCUS_DECAY);
            }
            case CHARGE -> focus = Math.max(0, focus - CHARGE_DRAIN);
            default -> { }
        }
    }

    /** 首次达到满锁定返回 true，供调用方播一次提示音。 */
    public boolean consumeLockAnnouncement(long now) {
        if (focus < FOCUS_MAX) { lockAnnouncedAt = -1; return false; }
        if (lockAnnouncedAt == now) return false;
        lockAnnouncedAt = now;
        return true;
    }

    public void clear() {
        phase = Phase.STALK;
        phaseUntil = 0;
        focus = 0;
        empowered = false;
        instinctUntil = 0;
        lockAnnouncedAt = -1;
    }

    /** 相位到期自动回到 STALK；LUNGE 到期视为本次出击结束。 */
    private void advance(long now) {
        if (phase == Phase.STALK || phase == Phase.CHARGE) return;
        if (now < phaseUntil) return;
        phase = Phase.STALK;
        phaseUntil = now;
        empowered = false;
    }

    /**
     * 距离与目标移动状态决定的锁定增益；无目标或超距返回 0。
     *
     * @param distance 水平距离（格）
     * @param speedSqr 目标水平速度平方
     * @param sprinting 目标是否疾跑
     */
    public static double gain(double distance, double speedSqr, boolean sprinting) {
        if (distance > FOCUS_RANGE) return 0;
        double byDistance = distance <= FOCUS_NEAR ? 1.0D : distance <= FOCUS_MID ? 0.75D : 0.5D;
        double byMotion = sprinting ? 1.4D : speedSqr > 0.0144D ? 1.0D : 0.6D;
        return FOCUS_RATE * byDistance * byMotion;
    }

    /** 受害者心跳间隔：锁定越满越急促（40 → 12 tick）。 */
    public static int heartbeatInterval(int focus) {
        int clamped = Math.max(0, Math.min(100, focus));
        return 40 - Math.round(28 * (clamped / 100F));
    }
}
