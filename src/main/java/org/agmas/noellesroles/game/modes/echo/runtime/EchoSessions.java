package org.agmas.noellesroles.game.modes.echo.runtime;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.modes.echo.EchoBootstrap;
import org.agmas.noellesroles.game.modes.echo.result.EchoResultSnapshot;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/** 世界实例为键；注册的 GameMode 对象本身不持有全服共用的对局。 */
public final class EchoSessions {
    private static final Map<ServerLevel, EchoMatch> MATCHES = new IdentityHashMap<>();
    private static final Map<ServerLevel, EchoPreflight.Prepared> PREPARED = new IdentityHashMap<>();
    private static long sequence;
    private static final java.util.Set<ServerPlayer> JOINED=java.util.Collections.newSetFromMap(new IdentityHashMap<>());
    public static void joined(ServerPlayer player) { JOINED.add(player); }
    public static void disconnected(ServerPlayer player) {
        JOINED.remove(player);
        for(var match:List.copyOf(MATCHES.values())) if(match.participants().contains(player.getUUID())) match.disconnect(player);
    }

    private EchoSessions() { }

    public static EchoMatch get(ServerLevel level) { return MATCHES.get(level); }
    public static void prepare(ServerLevel level, EchoPreflight.Prepared prepared) { PREPARED.put(level, prepared); }

    public static void initialize(ServerLevel level, List<ServerPlayer> players) {
        var prepared = PREPARED.remove(level);
        if (prepared == null || prepared.profile().playerCount() != players.size()) {
            throw new IllegalStateException("回声最终预检快照缺失");
        }
        MATCHES.put(level, new EchoMatch(level, prepared, players));
    }

    public static void startTick(MinecraftServer server) {
        sequence++;
        for(var player:List.copyOf(JOINED)) {
            JOINED.remove(player);
            if(server.getPlayerList().getPlayer(player.getUUID())!=player) continue;
            var match=MATCHES.values().stream().filter(m -> m.result()==null && m.participants().contains(player.getUUID())).findFirst().orElse(get(player.serverLevel()));
            if(match!=null) try { match.join(player); }
            catch(RuntimeException failure) {
                org.agmas.noellesroles.Noellesroles.LOGGER.error("回声重连恢复失败 {}",player.getUUID(),failure);
                match.settle(EchoResultSnapshot.Outcome.VOID,"RECONNECT_FAILURE",true);
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            var game = SREGameWorldComponent.KEY.get(level);
            var match = get(level);
            if (match != null && game.getGameMode().identifier.equals(EchoBootstrap.ID)
                    && game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) match.startTick(sequence);
        }
    }

    public static void endTick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            var game = SREGameWorldComponent.KEY.get(level);
            var match = get(level);
            if (match != null && game.getGameMode().identifier.equals(EchoBootstrap.ID)
                    && game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE) match.endTick();
        }
    }

    public static void unload(ServerLevel level) {
        EchoMatch match = MATCHES.remove(level);
        PREPARED.remove(level);
        if (match != null) {
            if (match.result() == null) match.settle(EchoResultSnapshot.Outcome.VOID, "WORLD_UNLOADED", false);
            match.finalizeMatch();
        }
    }

    public static void stopServer(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) unload(level);
        JOINED.clear();
    }
}
