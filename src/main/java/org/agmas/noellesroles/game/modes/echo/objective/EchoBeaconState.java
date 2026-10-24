package org.agmas.noellesroles.game.modes.echo.objective;

import org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig;

/** 百万分之tick定点充能；任意受支持充能时长都不逐tick截断总进度。 */
public final class EchoBeaconState {
    public static final int REQUIRED_CRYSTALS=3, FULL_WORK=6000;
    private static final long SCALE=1_000_000L;
    private final EchoRuleConfig.Beacon rules;
    private final long full;
    private int delivered;
    private long work,lockedUntil,pausedUntil,nextPolluteAt;
    private boolean stable;
    public EchoBeaconState() { this(EchoRuleConfig.DEFAULT.beacon()); }
    public EchoBeaconState(EchoRuleConfig.Beacon rules) { this.rules=java.util.Objects.requireNonNull(rules); full=rules.chargeTicks()*SCALE; if(full<=0) throw new IllegalArgumentException("充能时长必须大于0"); }
    public int delivered() { return delivered; }
    public int requiredCrystals() { return rules.requiredCrystals(); }
    public int progressPermille() { return (int)(work*1000/full); }
    public boolean active() { return work>=full; }
    public long lockedUntil() { return lockedUntil; }
    public long pausedUntil() { return pausedUntil; }
    public long nextPolluteAt() { return nextPolluteAt; }
    public boolean stable() { return stable; }
    public boolean canSubmit() { return delivered<rules.requiredCrystals(); }
    public boolean submit() { if(!canSubmit()) return false; delivered++; return true; }
    public boolean tick(long now,int escorts) {
        if(escorts<0) throw new IllegalArgumentException("护送人数不能为负");
        if(canSubmit() || active() || now<lockedUntil || now<pausedUntil) return false;
        work=Math.min(full,work+SCALE+Math.round(rules.escortBonus()*SCALE)*Math.min(rules.maxEscorts(),escorts));
        return active();
    }
    public boolean canPollute(long now) { return !canSubmit() && !active() && now>=lockedUntil && now>=nextPolluteAt; }
    public boolean pollute(long now,boolean strong) {
        if(!canPollute(now)) return false;
        if(stable) stable=false;
        else work=Math.max(0,work-Math.round(full*(strong?rules.strongRollback():rules.rollback())));
        lockedUntil=now+rules.pollutionLockTicks(); nextPolluteAt=now+rules.pollutionIntervalTicks(); return true;
    }
    public boolean stabilize() { if(stable || active()) return false; stable=true; return true; }
    public boolean purify(long now) { if(active() || now>=lockedUntil) return false; lockedUntil=now; return true; }
    public boolean pause(long now,int ticks) { if(ticks<=0 || canSubmit() || active() || now<pausedUntil) return false; pausedUntil=now+ticks; return true; }
}
