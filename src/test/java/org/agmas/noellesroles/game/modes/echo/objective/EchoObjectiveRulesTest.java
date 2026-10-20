package org.agmas.noellesroles.game.modes.echo.objective;

import org.agmas.noellesroles.game.modes.echo.action.EchoActionService;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EchoObjectiveRulesTest {
    @Test void chargeHasExactDurationAndSupplyCap() {
        for(int escorts=0;escorts<=3;escorts++) {
            EchoBeaconState beacon=new EchoBeaconState();
            assertTrue(beacon.submit()); assertTrue(beacon.submit()); assertTrue(beacon.submit()); assertFalse(beacon.submit());
            int expected=(int)Math.ceil(6000D/(5+escorts));
            for(int tick=0;tick<expected-1;tick++) assertFalse(beacon.tick(tick,escorts));
            assertTrue(beacon.tick(expected-1,escorts)); assertTrue(beacon.active()); assertEquals(1000,beacon.progressPermille());
            assertFalse(beacon.pollute(expected,false));
        }
    }
    @Test void pollutionLockAndSharedCooldownSurvivePurifyAndStableLayer() {
        EchoBeaconState beacon=new EchoBeaconState(); for(int n=0;n<3;n++) beacon.submit();
        for(int n=0;n<600;n++) beacon.tick(n,0);
        assertEquals(500,beacon.progressPermille()); assertTrue(beacon.stabilize()); assertFalse(beacon.stabilize());
        assertTrue(beacon.pollute(600,false)); assertEquals(500,beacon.progressPermille()); assertFalse(beacon.stable());
        assertFalse(beacon.tick(601,3)); assertTrue(beacon.purify(601)); assertFalse(beacon.canPollute(601));
        assertTrue(beacon.pollute(1500,true)); assertEquals(100,beacon.progressPermille());
        assertFalse(beacon.pollute(1500,false)); assertEquals(2400,beacon.nextPolluteAt());
    }
    @Test void pulseAndLockOverlapInsteadOfAddingDurations() {
        EchoBeaconState beacon=new EchoBeaconState(); for(int n=0;n<3;n++) beacon.submit();
        assertTrue(beacon.pollute(0,false)); assertTrue(beacon.pause(0,100));
        for(int tick=0;tick<400;tick++) assertFalse(beacon.tick(tick,3));
        assertEquals(0,beacon.progressPermille()); beacon.tick(400,3); assertTrue(beacon.progressPermille()>0);
    }
    @Test void overloadOnceKeepsCoreOpenAndReopensAfterExactlyThirtySeconds() {
        EchoGateState gates=new EchoGateState();
        assertFalse(gates.overload(0)); assertTrue(gates.openCore(0,300)); assertFalse(gates.openCore(1,300));
        assertTrue(gates.extendCore(1,100,500)); assertTrue(gates.coreOpen(399)); assertFalse(gates.coreOpen(400));
        gates.beginEvacuation(); assertTrue(gates.overload(500)); assertTrue(gates.coreOpen(501));
        assertFalse(gates.evacuationOpen(1099)); assertTrue(gates.evacuationOpen(1100)); assertFalse(gates.overload(1100));
    }
    @Test void keyReplenishmentDoesNotDependOnKeysHeldByPlayers() {
        EchoKeySchedule keys=new EchoKeySchedule(List.of("a","b","c"),0);
        var initial=keys.initial(new Random(1)); assertEquals(2,new HashSet<>(initial).size());
        UUID held=UUID.randomUUID(); assertTrue(keys.occupy(initial.getFirst(),held));
        keys.release(initial.getFirst(),UUID.randomUUID()); assertTrue(keys.at(initial.getFirst()).isPresent());
        keys.release(initial.getFirst(),held);
        assertTrue(keys.due(1199).isEmpty()); assertTrue(keys.due(1200).isPresent()); assertTrue(keys.due(1200).isEmpty());
        for(String altar:List.of("a","b","c")) assertTrue(keys.occupy(altar,UUID.randomUUID()));
        assertTrue(keys.due(2400).isEmpty());
    }
    @Test void leasesAreExclusiveCancelByIdAndDoNotCompleteAfterExpiry() {
        EchoActionService actions=new EchoActionService(); UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        var first=actions.request(a,"node",80,0,0,0,true).orElseThrow();
        assertTrue(actions.request(b,"node",80,0,0,0,true).isEmpty());
        for(int tick=5;tick<=80;tick+=5) {
            assertEquals(first.id(),actions.request(a,"node",80,tick,0,0,true).orElseThrow().id());
            var done=actions.advance(tick,action -> true);
            assertEquals(tick==80?1:0,done.size());
        }
        var newer=actions.request(a,"node",80,81,0,0,true).orElseThrow();
        assertFalse(actions.cancel(a,first.id())); assertTrue(actions.cancel(a,newer.id()));
        actions.request(b,"node",80,82,0,0,true);
        assertTrue(actions.advance(93,action -> true).isEmpty()); assertTrue(actions.current(b).isEmpty());
    }
    @Test void twoNonexclusiveSubmissionsKeepOrderAndOnlyLastSlotCommits() {
        EchoActionService actions=new EchoActionService(); UUID a=UUID.randomUUID(),b=UUID.randomUUID();
        EchoBeaconState beacon=new EchoBeaconState(); beacon.submit(); beacon.submit();
        actions.request(a,"beacon",1,0,0,0,false); actions.request(b,"beacon",1,0,0,0,false);
        var ready=actions.advance(1,action -> true); assertEquals(a,ready.getFirst().player());
        assertTrue(beacon.submit()); assertFalse(beacon.submit()); assertEquals(3,beacon.delivered());
    }
    @Test void renewedThenCancelledLeaseCannotCompleteOnSameTick() {
        EchoActionService actions=new EchoActionService(); UUID player=UUID.randomUUID();
        var action=actions.request(player,"crystal",80,0,0,0,true).orElseThrow();
        actions.request(player,"crystal",80,80,0,0,true);
        assertTrue(actions.cancel(player,action.id()));
        assertTrue(actions.advance(80,a -> true).isEmpty());
    }
    @Test void lastTickBeaconCompletionStartsFullEvacuationAndOverloadDoesNotResetClock() {
        var clock=new org.agmas.noellesroles.game.modes.echo.runtime.EchoRoundClock();
        clock.capture(0,1,false); clock.finish(0,false);
        EchoBeaconState beacon=new EchoBeaconState(); for(int n=0;n<3;n++) beacon.submit();
        for(int tick=0;tick<1199;tick++) beacon.tick(tick,0);
        clock.capture(1,1,false); assertTrue(beacon.tick(1199,0));
        assertTrue(clock.openEvacuation(false));
        assertEquals(org.agmas.noellesroles.game.modes.echo.runtime.EchoRoundClock.Boundary.NONE,clock.finish(2400,false));
        EchoGateState gate=new EchoGateState(); gate.beginEvacuation();
        clock.capture(2,2400,false); assertTrue(gate.overload(clock.matchTick()));
        assertEquals(org.agmas.noellesroles.game.modes.echo.runtime.EchoRoundClock.Boundary.NONE,clock.finish(2399,false));
        assertFalse(gate.evacuationOpen(clock.matchTick()));
    }
}
