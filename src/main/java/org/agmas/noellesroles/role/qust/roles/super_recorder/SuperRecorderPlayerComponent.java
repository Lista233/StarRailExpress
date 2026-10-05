package org.agmas.noellesroles.role.qust.roles.super_recorder;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import net.minecraft.world.phys.Vec3;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

/**
 * 超级记录员玩家组件
 * <ul>
 *   <li>管理标记进度（记录员笔记猜对 = 标记）</li>
 *   <li>追踪死亡感知位置（15 格半径）</li>
 *   <li>标记达到玩家人数 3/4 时直接胜利</li>
 * </ul>
 */
public class SuperRecorderPlayerComponent implements RoleComponent, ServerTickingComponent {

    private final Player player;

    /** 开局时的玩家列表（UUID → 名称） */
    private final Map<UUID, String> startPlayers = new HashMap<>();

    /** 已标记的玩家（UUID → 角色 ID） */
    private final Map<UUID, String> markedPlayers = new HashMap<>();

    /** 最近死亡位置列表（客户端渲染用，最多保留 10 个） */
    private final List<double[]> deathLocations = new ArrayList<>();

    /** 开局玩家总数（用于计算 3/4 阈值） */
    private int totalPlayerCount = 0;

    /** 是否已初始化开局数据 */
    private boolean initialized = false;

    private static final int MAX_DEATH_LOCATIONS = 10;
    /** 死亡位置过期 tick（5 分钟后自动移除） */
    private static final int DEATH_LOCATION_EXPIRE_TICKS = 5 * 60 * 20;
    private int deathLocationTimer = 0;

    public SuperRecorderPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void init() {
        startPlayers.clear();
        markedPlayers.clear();
        deathLocations.clear();
        totalPlayerCount = 0;
        initialized = false;
    }

    @Override
    public void serverTick() {
        // 延迟初始化：等待所有玩家加载
        if (!initialized && player instanceof ServerPlayer sp && sp.getServer() != null) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            List<UUID> playerUuids = new ArrayList<>();
            for (Player p : sp.level().players()) {
                if (p.getUUID().equals(sp.getUUID())) continue;
                startPlayers.put(p.getUUID(), p.getName().getString());
                playerUuids.add(p.getUUID());
            }
            totalPlayerCount = playerUuids.size();
            initialized = true;
        }

        // 死亡位置过期清理
        if (!deathLocations.isEmpty()) {
            deathLocationTimer++;
            if (deathLocationTimer >= DEATH_LOCATION_EXPIRE_TICKS) {
                deathLocationTimer = 0;
                if (!deathLocations.isEmpty()) {
                    deathLocations.remove(0);
                }
            }
        }
    }

    // ==================== 标记系统 ====================

    public void addMark(UUID targetUuid, ResourceLocation roleId) {
        markedPlayers.put(targetUuid, roleId.toString());
        checkVictoryCondition();
    }

    public boolean isMarked(UUID targetUuid) {
        return markedPlayers.containsKey(targetUuid);
    }

    public int getMarkCount() {
        return markedPlayers.size();
    }

    public Map<UUID, String> getMarkedPlayers() {
        return markedPlayers;
    }

    /** 计算需要标记的玩家数（四分之三的总玩家数） */
    public int getRequiredMarkCount() {
        return Math.max(2, (int) Math.ceil(totalPlayerCount * 3.0 / 4.0));
    }

    /** 检查是否达成标记胜利条件（标记数 >= 3/4 玩家数） */
    public boolean checkVictoryCondition() {
        if (getMarkCount() < getRequiredMarkCount()) return false;
        if (player instanceof ServerPlayer sp && sp.level() instanceof ServerLevel sl) {
            org.agmas.noellesroles.utils.RoleUtils.customWinnerWin(
                    sl, io.wifi.starrailexpress.game.GameUtils.WinStatus.CUSTOM,
                    "super_recorder", java.util.OptionalInt.of(
                            new java.awt.Color(100, 200, 255).getRGB()));
            for (var p : sp.level().players()) {
                p.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.super_recorder.win", player.getName())
                                .withStyle(net.minecraft.ChatFormatting.GOLD,
                                        net.minecraft.ChatFormatting.BOLD),
                        true);
            }
        }
        return true;
    }

    // ==================== 开局玩家 ====================

    public Map<UUID, String> getStartPlayers() {
        return startPlayers;
    }

    public int getTotalPlayerCount() {
        return totalPlayerCount;
    }

    // ==================== 死亡感知 ====================

    public void addDeathLocation(Vec3 pos) {
        deathLocations.add(new double[]{pos.x, pos.y, pos.z});
        if (deathLocations.size() > MAX_DEATH_LOCATIONS) {
            deathLocations.remove(0);
        }
        deathLocationTimer = 0;
    }

    public List<double[]> getDeathLocations() {
        return deathLocations;
    }

    // ==================== CCA 序列化 ====================

    @Override
    public Player getPlayer() { return player; }

    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) { return true; }

    @Override
    public void clear() { init(); }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 标记玩家
        CompoundTag marksTag = new CompoundTag();
        for (Map.Entry<UUID, String> entry : markedPlayers.entrySet()) {
            marksTag.putString(entry.getKey().toString(), entry.getValue());
        }
        tag.put("markedPlayers", marksTag);

        // 开局玩家
        CompoundTag startTag = new CompoundTag();
        for (Map.Entry<UUID, String> entry : startPlayers.entrySet()) {
            startTag.putString(entry.getKey().toString(), entry.getValue());
        }
        tag.put("startPlayers", startTag);
        tag.putInt("totalPlayerCount", totalPlayerCount);

        tag.putBoolean("initialized", initialized);

        // 死亡位置
        if (!deathLocations.isEmpty()) {
            var list = new net.minecraft.nbt.ListTag();
            for (double[] loc : deathLocations) {
                var posTag = new CompoundTag();
                posTag.putDouble("x", loc[0]);
                posTag.putDouble("y", loc[1]);
                posTag.putDouble("z", loc[2]);
                list.add(posTag);
            }
            tag.put("deathLocations", list);
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        markedPlayers.clear();
        if (tag.contains("markedPlayers", Tag.TAG_COMPOUND)) {
            CompoundTag marksTag = tag.getCompound("markedPlayers");
            for (String key : marksTag.getAllKeys()) {
                try {
                    markedPlayers.put(UUID.fromString(key), marksTag.getString(key));
                } catch (Exception ignored) {}
            }
        }

        startPlayers.clear();
        if (tag.contains("startPlayers", Tag.TAG_COMPOUND)) {
            CompoundTag startTag = tag.getCompound("startPlayers");
            for (String key : startTag.getAllKeys()) {
                try {
                    startPlayers.put(UUID.fromString(key), startTag.getString(key));
                } catch (Exception ignored) {}
            }
        }
        totalPlayerCount = tag.getInt("totalPlayerCount");
        initialized = tag.getBoolean("initialized");

        deathLocations.clear();
        if (tag.contains("deathLocations", Tag.TAG_LIST)) {
            var list = tag.getList("deathLocations", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag posTag = list.getCompound(i);
                deathLocations.add(new double[]{
                        posTag.getDouble("x"),
                        posTag.getDouble("y"),
                        posTag.getDouble("z")
                });
            }
        }
    }
}
