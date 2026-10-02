package org.agmas.noellesroles.role.qust.roles.super_doctor;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 超级医生（Super Doctor / 医生_花艺）玩家组件。
 * <p>
 * 跟踪 G 键治疗技能的冷却状态。
 */
public class SuperDoctorPlayerComponent implements RoleComponent, ServerTickingComponent {

    private final Player player;

    /** G 键技能剩余冷却 tick */
    private int healCooldownTicks = 0;

    public SuperDoctorPlayerComponent(Player player) {
        this.player = player;
    }

    // ── G 键治疗技能 ──

    public boolean isHealReady() {
        return healCooldownTicks <= 0;
    }

    public int getHealCooldownTicks() {
        return healCooldownTicks;
    }

    public void setHealCooldownTicks(int ticks) {
        this.healCooldownTicks = ticks;
        sync();
    }

    public void tickCooldown() {
        if (healCooldownTicks > 0) {
            healCooldownTicks--;
            // 每 20 tick 同步一次给客户端
            if (healCooldownTicks % 20 == 0) {
                sync();
            }
        }
    }

    // ── ServerTickingComponent ──

    @Override
    public void serverTick() {
        tickCooldown();
    }

    // ── RoleComponent 接口 ──

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) {
        return true;
    }

    @Override
    public void init() {
        healCooldownTicks = 0;
    }

    @Override
    public void clear() {
        healCooldownTicks = 0;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("healCooldownTicks", healCooldownTicks);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        healCooldownTicks = tag.getInt("healCooldownTicks");
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider provider) {
        readFromSyncNbt(tag, provider);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider provider) {
        writeToSyncNbt(tag, provider);
    }

    private void sync() {
        QUSTComponentKeys.Keys.SUPER_DOCTOR.sync(player);
    }
}
