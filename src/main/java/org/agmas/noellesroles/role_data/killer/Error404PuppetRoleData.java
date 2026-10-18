package org.agmas.noellesroles.role_data.killer;

import java.util.List;
import java.util.UUID;
import org.agmas.noellesroles.game.command_block.Error404Pathfinder.Cell;
import org.agmas.noellesroles.game.command_block.Error404Role;
import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** 同化完成后独立的傀儡状态；换职业会立即释放控制。 */
public class Error404PuppetRoleData extends SimpleRoleData {
    public UUID owner;
    public int order;
    public UUID attackTarget;
    public Vec3 destination;
    public List<Cell> path = List.of();
    public int pathIndex;
    public long repathAt;
    public long attackAt;

    public Error404PuppetRoleData(RoleDataContext context) { super(context); }

    @Override
    public void serverTick() {
        if (player instanceof ServerPlayer self) Error404Role.tickPuppet(self, this);
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer self) Error404Role.releasePuppet(self, this);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registry) {
        if (owner != null) tag.putUUID("owner", owner);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registry) {
        owner = tag.hasUUID("owner") ? tag.getUUID("owner") : null;
    }
}
