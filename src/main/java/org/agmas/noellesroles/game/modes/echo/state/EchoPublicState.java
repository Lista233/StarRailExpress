package org.agmas.noellesroles.game.modes.echo.state;

import java.util.*;
import org.agmas.noellesroles.game.modes.echo.rules.EchoRosterPolicy;

/** 公开目标事实与信标点位；不含玩家UUID/位置、阵营、手牌或私人契约。 */
public record EchoPublicState(EchoPhase phase,long tick,int remaining,boolean paused,int escaped,int quota,
                              boolean coreOpen,long coreUntil,boolean evacuationOpen,long closedUntil,
                              boolean overloadUsed,List<Beacon> beacons) {
    public record Point(double x,double y,double z) {
        public Point { if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || Math.abs(x)>30000000 || Math.abs(y)>30000000 || Math.abs(z)>30000000) throw new IllegalArgumentException("无效公开信标位置"); }
    }
    public record Beacon(String id,int delivered,int required,int progress,boolean active,boolean stable,long lockedUntil,long pausedUntil,Point position,long submissionUntil) {
        public Beacon(String id,int delivered,int required,int progress,boolean active,boolean stable,long lockedUntil,long pausedUntil) { this(id,delivered,required,progress,active,stable,lockedUntil,pausedUntil,null,0); }
        public Beacon {
            if(id==null || id.length()>64 || delivered<0 || required<1 || required>64 || delivered>required
                    || progress<0 || progress>1000 || lockedUntil<0 || pausedUntil<0 || submissionUntil<0) throw new IllegalArgumentException("无效公开信标");
        }
        public boolean charging() { return delivered>=required && !active; }
        public boolean working(long now) { return charging() && now>=lockedUntil && now>=pausedUntil; }
        public boolean highlighted(long now) { return now<submissionUntil; }
    }
    public EchoPublicState {
        Objects.requireNonNull(phase); beacons=List.copyOf(beacons);
        if(tick<0 || remaining<0 || remaining>720000 || escaped<0 || escaped>EchoRosterPolicy.MAX_PLAYERS || quota<1 || quota>EchoRosterPolicy.MAX_PLAYERS
                || coreUntil<0 || closedUntil<0 || beacons.size()>3) throw new IllegalArgumentException("无效公开目标");
        if(beacons.stream().map(Beacon::id).distinct().count()!=beacons.size()) throw new IllegalArgumentException("重复公开信标");
    }
    /** 本地插值最多领先一个同步周期，只显示进度，不能推断完成事实。 */
    public long displayTick(int elapsed) { return tick+(paused?0:Math.max(0,Math.min(10,elapsed))); }
    public int displayRemaining(int elapsed) { return Math.max(0,remaining-(int)(displayTick(elapsed)-tick)); }
    public static long seconds(long deadline,long now) { return Math.max(0,deadline-now+19)/20; }
}
