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

import java.util.LinkedHashSet;
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
    }

    @Override
    public void clear() {
        markedPlayers.clear();
        sentPlayers.clear();
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

    @Override
    public void serverTick() {
        // 不需要每tick更新
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
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }
}
