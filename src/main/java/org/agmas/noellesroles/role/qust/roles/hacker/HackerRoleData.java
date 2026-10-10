package org.agmas.noellesroles.role.qust.roles.hacker;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 黑客职业数据：跟踪已标记和已发送的玩家。
 * <p>
 * 胜利条件：发送给场上至少3/4的存活玩家（上限15人）
 */
public class HackerRoleData implements RoleComponent, org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent {

    private final Player player;

    /** 已标记的玩家（使用干扰芯片标记过） */
    public final Set<UUID> markedPlayers = new LinkedHashSet<>();

    /** 已发送终端消息的玩家（使用发送终端后标记） */
    public final Set<UUID> sentPlayers = new LinkedHashSet<>();

    /** 标记时间戳（玩家UUID -> 标记时的tick） */
    public final Map<UUID, Integer> markTimestamps = new HashMap<>();

    /** 待发送的延迟通知（标记后8秒通知被标记者） */
    private final List<PendingNotification> pendingNotifications = new ArrayList<>();

    /** 延迟通知数据 */
    public record PendingNotification(UUID targetUuid, String targetName, String ip, int sendAtTick) {}

    /** 同步到客户端的存活玩家数（用于HUD显示） */
    public int syncedAliveCount = 0;

    /** 同步到客户端的需要发送数（用于HUD显示） */
    public int syncedRequiredCount = 0;

    /** 上次同步时的存活数/所需数（变化检测用，仅服务端运行时状态） */
    private int lastSyncedAliveCount = -1;
    private int lastSyncedRequiredCount = -1;

    public HackerRoleData(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        markedPlayers.clear();
        sentPlayers.clear();
        markTimestamps.clear();
        pendingNotifications.clear();
        syncedAliveCount = 0;
        syncedRequiredCount = 0;
        lastSyncedAliveCount = -1;
        lastSyncedRequiredCount = -1;
    }

    @Override
    public void clear() {
        markedPlayers.clear();
        sentPlayers.clear();
        markTimestamps.clear();
        pendingNotifications.clear();
        syncedAliveCount = 0;
        syncedRequiredCount = 0;
        lastSyncedAliveCount = -1;
        lastSyncedRequiredCount = -1;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) {
        return true;
    }

    private void sync() {
        if (!player.level().isClientSide && player instanceof ServerPlayer) {
            org.agmas.noellesroles.role.qust.QUSTComponentKeys.Keys.HACKER.sync(player);
        }
    }

    /**
     * 标记一个玩家
     */
    public void markPlayer(UUID playerId) {
        if (playerId != null && markedPlayers.add(playerId)) {
            sync();
        }
    }

    /**
     * 检查玩家是否已被标记
     */
    public boolean isMarked(UUID playerId) {
        return playerId != null && markedPlayers.contains(playerId);
    }

    /**
     * 设置标记时间戳
     */
    public void setMarkTimestamp(UUID playerId, int tick) {
        if (playerId != null) {
            markTimestamps.put(playerId, tick);
        }
    }

    /**
     * 添加延迟通知（标记后8秒通知被标记者）
     */
    public void addPendingNotification(UUID targetUuid, String targetName, String ip, int currentTick) {
        pendingNotifications.add(new PendingNotification(targetUuid, targetName, ip, currentTick + 160)); // 8秒 = 160 ticks
    }

    /**
     * 获取标记时间戳
     */
    public int getMarkTimestamp(UUID playerId) {
        return markTimestamps.getOrDefault(playerId, -1);
    }

    /**
     * 检查玩家是否已被发送过终端消息
     */
    public boolean hasSent(UUID playerId) {
        return playerId != null && sentPlayers.contains(playerId);
    }

    /**
     * 发送终端：将所有已标记但未发送的玩家标记为已发送
     *
     * @return 本次新发送的玩家数量
     */
    public int sendTerminal(ServerPlayer hacker) {
        int newSent = 0;
        for (UUID uuid : markedPlayers) {
            if (!sentPlayers.contains(uuid)) {
                sentPlayers.add(uuid);
                newSent++;
            }
        }
        if (newSent > 0) {
            sync();
        }
        return newSent;
    }

    /**
     * 检查是否达成胜利条件
     */
    public boolean hasWon() {
        if (!(player instanceof ServerPlayer sp)) {
            return false;
        }

        // 统计场上存活的玩家数（排除黑客自己）
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        int aliveCount = 0;
        for (ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) {
            if (p.getUUID().equals(player.getUUID())) {
                continue;  // 排除黑客自己
            }
            if (gameWorld.getRole(p) != null && GameUtils.isPlayerAliveAndSurvival(p)) {
                aliveCount++;
            }
        }

        if (aliveCount == 0) {
            return false;
        }

        // 需要发送的人数：场上人数的 3/4，上限15
        int requiredCount = Math.min((int) Math.ceil(aliveCount * 0.75), 15);
        int actualSent = sentPlayers.size();

        return actualSent >= requiredCount;
    }

    /**
     * 获取胜利进度（已发送/需要发送）
     */
    public String getProgress() {
        if (!(player instanceof ServerPlayer sp)) {
            return "0/0";
        }

        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        int aliveCount = 0;
        for (ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) {
            if (p.getUUID().equals(player.getUUID())) {
                continue;
            }
            if (gameWorld.getRole(p) != null && GameUtils.isPlayerAliveAndSurvival(p)) {
                aliveCount++;
            }
        }

        int requiredCount = Math.min((int) Math.ceil(aliveCount * 0.75), 15);
        int actualSent = sentPlayers.size();

        return actualSent + "/" + requiredCount;
    }

    /**
     * 统计场上存活的玩家数（排除黑客自己），并更新同步字段
     */
    private int updateAliveCount() {
        if (!(player instanceof ServerPlayer sp)) {
            syncedAliveCount = 0;
            syncedRequiredCount = 0;
            return 0;
        }
        var gameWorld = SREGameWorldComponent.KEY.get(player.level());
        int aliveCount = 0;
        for (ServerPlayer p : sp.getServer().getPlayerList().getPlayers()) {
            if (p.getUUID().equals(player.getUUID())) continue;
            if (gameWorld.getRole(p) != null && GameUtils.isPlayerAliveAndSurvival(p)) {
                aliveCount++;
            }
        }
        syncedAliveCount = aliveCount;
        syncedRequiredCount = Math.min((int) Math.ceil(aliveCount * 0.75), 15);
        return aliveCount;
    }

    @Override
    public void serverTick() {
        // 更新HUD同步数据
        updateAliveCount();

        // 存活人数或所需发送数变化时同步到客户端（含职业刚分配后的首次初始化），
        // 保证一进游戏 HUD 的 x/y 就是正确值而非 0/0（此前只有标记/发送动作才触发 sync）
        if (syncedAliveCount != lastSyncedAliveCount || syncedRequiredCount != lastSyncedRequiredCount) {
            lastSyncedAliveCount = syncedAliveCount;
            lastSyncedRequiredCount = syncedRequiredCount;
            sync();
        }

        if (!pendingNotifications.isEmpty()) {
            int currentTick = (int) player.level().getGameTime();

            List<PendingNotification> toRemove = new ArrayList<>();
            for (PendingNotification pn : pendingNotifications) {
                if (currentTick >= pn.sendAtTick()) {
                    // 时间到了，发送通知给被标记者
                    ServerPlayer target = player.getServer().getPlayerList().getPlayer(pn.targetUuid());
                    if (target != null && GameUtils.isPlayerAliveAndSurvival(target)) {
                        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                                target,
                                new HackerPayload.ShowBeenMarked(
                                        pn.targetName(),
                                        pn.targetUuid(),
                                        pn.ip()
                                )
                        );
                        // 标记为已发送（用于胜利条件统计）
                        sentPlayers.add(pn.targetUuid());
                    }
                    toRemove.add(pn);
                }
            }
            if (!toRemove.isEmpty()) {
                sync();
            }
            pendingNotifications.removeAll(toRemove);
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        markedPlayers.clear();
        ListTag marked = tag.getList("MarkedPlayers", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < marked.size(); i++) {
            markedPlayers.add(NbtUtils.loadUUID(marked.get(i)));
        }

        sentPlayers.clear();
        ListTag sent = tag.getList("SentPlayers", Tag.TAG_INT_ARRAY);
        for (int i = 0; i < sent.size(); i++) {
            sentPlayers.add(NbtUtils.loadUUID(sent.get(i)));
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 保存已标记的玩家
        ListTag marked = new ListTag();
        for (UUID id : markedPlayers) {
            marked.add(NbtUtils.createUUID(id));
        }
        tag.put("MarkedPlayers", marked);

        // 保存已发送的玩家
        ListTag sent = new ListTag();
        for (UUID id : sentPlayers) {
            sent.add(NbtUtils.createUUID(id));
        }
        tag.put("SentPlayers", sent);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
        // 同步存活人数和需要发送数（用于客户端HUD显示）
        updateAliveCount();
        tag.putInt("SyncedAliveCount", syncedAliveCount);
        tag.putInt("SyncedRequiredCount", syncedRequiredCount);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
        syncedAliveCount = tag.getInt("SyncedAliveCount");
        syncedRequiredCount = tag.getInt("SyncedRequiredCount");
    }
}
