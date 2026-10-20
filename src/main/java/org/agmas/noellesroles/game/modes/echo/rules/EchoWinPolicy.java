package org.agmas.noellesroles.game.modes.echo.rules;

import org.agmas.noellesroles.game.modes.echo.state.*;

import java.util.List;
import java.util.Objects;

/** 只消费已提交事实；撤离完成在超时判定之前，不由中立成功抢停主局。 */
public final class EchoWinPolicy {
    public enum Outcome { NONE, CARRIERS, INTERCEPTORS, VOID }
    public enum Reason { PENDING, ABORTED, ESCAPE_QUOTA, INSUFFICIENT_CARRIERS, NO_INTERCEPTOR, TIME_EXPIRED }
    public record Decision(Outcome outcome, Reason reason) { }
    public record PlayerFact(EchoFaction faction, EchoLifeState life, boolean connected, boolean inGrace) {
        public PlayerFact { Objects.requireNonNull(faction); Objects.requireNonNull(life); }
        public boolean available() { return connected || inGrace; }
    }
    public record Facts(EchoPhase phase, int remainingTicks, int quota, boolean aborted, List<PlayerFact> players) {
        public Facts {
            Objects.requireNonNull(phase);
            players = List.copyOf(players);
            if (remainingTicks < 0 || quota < 1) throw new IllegalArgumentException("无效胜负输入");
        }
    }

    private EchoWinPolicy() { }

    public static Decision evaluate(Facts facts) {
        if (facts.aborted()) return new Decision(Outcome.VOID, Reason.ABORTED);
        if (!facts.phase().acceptsActions()) return new Decision(Outcome.NONE, Reason.PENDING);
        long escaped = facts.players().stream().filter(p -> p.faction() == EchoFaction.CARRIER
                && p.life() == EchoLifeState.ESCAPED).count();
        if (facts.phase() == EchoPhase.EVACUATION && escaped >= facts.quota()) {
            return new Decision(Outcome.CARRIERS, Reason.ESCAPE_QUOTA);
        }
        long possible = facts.players().stream().filter(p -> p.faction() == EchoFaction.CARRIER
                && p.available() && p.life().potentiallyEscapable()).count();
        if (escaped + possible < facts.quota()) return new Decision(Outcome.INTERCEPTORS, Reason.INSUFFICIENT_CARRIERS);
        boolean wolf = facts.players().stream().anyMatch(p -> p.faction() == EchoFaction.INTERCEPTOR
                && p.available() && p.life().potentiallyEscapable());
        if (!wolf) return new Decision(Outcome.VOID, Reason.NO_INTERCEPTOR);
        if (facts.remainingTicks() == 0) return new Decision(Outcome.INTERCEPTORS, Reason.TIME_EXPIRED);
        return new Decision(Outcome.NONE, Reason.PENDING);
    }

    public static boolean didPlayerWin(Decision result, PlayerFact player, EchoContract contract,
                                        boolean successLocked, int resolvedPacks) {
        if (result.outcome() == Outcome.NONE || result.outcome() == Outcome.VOID) return false;
        if (player.faction() == EchoFaction.CARRIER) return result.outcome() == Outcome.CARRIERS;
        if (player.faction() == EchoFaction.INTERCEPTOR) return result.outcome() == Outcome.INTERCEPTORS;
        if (contract == null) return false;
        if (contract.requiresFinalSurvival()) return resolvedPacks >= 3 && player.connected() && player.life().standing();
        return successLocked;
    }
}
