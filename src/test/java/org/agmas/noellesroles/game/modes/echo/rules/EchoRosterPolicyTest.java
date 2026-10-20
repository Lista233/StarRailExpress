package org.agmas.noellesroles.game.modes.echo.rules;

import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class EchoRosterPolicyTest {
    @Test void allPopulationProfilesAndUniqueContracts() {
        int[][] expected = {{1,4,1,2,3,720,90},{1,5,1,2,3,720,90},{1,6,1,3,4,720,90},
                {2,6,1,3,4,840,120},{2,7,1,3,5,840,120},{2,8,1,3,5,840,120},{2,8,2,3,5,840,120}};
        for (int count = 6; count <= 12; count++) {
            var p = EchoRosterPolicy.forPlayerCount(count);
            int[] row = expected[count - 6];
            assertArrayEquals(row, new int[]{p.interceptors(), p.carriers(), p.brokers(), p.beacons(),
                    p.escapeQuota(), p.operationTicks()/20, p.evacuationTicks()/20});
            List<UUID> players = IntStream.range(0, count).mapToObj(i -> new UUID(0, i)).toList();
            var assigned = EchoRosterPolicy.assign(players, new Random(44));
            assertEquals(count, assigned.size());
            assertEquals(p.interceptors(), assigned.values().stream().filter(f -> f == EchoFaction.INTERCEPTOR).count());
            assertEquals(p.carriers(), assigned.values().stream().filter(f -> f == EchoFaction.CARRIER).count());
            var contracts = EchoRosterPolicy.assignContracts(assigned, new Random(91));
            assertEquals(p.brokers(), contracts.size());
            assertEquals(contracts.size(), new HashSet<>(contracts.values()).size());
            assertThrows(UnsupportedOperationException.class, assigned::clear);
            assertEquals(new UUID(0, 0), players.getFirst());
        }
    }

    @Test void rejectsInvalidPopulationDuplicatesNullsAndTickOverflow() {
        for (int count : new int[]{Integer.MIN_VALUE, 0, 5, 13, Integer.MAX_VALUE}) {
            assertThrows(IllegalArgumentException.class, () -> EchoRosterPolicy.forPlayerCount(count));
        }
        List<UUID> duplicate = Collections.nCopies(6, UUID.randomUUID());
        assertThrows(IllegalArgumentException.class, () -> EchoRosterPolicy.assign(duplicate, new Random()));
        List<UUID> nulls = new ArrayList<>(duplicate);
        nulls.set(0, null);
        assertThrows(IllegalArgumentException.class, () -> EchoRosterPolicy.assign(nulls, new Random()));
        assertThrows(IllegalArgumentException.class, () -> EchoRules.secondsToTicks(-1));
        assertThrows(ArithmeticException.class, () -> EchoRules.secondsToTicks(Integer.MAX_VALUE));
        assertEquals(0, EchoRules.secondsToTicks(0));
        assertEquals(400, EchoRules.SAFE_TICKS);
    }
}
