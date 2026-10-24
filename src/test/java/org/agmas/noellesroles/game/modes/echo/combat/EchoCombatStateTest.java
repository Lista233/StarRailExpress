package org.agmas.noellesroles.game.modes.echo.combat;

import org.agmas.noellesroles.game.modes.echo.state.EchoLifeState;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoRoundClock;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EchoCombatStateTest {
    @Test void twoWolvesSameTickCannotFinishDownedTarget() {
        var state=new EchoCombatState();
        assertEquals(EchoCombatState.Hit.DOWNED,state.hit(10,false,false));
        assertEquals(EchoCombatState.Hit.IGNORED,state.hit(10,false,false));
        assertEquals(910,state.bleedUntil());
        assertEquals(EchoLifeState.DOWNED,state.life());
    }
    @Test void rescuedTargetHasOnlyOneRemainingOpportunity() {
        var state=new EchoCombatState(); state.hit(0,false,false);
        assertTrue(state.rescue(100)); assertEquals(EchoLifeState.WOUNDED,state.life());
        assertEquals(EchoCombatState.Hit.IGNORED,state.hit(219,false,false));
        assertEquals(EchoCombatState.Hit.ELIMINATED,state.hit(220,false,false));
        assertEquals(EchoCombatState.Hit.IGNORED,state.hit(221,false,false));
    }
    @Test void activeInteractionCancelsRescueProtection() {
        var state=new EchoCombatState(); state.hit(0,false,false); state.rescue(100);
        state.activeAction(); assertFalse(state.protectedAt(100));
        assertEquals(EchoCombatState.Hit.ELIMINATED,state.hit(100,false,false));
    }
    @Test void bleedDeadlineWinsOverRescueAtExactTick() {
        var state=new EchoCombatState(); state.hit(20,false,false);
        assertTrue(state.canBeRescued(919)); assertFalse(state.rescue(920));
        assertTrue(state.expireBleed(920)); assertFalse(state.expireBleed(920));
        assertFalse(state.rescue(921)); assertEquals(EchoLifeState.ELIMINATED,state.life());
    }
    @Test void rescueBeforeDeadlineClearsBleed() {
        var state=new EchoCombatState(); state.hit(20,false,false);
        assertTrue(state.rescue(919)); assertFalse(state.expireBleed(920));
        assertEquals(0,state.bleedUntil());
    }
    @Test void chargeRequiresTwelveModeTicksAndMissStillCoolsDown() {
        var state=new EchoCombatState(); assertTrue(state.beginCutter(0));
        assertFalse(state.releaseCutter(11)); assertEquals(0,state.snapshot().cutterReadyAt());
        assertTrue(state.beginCutter(11)); assertTrue(state.releaseCutter(23));
        assertFalse(state.releaseCutter(23)); assertFalse(state.beginCutter(82));
        assertTrue(state.beginCutter(83));
    }
    @Test void controlCancelsChargeAndResistsUntilEightSecondsAfterEnd() {
        var state=new EchoCombatState(); state.beginCutter(0);
        assertTrue(state.control(12,25)); assertFalse(state.releaseCutter(12));
        assertFalse(state.canAct(36)); assertTrue(state.canAct(37));
        assertFalse(state.control(196,25)); assertTrue(state.control(197,25));
    }
    @Test void onlySuccessfulShoveConsumesOwnCooldown() {
        var state=new EchoCombatState(); assertTrue(state.shoveReady(0));
        assertTrue(state.shoveReady(1)); state.consumeShove(1);
        assertFalse(state.shoveReady(500)); assertTrue(state.shoveReady(501));
    }
    @Test void environmentResetsWolfButForceEliminates() {
        var state=new EchoCombatState();
        assertEquals(EchoCombatState.Hit.RESET_INTERCEPTOR,state.hit(0,false,true));
        assertEquals(EchoLifeState.HEALTHY,state.life()); assertTrue(state.controlled(59));
        assertFalse(state.controlled(60)); assertEquals(EchoCombatState.Hit.IGNORED,state.hit(199,false,true));
        assertEquals(EchoCombatState.Hit.RESET_INTERCEPTOR,state.hit(200,false,true));
        assertEquals(EchoCombatState.Hit.ELIMINATED,state.hit(201,true,true));
    }
    @Test void forceBypassesFirstOpportunityAndProtection() {
        var state=new EchoCombatState();
        assertEquals(EchoCombatState.Hit.ELIMINATED,state.hit(0,true,false));
        var rescued=new EchoCombatState(); rescued.hit(0,false,false); rescued.rescue(1);
        assertEquals(EchoCombatState.Hit.ELIMINATED,rescued.hit(1,true,false));
    }
    @Test void terminalDepartureCannotBeHitRescuedOrControlled() {
        for(var life:new EchoLifeState[]{EchoLifeState.ESCAPED,EchoLifeState.RETIRED,EchoLifeState.ELIMINATED}) {
            var state=new EchoCombatState(); state.depart(life);
            assertEquals(EchoCombatState.Hit.IGNORED,state.hit(1000,true,false));
            assertFalse(state.rescue(1000)); assertFalse(state.control(1000,25)); assertFalse(state.canAct(1000));
        }
    }
    @Test void frozenClockDoesNotAdvanceBleedingChargeOrControl() {
        var clock=new EchoRoundClock(); var state=new EchoCombatState();
        state.hit(0,false,false);
        for(int n=0;n<1000;n++) { clock.capture(n,100,true); clock.finish(100,true); }
        assertEquals(0,clock.matchTick()); assertFalse(state.expireBleed(clock.matchTick()));
        var wolf=new EchoCombatState(); wolf.beginCutter(clock.matchTick());
        assertFalse(wolf.releaseCutter(clock.matchTick()));
    }
    @Test void repeatedUnderchargedCancellationsNeverConsumeCooldown() {
        var state=new EchoCombatState();
        for(int now=0;now<100;now++) {
            assertTrue(state.beginCutter(now)); assertFalse(state.releaseCutter(now+1));
            assertEquals(0,state.snapshot().cutterReadyAt());
        }
        assertFalse(state.releaseCutter(1000),"取消后不能靠旧蓄力时间出手");
    }
    @Test void specializationAndInstinctCannotReduceThreeSecondFloor() {
        for(var spec:new org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization[]{
                org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization.HUNTER,
                org.agmas.noellesroles.game.modes.echo.state.EchoSpecialization.SABOTEUR}) {
            var state=new EchoCombatState(spec.combat(org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT));
            var loop=new EchoHuntLoop(); loop.activateInstinct(0);
            int charge=loop.chargeTicks(0,state.cutterChargeTicks());
            assertTrue(state.beginCutter(0)); assertTrue(state.releaseCutter(charge,charge));
            assertTrue(state.snapshot().cutterReadyAt()>=charge+60);
            loop.onHit(charge,true,1);
            assertTrue(loop.canCharge(charge)); assertFalse(state.beginCutter(charge+59));
        }
    }
    @Test void onlyActualDownTransitionAddsTwentySeconds() {
        var attacker=new EchoCombatState(); var victim=new EchoCombatState();
        assertTrue(attacker.beginCutter(0)); assertTrue(attacker.releaseCutter(12));
        assertTrue(attacker.recordCutterHit(12,victim.hit(12,false,false)));
        assertEquals(412,attacker.snapshot().cutterReadyAt());
        assertFalse(attacker.recordCutterHit(13,victim.hit(13,false,false)));
        assertTrue(victim.rescue(20));
        assertFalse(attacker.recordCutterHit(21,victim.hit(21,false,false)),"救援保护不算击倒");
        victim.activeAction();
        assertFalse(attacker.recordCutterHit(22,victim.hit(22,false,false)),"淘汰不是首次击倒");
        var ally=new EchoCombatState();
        assertFalse(attacker.recordCutterHit(23,ally.hit(23,false,true)),"友军复位不能触发击倒冷却");
        assertEquals(412,attacker.snapshot().cutterReadyAt());
    }
    @Test void multiTargetAndDeferredHitsAlwaysTakeMaximumDeadline() {
        var attacker=new EchoCombatState();
        assertTrue(attacker.beginCutter(0)); assertTrue(attacker.releaseCutter(12));
        attacker.recordCutterHit(20,new EchoCombatState().hit(20,false,false));
        assertEquals(420,attacker.snapshot().cutterReadyAt());
        attacker.recordCutterHit(20,new EchoCombatState().hit(20,false,false));
        assertEquals(420,attacker.snapshot().cutterReadyAt(),"同 tick 横扫不叠加惩罚");
        attacker.recordCutterHit(35,new EchoCombatState().hit(35,false,false));
        assertEquals(435,attacker.snapshot().cutterReadyAt(),"延迟弹命中以命中 tick 起算");
        assertFalse(attacker.releaseCutter(36,1)); assertFalse(attacker.beginCutter(434));
        assertEquals(435,attacker.snapshot().cutterReadyAt(),"后续出击不能把 20 秒改为 3 秒");
        assertTrue(attacker.beginCutter(435));
    }
    @Test void longerConfiguredCooldownIsNeverShortenedByDown() {
        var base=org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.DEFAULT.combat();
        var state=new EchoCombatState(new org.agmas.noellesroles.game.modes.echo.rules.EchoRuleConfig.Combat(
                12,600,base.cutterRange(),base.shoveCooldownTicks(),base.shoveControlTicks(),base.controlImmunityTicks(),
                base.bleedTicks(),base.rescueTicks(),base.rescueProtectionTicks(),base.environmentControlTicks(),
                base.environmentProtectionTicks(),base.rescueRange(),base.shoveRange()));
        assertTrue(state.beginCutter(0)); assertTrue(state.releaseCutter(12));
        state.recordCutterHit(20,EchoCombatState.Hit.DOWNED);
        assertEquals(612,state.snapshot().cutterReadyAt());
    }
    @Test void instinctAndDashFinisherCannotBypassDownCooldown() {
        var state=new EchoCombatState(); var loop=new EchoHuntLoop();
        assertTrue(state.beginCutter(0)); assertTrue(state.releaseCutter(12));
        state.recordCutterHit(12,EchoCombatState.Hit.DOWNED);
        loop.activateInstinct(13); loop.onHit(13,true,1);
        var combo=new org.agmas.noellesroles.game.modes.echo.hunter.EchoRendCombo(); combo.forceFinisher(13);
        assertTrue(combo.swing(13).finisher()); assertTrue(loop.canCharge(13));
        assertFalse(state.beginCutter(13)); assertFalse(state.beginCutter(411));
        assertTrue(state.beginCutter(412));
    }
    @Test void frozenClockKeepsWeaponRemainingAndSnapshotDeadline() {
        var clock=new EchoRoundClock(); var state=new EchoCombatState();
        state.recordCutterHit(clock.matchTick(),EchoCombatState.Hit.DOWNED);
        long savedDeadline=state.snapshot().cutterReadyAt();
        for(int n=0;n<1000;n++) { clock.capture(n,100,true); clock.finish(100,true); }
        assertEquals(400,state.cutterRemaining(clock.matchTick()));
        assertEquals(savedDeadline,state.snapshot().cutterReadyAt());
        assertFalse(state.beginCutter(clock.matchTick()));
    }
}
