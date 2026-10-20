package org.agmas.noellesroles.game.modes.echo.hunter;

/**
 * 裂爪职业的「三段连击」纯状态机。
 *
 * <pre>
 *   第一段 ──命中──▶ 第二段 ──命中──▶ 第三段（终结技） ──命中──▶ 第一段
 *     │                │                    │
 *     └──挥空──────────┴──挥空──────────────┴──挥空──▶ 第一段
 *   任意两段之间超过 {@link #WINDOW_TICKS} tick 未命中 ──▶ 第一段
 * </pre>
 *
 * <p>连段只改变「本次出击的结算参数」，不参与命中判定：调用方先用 {@link #swing(long)}
 * 取到本次的判定距离与出血层数，再按真实命中结果调用 {@link #onHit} 或 {@link #onMiss}。
 * 这样判定权威仍然只有服务器射线，连段只是把「打中了」这件事记账。
 *
 * <p>本类不读 Minecraft 世界、不发包、不碰玩家属性，因此可以完整单元测试。
 */
public final class EchoRendCombo {
    /** 两段之间允许的最长间隔（tick）；超时视为断连。 */
    public static final int WINDOW_TICKS = 18;
    /** 连段段数；第三段为终结技。 */
    public static final int MAX_STAGE = 3;
    /** 每一层出血的额外倒地时长（tick）= 2 秒。 */
    public static final int STACK_TICKS = 40;
    /** 各段的额外判定距离（格）：前两段贴脸，终结技向前扑。 */
    public static final double[] REACH_BONUS = {0.0D, 0.3D, 1.0D};
    /** 各段命中追加的出血层数：只有第二段开始叠，终结技一次叠满。 */
    public static final int[] BLEED_STACKS = {0, 1, 3};
    /** 各段命中后的后摇（tick）；终结技后摇最长，因为收益最大。 */
    public static final int[] RECOVER_TICKS = {6, 8, 14};

    /**
     * 一次出击的结算输入。
     *
     * @param stage       本次是第几段（1~{@link #MAX_STAGE}）
     * @param finisher    是否终结技
     * @param reachBonus  额外判定距离（格）
     * @param bleedStacks 命中后追加的出血层数
     */
    public record Swing(int stage, boolean finisher, double reachBonus, int bleedStacks) {
        /** 命中后追加的倒地时长（tick）。 */
        public int bleedTicks() { return bleedStacks * STACK_TICKS; }
        /** 命中后应进入的后摇（tick）。 */
        public int recoverTicks() { return RECOVER_TICKS[Math.max(0, Math.min(RECOVER_TICKS.length - 1, stage - 1))]; }
    }

    /** 未进入连段时使用的第一段结算；调用方拿它当「原版一刀」。 */
    public static final Swing OPENING = new Swing(1, false, REACH_BONUS[0], BLEED_STACKS[0]);

    private int stage = 1;
    private long windowUntil;

    public int stage() { return stage; }
    public long windowUntil() { return windowUntil; }
    public boolean finisherReady() { return stage >= MAX_STAGE; }
    /** 距离断连还剩多少 tick；未在连段中时为 0。 */
    public long windowRemaining(long now) { return stage <= 1 ? 0 : Math.max(0, windowUntil - now); }

    /** 取本次出击的结算参数；先按窗口到期把连段拉回第一段，因此不会读到过期状态。 */
    public Swing swing(long now) {
        expire(now);
        int index = Math.max(0, Math.min(MAX_STAGE - 1, stage - 1));
        return new Swing(stage, stage >= MAX_STAGE, REACH_BONUS[index], BLEED_STACKS[index]);
    }

    /** 命中：非终结技推进一段并刷新窗口，终结技打完后回到第一段。 */
    public void onHit(long now, Swing swing) {
        if (swing == null || swing.finisher() || swing.stage() >= MAX_STAGE) { stage = 1; windowUntil = 0; return; }
        stage = Math.max(1, swing.stage()) + 1;
        windowUntil = now + WINDOW_TICKS;
    }

    /** 挥空：连段直接归零，给出真实的惩罚窗口。 */
    public void onMiss(long now) {
        stage = 1;
        windowUntil = 0;
    }

    /** 主动技能「撕裂突进」把连段直接推到终结技。 */
    public void forceFinisher(long now) {
        stage = MAX_STAGE;
        windowUntil = now + WINDOW_TICKS;
    }

    /** 窗口到期后回到第一段；重复调用无副作用。 */
    public void expire(long now) {
        if (stage > 1 && now >= windowUntil) { stage = 1; windowUntil = 0; }
    }

    public void clear() { stage = 1; windowUntil = 0; }
}
