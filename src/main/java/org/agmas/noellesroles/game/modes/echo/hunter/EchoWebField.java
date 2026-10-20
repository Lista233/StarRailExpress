package org.agmas.noellesroles.game.modes.echo.hunter;

import java.util.UUID;

/**
 * 织网职业的黏网场地：一个贴地的圆形区域，范围内猎物减速。
 *
 * <p>两种场地共用同一份数据结构，只是半径、时长与减速倍率不同：
 * <ul>
 * <li>{@link Kind#SPLAT} 蛛丝弹的落点残留——脱靶也有收益，这是织网与裂爪最大的手感差别；</li>
 * <li>{@link Kind#NEST} 主动技能「蛛网阵」——更大更久，并且**主人的锁定值在阵内不衰减**。</li>
 * </ul>
 *
 * <p>场地只做减速与「锁定不衰减」，不做伤害；伤害仍然只有服务器射线与机会数。
 * 本类不读 Minecraft 世界，可以完整单元测试。
 */
public final class EchoWebField {
    public enum Kind { SPLAT, NEST }

    /** 落点黏网半径（格）。 */
    public static final double SPLAT_RADIUS = 2.2D;
    /** 蛛网阵半径（格）。 */
    public static final double NEST_RADIUS = 4.5D;
    /** 落点黏网减速后的移速倍率（-45%）。 */
    public static final double SPLAT_SLOW = 0.55D;
    /** 蛛网阵减速后的移速倍率（-55%）。 */
    public static final double NEST_SLOW = 0.45D;
    /** 落点黏网持续 tick（5 秒）。 */
    public static final int SPLAT_TICKS = 100;
    /** 蛛网阵持续 tick（12 秒）。 */
    public static final int NEST_TICKS = 240;
    /** 落在黏网上的猎物被缠住的额外倒地时长（tick）= 3 秒。 */
    public static final int SPLAT_BIND_TICKS = 60;
    /** 阵内主人的锁定不衰减；落点黏网没有这个待遇。 */
    public static final boolean NEST_HOLDS_FOCUS = true;

    private final UUID owner;
    private final Kind kind;
    private final double x, y, z;
    private final double radius;
    private final double slow;
    private final long expiresAt;

    public EchoWebField(UUID owner, Kind kind, double x, double y, double z, long now) {
        this.owner = owner;
        this.kind = kind == null ? Kind.SPLAT : kind;
        this.x = x; this.y = y; this.z = z;
        this.radius = this.kind == Kind.NEST ? NEST_RADIUS : SPLAT_RADIUS;
        this.slow = this.kind == Kind.NEST ? NEST_SLOW : SPLAT_SLOW;
        this.expiresAt = now + (this.kind == Kind.NEST ? NEST_TICKS : SPLAT_TICKS);
    }

    public UUID owner() { return owner; }
    public Kind kind() { return kind; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double radius() { return radius; }
    /** 范围内移速倍率（1 表示不减速）。 */
    public double slow() { return slow; }
    public long expiresAt() { return expiresAt; }
    public boolean expired(long now) { return now >= expiresAt; }
    /** 阵内主人的锁定值不衰减。 */
    public boolean holdsFocus() { return kind == Kind.NEST && NEST_HOLDS_FOCUS; }
    /** 命中该场地内的猎物时额外延长的倒地时长。 */
    public int bindTicks() { return kind == Kind.SPLAT ? SPLAT_BIND_TICKS : 0; }

    /** 水平包含判定：垂直方向不设限，避免玩家站在斜坡上时判定突然失效。 */
    public boolean contains(double px, double pz) {
        double dx = px - x, dz = pz - z;
        return dx * dx + dz * dz <= radius * radius;
    }

    /** 场地是否仍覆盖该点；把「过期」与「包含」合成一次调用，减少调用方漏判。 */
    public boolean covers(long now, double px, double pz) {
        return !expired(now) && contains(px, pz);
    }
}
