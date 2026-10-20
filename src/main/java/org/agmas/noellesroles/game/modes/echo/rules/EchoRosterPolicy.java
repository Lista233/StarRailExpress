package org.agmas.noellesroles.game.modes.echo.rules;

import org.agmas.noellesroles.game.modes.echo.state.EchoContract;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;

import java.util.*;
import java.util.random.RandomGenerator;

/** 固定人数档与无放回分配；不接受普通职业池覆盖。 */
public final class EchoRosterPolicy {
    private EchoRosterPolicy() { }

    public record Profile(int playerCount, int interceptors, int carriers, int brokers, int beacons,
                          int escapeQuota, int operationTicks, int evacuationTicks) {
        public Profile {
            if (playerCount < 6 || playerCount > 12 || interceptors < 1 || carriers < 1 || brokers < 1
                    || interceptors + carriers + brokers != playerCount || beacons < 2 || beacons > 3
                    || escapeQuota < 1 || escapeQuota > carriers || operationTicks <= 0 || evacuationTicks <= 0) {
                throw new IllegalArgumentException("无效人数档");
            }
        }
    }

    public static Profile forPlayerCount(int count) {
        if (count < 6 || count > 12) throw new IllegalArgumentException("回声托运站要求6–12人");
        int wolves = count <= 8 ? 1 : 2;
        int brokers = count == 12 ? 2 : 1;
        int quota = switch (count) {
            case 6, 7 -> 3;
            case 8, 9 -> 4;
            default -> 5;
        };
        return new Profile(count, wolves, count - wolves - brokers, brokers, count <= 7 ? 2 : 3,
                quota, EchoRules.secondsToTicks(count <= 8 ? 720 : 840),
                EchoRules.secondsToTicks(count <= 8 ? 90 : 120));
    }

    public static Map<UUID, EchoFaction> assign(List<UUID> players, RandomGenerator random) {
        Objects.requireNonNull(players);
        Objects.requireNonNull(random);
        Profile profile = forPlayerCount(players.size());
        if (players.stream().anyMatch(Objects::isNull) || new HashSet<>(players).size() != players.size()) {
            throw new IllegalArgumentException("准备名单包含空或重复UUID");
        }
        List<UUID> shuffled = new ArrayList<>(players);
        shuffle(shuffled, random);
        Map<UUID, EchoFaction> assignment = new LinkedHashMap<>();
        for (int index = 0; index < shuffled.size(); index++) {
            EchoFaction faction = index < profile.interceptors() ? EchoFaction.INTERCEPTOR
                    : index < profile.interceptors() + profile.brokers() ? EchoFaction.BROKER : EchoFaction.CARRIER;
            assignment.put(shuffled.get(index), faction);
        }
        return Collections.unmodifiableMap(assignment);
    }

    public static Map<UUID, EchoContract> assignContracts(Map<UUID, EchoFaction> roster, RandomGenerator random) {
        Objects.requireNonNull(random);
        List<UUID> brokers = new ArrayList<>();
        roster.forEach((uuid, faction) -> {
            Objects.requireNonNull(uuid);
            Objects.requireNonNull(faction);
            if (faction == EchoFaction.BROKER) brokers.add(uuid);
        });
        if (brokers.size() > 2) throw new IllegalArgumentException("投机客人数超过上限");
        List<EchoContract> contracts = new ArrayList<>(List.of(EchoContract.values()));
        shuffle(contracts, random);
        Map<UUID, EchoContract> result = new LinkedHashMap<>();
        for (int index = 0; index < brokers.size(); index++) result.put(brokers.get(index), contracts.get(index));
        return Collections.unmodifiableMap(result);
    }

    private static <T> void shuffle(List<T> list, RandomGenerator random) {
        for (int index = list.size() - 1; index > 0; index--) {
            Collections.swap(list, index, random.nextInt(index + 1));
        }
    }
}
