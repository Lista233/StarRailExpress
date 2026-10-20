package org.agmas.noellesroles.game.modes.echo.hunter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 织网职业的蛛丝弹：一条纯模拟的抛物线。
 *
 * <p>为什么不注册新的实体类型：蛛丝弹只需要「沿视线飞出、受重力、碰到东西就停」，
 * 注册实体要进注册表、要处理区块卸载与重连，成本远高于收益。这里把弹道做成纯数学，
 * 服务端每 tick 推进一次并用方块射线与球体判定决定命中；客户端只收到命中/落点的
 * 一次性特效包。丢包只丢表现，不丢判定。
 *
 * <p>本类不读 Minecraft 世界、不发包，只保存坐标与速度，因此可以完整单元测试。
 */
public final class EchoWebBolt {
    /** 初速（格/tick）。比玩家步行快，但明显慢于原版箭，留出闪避空间。 */
    public static final double SPEED = 1.4D;
    /** 每 tick 的重力加速度（格/tick²）；抛物线要能被看出来。 */
    public static final double GRAVITY = 0.05D;
    /** 命中判定球半径（格）；略大于玩家半宽，避免擦身而过。 */
    public static final double HIT_RADIUS = 0.45D;
    /** 最长存活 tick；到期视为脱靶，仍然会留下黏网。 */
    public static final int MAX_TICKS = 40;
    /** 每 tick 的推进子步数：初速 1.4 格/tick 时单步跨 0.7 格，两小步足够避免穿人。 */
    public static final int SUBSTEPS = 2;

    /** 一个采样点；保持纯 double，避免把 Minecraft 类型带进单元测试。 */
    public record Point(double x, double y, double z) { }

    private final UUID owner;
    private final double originX, originY, originZ;
    private double x, y, z;
    private double vx, vy, vz;
    private int ticks;
    private boolean finished;

    public EchoWebBolt(UUID owner, double x, double y, double z, double vx, double vy, double vz) {
        this.owner = owner;
        this.originX = x; this.originY = y; this.originZ = z;
        this.x = x; this.y = y; this.z = z;
        this.vx = vx; this.vy = vy; this.vz = vz;
    }

    /** 由「眼睛位置 + 视线方向」构造；方向会先归一化再乘 {@link #SPEED}。 */
    public static EchoWebBolt aim(UUID owner, double eyeX, double eyeY, double eyeZ,
                                  double dirX, double dirY, double dirZ) {
        double length = Math.sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ);
        if (!(length > 1.0E-6D) || !Double.isFinite(length)) return new EchoWebBolt(owner, eyeX, eyeY, eyeZ, 0D, 0D, SPEED);
        return new EchoWebBolt(owner, eyeX, eyeY, eyeZ,
                dirX / length * SPEED, dirY / length * SPEED, dirZ / length * SPEED);
    }

    public UUID owner() { return owner; }
    public double x() { return x; }
    public double y() { return y; }
    public double z() { return z; }
    public double vx() { return vx; }
    public double vy() { return vy; }
    public double vz() { return vz; }
    public int ticks() { return ticks; }
    public Point position() { return new Point(x, y, z); }
    public Point origin() { return new Point(originX, originY, originZ); }
    /** 已结束（命中或脱靶）的弹不再推进。 */
    public boolean finished() { return finished; }
    public void finish() { finished = true; }

    /**
     * 推进 {@code 1 / SUBSTEPS} 个 tick：先按当前速度位移，再累加重力。
     *
     * @return 推进后的位置；已结束的弹返回当前位置且不再变化
     */
    public Point step() {
        if (finished) return position();
        double fraction = 1D / SUBSTEPS;
        x += vx * fraction;
        y += vy * fraction;
        z += vz * fraction;
        vy -= GRAVITY * fraction;
        return position();
    }

    /** 一个完整 tick 推进（{@link #SUBSTEPS} 次小步），供纯模拟与测试使用。 */
    public Point advanceTick() {
        Point last = position();
        for (int i = 0; i < SUBSTEPS; i++) last = step();
        countTick();
        return last;
    }

    /**
     * 只做时间记账：服务端为了逐子步判人判墙，会自己调用 {@link #step()}，
     * 因此必须显式告知「这一 tick 已经过去了」，否则弹永远不会过期。
     */
    public void countTick() {
        ticks++;
        if (ticks >= MAX_TICKS) finished = true;
    }

    /** 目标中心是否落在本弹的命中球内。 */
    public boolean reaches(double targetX, double targetY, double targetZ, double radius) {
        double dx = x - targetX, dy = y - targetY, dz = z - targetZ;
        double limit = radius + HIT_RADIUS;
        return dx * dx + dy * dy + dz * dz <= limit * limit;
    }

    /** 脱靶：存活超过 {@link #MAX_TICKS}。 */
    public boolean expired() { return ticks >= MAX_TICKS; }

    /** 纯模拟整条弹道，只给测试与文档用；真实战斗由服务端逐 tick 推进。 */
    public static List<Point> trajectory(UUID owner, double eyeX, double eyeY, double eyeZ,
                                         double dirX, double dirY, double dirZ, int ticks) {
        EchoWebBolt bolt = aim(owner, eyeX, eyeY, eyeZ, dirX, dirY, dirZ);
        List<Point> points = new ArrayList<>(Math.max(0, Math.min(ticks, MAX_TICKS)));
        points.add(bolt.position());
        for (int i = 0; i < ticks && !bolt.finished(); i++) points.add(bolt.advanceTick());
        return points;
    }
}
