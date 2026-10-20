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
        assertFalse(state.releaseCutter(23)); assertFalse(state.beginCutter(72));
        assertTrue(state.beginCutter(73));
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
}
