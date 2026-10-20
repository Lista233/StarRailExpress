package org.agmas.noellesroles.game.modes.echo.combat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** 狩猎循环的纯状态机：锁定、相位、扑击升级、后摇与本能覆盖。 */
class EchoHuntLoopTest {

    @Test void stalkAccumulatesFocusUpToCap() {
        var loop = new EchoHuntLoop();
        for (int tick = 0; tick < 100; tick++) loop.tick(tick, EchoHuntLoop.gain(4, 0.02, true));
        assertEquals(100, loop.focus());
        assertEquals(EchoHuntLoop.Phase.STALK, loop.phase());
    }

    @Test void focusDecaysWithoutTargetButNeverBelowZero() {
        var loop = new EchoHuntLoop();
        loop.tick(0, 60);
        assertEquals(60, loop.focus());
        for (int tick = 1; tick <= 40; tick++) loop.tick(tick, 0);
        assertEquals(0, loop.focus());
    }

    @Test void chargeDrainsFocusAndCannotRestartUntilStalk() {
        var loop = new EchoHuntLoop();
        loop.tick(0, 100);
        assertTrue(loop.canCharge(1));
        loop.onCharge(1);
        assertEquals(EchoHuntLoop.Phase.CHARGE, loop.phase());
        assertFalse(loop.canCharge(2));
        loop.tick(2, 0);
        assertEquals(98, loop.focus());
        loop.cancel(3);
        assertEquals(EchoHuntLoop.Phase.STALK, loop.phase());
        assertTrue(loop.canCharge(3));
    }

    @Test void fullFocusUpgradesStrikeAndClearsFocus() {
        var loop = new EchoHuntLoop();
        loop.tick(0, 100);
        loop.onCharge(1);
        var strike = loop.beginLunge(1);
        assertTrue(strike.empowered());
        assertEquals(EchoHuntLoop.LUNGE_RANGE_BONUS + EchoHuntLoop.EMPOWERED_RANGE_BONUS, strike.rangeBonus(), 1.0E-6);
        assertEquals(EchoHuntLoop.EMPOWERED_BLEED_BONUS, strike.bleedBonus());
        assertEquals(0, loop.focus());
        assertEquals(EchoHuntLoop.Phase.LUNGE, loop.phase());
    }

    @Test void partialFocusDoesNotUpgradeStrike() {
        var loop = new EchoHuntLoop();
        loop.tick(0, 40);
        loop.onCharge(1);
        var strike = loop.beginLunge(1);
        assertFalse(strike.empowered());
        assertEquals(EchoHuntLoop.LUNGE_RANGE_BONUS, strike.rangeBonus(), 1.0E-6);
        assertEquals(0, strike.bleedBonus());
        assertEquals(40, loop.focus());
    }

    @Test void missEntersLongRecoveryAndHitEntersShortRecovery() {
        var loop = new EchoHuntLoop();
        loop.onCharge(0);
        loop.beginLunge(0);
        loop.onMiss(2);
        assertEquals(EchoHuntLoop.Phase.RECOVER, loop.phase());
        assertEquals(2 + EchoHuntLoop.RECOVER_MISS_TICKS, loop.phaseUntil());
        assertFalse(loop.canCharge(3));
        assertTrue(loop.canCharge(2 + EchoHuntLoop.RECOVER_MISS_TICKS));

        loop.onCharge(30);
        loop.beginLunge(30);
        loop.onHit(30, false);
        assertEquals(EchoHuntLoop.Phase.RECOVER, loop.phase());
        assertEquals(30 + EchoHuntLoop.RECOVER_HIT_TICKS, loop.phaseUntil());
    }

    @Test void empoweredHitSkipsRecovery() {
        var loop = new EchoHuntLoop();
        loop.tick(0, 100);
        loop.onCharge(1);
        loop.beginLunge(1);
        loop.onHit(1, true);
        assertEquals(EchoHuntLoop.Phase.STALK, loop.phase());
    }

    @Test void instinctFillsFocusShortensChargeAndSkipsRecovery() {
        var loop = new EchoHuntLoop();
        loop.activateInstinct(100);
        assertEquals(100, loop.focus());
        assertTrue(loop.instinct(100 + EchoHuntLoop.INSTINCT_TICKS - 1));
        assertFalse(loop.instinct(100 + EchoHuntLoop.INSTINCT_TICKS));
        assertEquals(EchoHuntLoop.INSTINCT_CHARGE_TICKS, loop.chargeTicks(101, 20));
        assertEquals(20, loop.chargeTicks(100 + EchoHuntLoop.INSTINCT_TICKS, 20));
        loop.onCharge(101);
        loop.beginLunge(101);
        loop.onMiss(101);
        // 挥空仍然是长后摇；本能只免除「命中」后摇。
        assertEquals(EchoHuntLoop.Phase.RECOVER, loop.phase());
    }

    @Test void instinctHitSkipsRecovery() {
        var loop = new EchoHuntLoop();
        loop.activateInstinct(0);
        loop.onCharge(1);
        loop.beginLunge(1);
        loop.onHit(1, false);
        assertEquals(EchoHuntLoop.Phase.STALK, loop.phase());
    }

    @Test void speedFactorReflectsPhaseAndInstinct() {
        var loop = new EchoHuntLoop();
        assertEquals(1.0, loop.speedFactor(0), 1.0E-6);
        loop.onCharge(0);
        assertEquals(EchoHuntLoop.CHARGE_SPEED, loop.speedFactor(0), 1.0E-6);
        loop.cancel(2);
        loop.onCharge(3);
        loop.beginLunge(3);
        loop.onMiss(3);
        assertEquals(EchoHuntLoop.RECOVER_SPEED, loop.speedFactor(4), 1.0E-6);
        loop.activateInstinct(100);
        assertEquals(EchoHuntLoop.INSTINCT_SPEED, loop.speedFactor(100), 1.0E-6);
    }

    @Test void heartbeatIntervalIsBoundedAndAccelerates() {
        assertEquals(40, EchoHuntLoop.heartbeatInterval(0));
        assertEquals(12, EchoHuntLoop.heartbeatInterval(100));
        assertTrue(EchoHuntLoop.heartbeatInterval(50) < EchoHuntLoop.heartbeatInterval(10));
        assertEquals(40, EchoHuntLoop.heartbeatInterval(-10));
        assertEquals(12, EchoHuntLoop.heartbeatInterval(999));
    }

    @Test void gainFollowsDistanceAndMotionBands() {
        assertEquals(0, EchoHuntLoop.gain(EchoHuntLoop.FOCUS_RANGE + 1, 0.2, true), 1.0E-6);
        double close = EchoHuntLoop.gain(4, 0.2, true);
        double mid = EchoHuntLoop.gain(12, 0.2, true);
        double far = EchoHuntLoop.gain(20, 0.2, true);
        assertTrue(close > mid && mid > far);
        assertTrue(EchoHuntLoop.gain(4, 0.2, true) > EchoHuntLoop.gain(4, 0.2, false));
        assertTrue(EchoHuntLoop.gain(4, 0.2, false) > EchoHuntLoop.gain(4, 0, false));
    }

    @Test void lockAnnouncementFiresOncePerFullCharge() {
        var loop = new EchoHuntLoop();
        loop.tick(0, 60);
        assertFalse(loop.consumeLockAnnouncement(0));
        loop.tick(1, 100);
        assertTrue(loop.consumeLockAnnouncement(1));
        assertFalse(loop.consumeLockAnnouncement(1));
        // 掉下满值后重新充满，应再次提示。
        loop.onCharge(2);
        for (int tick = 3; tick < 60; tick++) loop.tick(tick, 0);
        assertEquals(0, loop.focus());
    }

    @Test void clearResetsEverything() {
        var loop = new EchoHuntLoop();
        loop.activateInstinct(0);
        loop.onCharge(1);
        loop.beginLunge(1);
        loop.clear();
        assertEquals(EchoHuntLoop.Phase.STALK, loop.phase());
        assertEquals(0, loop.focus());
        assertFalse(loop.empowered());
        assertFalse(loop.instinct(1));
        assertEquals(0, loop.snapshot().instinctUntil());
    }

    @Test void snapshotReflectsLiveState() {
        var loop = new EchoHuntLoop();
        loop.activateInstinct(10);
        var snapshot = loop.snapshot();
        assertEquals(EchoHuntLoop.Phase.STALK, snapshot.phase());
        assertEquals(100, snapshot.focus());
        assertTrue(snapshot.instinctUntil() > 10);
    }
}