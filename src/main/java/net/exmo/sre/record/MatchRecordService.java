/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package net.exmo.sre.record;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.api.replay.GameReplayData;
import io.wifi.starrailexpress.api.replay.GameReplayManager;
import io.wifi.starrailexpress.api.replay.ReplayEventTypes;
import io.wifi.starrailexpress.api.replay.ReplayTimelineEvent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import net.exmo.sre.record.network.RecordListS2CPayload;
import net.exmo.sre.record.network.RecordReplayS2CPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.harpymodloader.modded_murder.PlayerRoleWeightManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 全局战绩服务端逻辑：在对局结束时把回放时间线落库，并响应客户端的列表 / 回放查询请求。
 */
public final class MatchRecordService {

    private static final Logger logger = LoggerFactory.getLogger(MatchRecordService.class);
    private static final int DEFAULT_LIST_LIMIT = 50;

    private MatchRecordService() {
    }

    /**
     * 对局结束时调用：把当前回放管理器中的完整时间线构造成 {@link MatchRecord} 并异步保存到远端数据库。
     * 若远端数据库不可用（未开启 MySQL 同步）则静默跳过。
     *
     * <p>必须在 {@code GameMode#recordWinStats} 之后调用：落库时要读取 {@link SREGameRoundEndComponent}
     * 中刚写入的每玩家胜负标记来填充 {@link MatchRecord.MatchPlayer#win}。
     */
    public static void recordFinishedMatch(ServerLevel level) {
        if (level == null || !MatchRecordStore.isAvailable() || !SREConfig.instance().isStatsSyncEnabled) {
            return;
        }
        GameReplayManager manager = SRE.REPLAY_MANAGER;
        if (manager == null || manager.currentReplayData == null) {
            return;
        }
        try {
            MatchRecord record = build(manager, manager.currentReplayData, level.registryAccess(),
                    SREGameRoundEndComponent.KEY.get(level));
            MatchRecordStore.saveAsync(record).whenComplete((ok, err) -> {
                if (err != null) {
                    logger.warn("异步保存全局战绩失败。", err);
                } else if (Boolean.TRUE.equals(ok)) {
                    logger.info("全局战绩 {} 已保存（{} 条事件）。", record.matchId, record.events.size());
                }
            });
        } catch (Exception exception) {
            logger.warn("构造全局战绩时出错，已跳过本局保存。", exception);
        }
    }

    private static MatchRecord build(GameReplayManager manager, GameReplayData data, RegistryAccess registryAccess,
            SREGameRoundEndComponent roundEnd) {
        MatchRecord record = new MatchRecord();
        record.matchId = UUID.randomUUID().toString();
        record.createdAt = System.currentTimeMillis();
        record.winningTeam = data.getWinningTeam();
        MutableComponent title = data.getWinningTitle();
        record.winningTitleJson = title == null ? null : Component.Serializer.toJson(title, registryAccess);
        record.playerCount = data.getPlayerCount();

        // 扫描换职业事件，得到每位玩家的「最初旧职业」与「最终现职业」
        Map<UUID, String> firstOldRole = new HashMap<>();
        Map<UUID, String> lastNewRole = new HashMap<>();
        for (GameReplayData.ReplayEvent event : data.getTimeline()) {
            if (event.getType() != GameReplayData.EventType.CHANGE_ROLE) {
                continue;
            }
            UUID player = event.getSourcePlayer();
            String message = event.getMessage();
            if (player == null || message == null) {
                continue;
            }
            int separator = message.indexOf("===");
            if (separator < 0) {
                continue;
            }
            firstOldRole.putIfAbsent(player, message.substring(0, separator));
            lastNewRole.put(player, message.substring(separator + 3));
        }

        Map<UUID, String> roles = data.getPlayerRoles();
        if (roles != null) {
            for (Map.Entry<UUID, String> entry : roles.entrySet()) {
                UUID uuid = entry.getKey();
                if (uuid == null) {
                    continue;
                }
                String currentRole = lastNewRole.getOrDefault(uuid, entry.getValue());
                String oldRole = firstOldRole.get(uuid);
                if (oldRole != null && oldRole.equals(currentRole)) {
                    oldRole = null;
                }
                MatchRecord.MatchPlayer player = new MatchRecord.MatchPlayer();
                player.uuid = uuid.toString();
                player.name = GameReplayManager.playerNames.getOrDefault(uuid, uuid.toString());
                player.roleId = currentRole;
                player.oldRoleId = oldRole;
                player.faction = factionOf(currentRole);
                player.win = roundEnd != null && roundEnd.didWin(uuid) ? 1 : 0;
                record.players.add(player);
            }
        }

        for (ReplayTimelineEvent event : manager.getTimelineEvents(true)) {
            MatchRecord.MatchEvent matchEvent = new MatchRecord.MatchEvent();
            matchEvent.type = event.type() == null ? "CUSTOM_EVENT" : event.type().name();
            matchEvent.relativeTimestamp = event.relativeTimestamp();
            matchEvent.actorName = event.actor() == null ? null : event.actor().name();
            matchEvent.textJson = event.text() == null
                    ? null
                    : Component.Serializer.toJson(event.text(), registryAccess);
            matchEvent.hidden = event.hidden();
            matchEvent.killType = killTypeOf(event);
            record.events.add(matchEvent);
        }
        return record;
    }

    private static int factionOf(String roleId) {
        if (roleId == null) {
            return -1;
        }
        ResourceLocation id = ResourceLocation.tryParse(roleId);
        if (id == null) {
            return -1;
        }
        SRERole role = TMMRoles.ROLES.get(id);
        return role == null ? -1 : PlayerRoleWeightManager.getRoleType(role);
    }

    /**
     * 计算 PLAYER_KILL 事件的击杀性质（按事件发生时双方的阵营判定，职业取自事件数据里的
     * {@code sourceRole}/{@code targetRole} 快照）：
     *
     * <ul>
     * <li>1 误杀：好人侧（平民/义警）互杀；杀手侧（杀手/中立偏杀手）互杀；</li>
     * <li>2 正义击杀：好人侧击杀非好人（杀手/中立偏杀手/纯中立）；</li>
     * <li>3 邪恶击杀：杀手侧击杀好人侧或纯中立；</li>
     * <li>4 中立击杀：纯中立击杀任何阵营（含纯中立）；</li>
     * <li>0 未分类：非击杀事件、无击杀者（环境死亡等），或阵营信息缺失无法判定。</li>
     * </ul>
     */
    private static int killTypeOf(ReplayTimelineEvent event) {
        if (event.type() != ReplayEventTypes.EventType.PLAYER_KILL) {
            return 0;
        }
        Map<String, String> data = event.data();
        int killerType = factionOf(data == null ? null : data.get("sourceRole"));
        int victimType = factionOf(data == null ? null : data.get("targetRole"));
        if (killerType <= 0 || victimType <= 0) {
            return 0;
        }
        // 阵营：1 好人 / 2 纯中立 / 3 中立偏杀手 / 4 杀手 / 5 义警
        boolean killerGood = killerType == 1 || killerType == 5;
        boolean victimGood = victimType == 1 || victimType == 5;
        boolean killerSide = killerType == 3 || killerType == 4;
        boolean victimSide = victimType == 3 || victimType == 4;

        if (killerGood) {
            // 好人侧互杀是误杀，击杀非好人算正义击杀
            return victimGood ? 1 : 2;
        }
        if (killerSide) {
            // 杀手/亲杀中立内讧算误杀，其余算邪恶击杀
            return victimSide ? 1 : 3;
        }
        if (killerType == 2) {
            // 纯中立击杀（含中立互杀）单列一类
            return 4;
        }
        return 0; // 未知阵营兜底
    }

    /** 响应「按需拉取战绩列表的一页」请求：仅查询并同步该窗口，减少流量与数据库负载。 */
    public static void openListWindow(ServerPlayer player, int offset, int limit) {
        if (player == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        int requested = limit <= 0 ? DEFAULT_LIST_LIMIT : limit;
        int safeOffset = Math.max(0, offset);
        MatchRecordStore.listWindowAsync(safeOffset, requested).whenComplete((page, err) -> {
            if (server == null) {
                return;
            }
            server.execute(() -> {
                if (!isOnline(server, player)) {
                    return;
                }
                if (err != null || page == null) {
                    ServerPlayNetworking.send(player, new RecordListS2CPayload(safeOffset, 0, "[]"));
                    return;
                }
                ServerPlayNetworking.send(player, new RecordListS2CPayload(page.offset(), page.total(),
                        MatchRecord.summaryListToJson(page.items())));
            });
        });
    }

    /** 响应「打开某场回放」请求：异步读取完整记录并同步给玩家。 */
    public static void openReplayFor(ServerPlayer player, String matchId) {
        if (player == null || matchId == null) {
            return;
        }
        MinecraftServer server = player.getServer();
        MatchRecordStore.loadAsync(matchId).whenComplete((record, err) -> {
            if (server == null) {
                return;
            }
            server.execute(() -> {
                if (!isOnline(server, player)) {
                    return;
                }
                String json = (err != null || record == null || record.isEmpty())
                        ? ""
                        : record.get().toJson();
                ServerPlayNetworking.send(player, new RecordReplayS2CPayload(matchId, json));
            });
        });
    }

    private static boolean isOnline(MinecraftServer server, ServerPlayer player) {
        return server.getPlayerList().getPlayer(player.getUUID()) != null;
    }
}
