package org.agmas.noellesroles.role.qust.roles.minigame_master;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

/**
 * 小游戏达人玩家组件
 * <p>
 * 击退 buff 已随技能一并移除（击退剑改为持有即可左键击退），
 * 目前仅作为角色组件占位保留。
 */
public class MinigameMasterPlayerComponent implements RoleComponent {

    private final Player player;

    public MinigameMasterPlayerComponent(Player player) {
        this.player = player;
    }

    public void sync() {
        QUSTComponentKeys.Keys.MINIGAME_MASTER.sync(player);
    }

    @Override
    public void init() {
    }

    // ==================== CCA 同步 ====================

    @Override
    public Player getPlayer() { return player; }

    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) { return true; }

    @Override
    public void clear() {
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
