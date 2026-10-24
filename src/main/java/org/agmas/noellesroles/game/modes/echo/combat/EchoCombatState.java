package org.agmas.noellesroles.game.modes.echo.combat;

import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;

/** 暂停感知的两次机会、蓄力和控制状态；不读取Minecraft生命值。 */
public final class EchoCombatState {
    public enum Hit { IGNORED, DOWNED, ELIMINATED, RESET_INTERCEPTOR }
    /** 出击与真正击倒的硬下限；专长、技能和演出相位均不能缩短。 */
    public static final int MIN_ATTACK_COOLDOWN_TICKS=60, MIN_DOWN_COOLDOWN_TICKS=400;
    public record Snapshot(EchoLifeState life,long bleedUntil,long protectedUntil,long controlledUntil,
                           long controlImmuneUntil,long cutterReadyAt,long shoveReadyAt) { }
    private final org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Combat rules;
    public EchoCombatState() { this(org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.combat()); }
    public EchoCombatState(org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Combat rules) { this.rules=java.util.Objects.requireNonNull(rules); }
    private EchoLifeState life=EchoLifeState.HEALTHY;
    private long bleedUntil,protectedUntil,controlledUntil,controlImmuneUntil,cutterReadyAt,shoveReadyAt,environmentReadyAt;
    private long chargeStartedAt=-1;
    public EchoLifeState life() { return life; }
    public boolean controlled(long now) { return now<controlledUntil; }
    public boolean canAct(long now) { return life.standing() && !controlled(now); }
    public boolean protectedAt(long now) { return now<protectedUntil; }
    public long bleedUntil() { return bleedUntil; }
    public Snapshot snapshot() { return new Snapshot(life,bleedUntil,protectedUntil,controlledUntil,controlImmuneUntil,cutterReadyAt,shoveReadyAt); }

    public boolean beginCutter(long now) {
        if(!canAct(now) || now<cutterReadyAt) return false;
        chargeStartedAt=now; return true;
    }
    public boolean releaseCutter(long now) { return releaseCutter(now,rules.cutterChargeTicks()); }
    /** 本能只缩短有效蓄力要求，不改变武器的服务端冷却下限。 */
    public boolean releaseCutter(long now,int requiredChargeTicks) {
        long start=chargeStartedAt; chargeStartedAt=-1;
        if(start<0 || now-start<Math.max(1,requiredChargeTicks) || !canAct(now) || now<cutterReadyAt) return false;
        cutterReadyAt=Math.max(cutterReadyAt,now+Math.max(MIN_ATTACK_COOLDOWN_TICKS,rules.cutterCooldownTicks()));
        activeAction(); return true;
    }
    public int cutterChargeTicks() { return rules.cutterChargeTicks(); }
    public long cutterRemaining(long now) { return Math.max(0,cutterReadyAt-now); }
    /** 只接收已经完成生命状态转换的结果；多目标、延迟命中一律取最大截止。 */
    public boolean recordCutterHit(long now,Hit hit) {
        if(hit!=Hit.DOWNED) return false;
        cutterReadyAt=Math.max(cutterReadyAt,now+MIN_DOWN_COOLDOWN_TICKS); return true;
    }
    public void cancelCharge() { chargeStartedAt=-1; }
    public boolean shoveReady(long now) { return canAct(now) && now>=shoveReadyAt; }
    public void consumeShove(long now) { shoveReadyAt=now+rules.shoveCooldownTicks(); activeAction(); }
    public boolean control(long now,int duration) {
        if(!life.standing() || duration<=0 || now<controlImmuneUntil || controlled(now)) return false;
        controlledUntil=now+duration; controlImmuneUntil=controlledUntil+rules.controlImmunityTicks(); cancelCharge(); return true;
    }
    public Hit hit(long now,boolean force,boolean interceptor) {
        if(!life.potentiallyEscapable()) return Hit.IGNORED;
        if(force) { eliminate(); return Hit.ELIMINATED; }
        if(interceptor) {
            if(now<environmentReadyAt) return Hit.IGNORED;
            environmentReadyAt=now+rules.environmentProtectionTicks(); controlledUntil=now+rules.environmentControlTicks(); controlImmuneUntil=controlledUntil+rules.controlImmunityTicks();
            cancelCharge(); return Hit.RESET_INTERCEPTOR;
        }
        if(life==EchoLifeState.DOWNED || protectedAt(now)) return Hit.IGNORED;
        cancelCharge();
        if(life==EchoLifeState.HEALTHY) { life=EchoLifeState.DOWNED; bleedUntil=now+rules.bleedTicks(); return Hit.DOWNED; }
        eliminate(); return Hit.ELIMINATED;
    }
    public boolean expireBleed(long now) {
        if(life!=EchoLifeState.DOWNED || now<bleedUntil) return false;
        eliminate(); return true;
    }
    public boolean canBeRescued(long now) { return life==EchoLifeState.DOWNED && now<bleedUntil; }
    /** 扑击命中延长倒地时间；只对正在流血的倒地者生效。 */
    public boolean extendBleed(long now,int extraTicks) {
        if(life!=EchoLifeState.DOWNED || extraTicks<=0 || now>=bleedUntil) return false;
        bleedUntil+=extraTicks; return true;
    }
    public boolean rescue(long now) {
        if(!canBeRescued(now)) return false;
        life=EchoLifeState.WOUNDED; bleedUntil=0; protectedUntil=now+rules.rescueProtectionTicks(); return true;
    }
    public void activeAction() { protectedUntil=0; }
    public void depart(EchoLifeState terminal) {
        if(terminal!=EchoLifeState.ESCAPED && terminal!=EchoLifeState.RETIRED && terminal!=EchoLifeState.ELIMINATED) throw new IllegalArgumentException("非离场状态");
        life=terminal; bleedUntil=0; protectedUntil=0; controlledUntil=0; cancelCharge();
    }
    private void eliminate() { depart(EchoLifeState.ELIMINATED); }
}
