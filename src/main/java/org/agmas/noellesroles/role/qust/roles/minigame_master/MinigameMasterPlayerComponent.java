package org.agmas.noellesroles.role.qust.roles.minigame_master;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

/**
 * 小游戏达人玩家组件（当局制，随开局 init / 结算 clear）
 * <p>
 * 击退 buff 已随技能一并移除（击退剑改为持有即可左键击退），
 * 目前仅记录本局达人自己用小游戏券完成的小游戏次数（里程碑奖励用）。
 */
public class MinigameMasterPlayerComponent implements RoleComponent {

    private final Player player;

    /** 本局达人自己用小游戏券完成的小游戏计数（每满配置数量发放一次里程碑金币） */
    public int ticketMinigameCompleted = 0;

    public MinigameMasterPlayerComponent(Player player) {
        this.player = player;
    }

    public void sync() {
        QUSTComponentKeys.Keys.MINIGAME_MASTER.sync(player);
    }

    @Override
    public void init() {
        this.ticketMinigameCompleted = 0;
    }

    // ==================== CCA 同步 ====================

    @Override
    public Player getPlayer() { return player; }

    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) { return true; }

    @Override
    public void clear() {
        this.ticketMinigameCompleted = 0;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
    }
}
