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

package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.init.ModItems;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

/**
 * 竹枪延伸体：从持有者手部沿视线伸长，最长 10 格、最多 3 秒；命中玩家则击杀并收回。
 */
public class BambooSpearEntity extends Entity {

    public static final float MAX_LENGTH = 10.0f;
    public static final int EXTEND_TICKS = 20 * 3;
    public static final int RETRACT_TICKS = 10;

    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            BambooSpearEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> RETRACTING = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.BOOLEAN);

    private boolean killed;
    private float clientLength;
    private float clientPrevLength;
    /**
     * 最后一格耐久使用时物品会碎裂（背包里不再有竹枪），
     * 置为 true 后本条「持有者必须持有竹枪」的检查被跳过，
     * 让竹枪正常完成本次伸长与伤害结算后再消失。
     */
    private boolean itemCheckBypassed;

    public BambooSpearEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public void setup(ServerPlayer owner) {
        this.entityData.set(OWNER_UUID, Optional.of(owner.getUUID()));
        this.setPos(owner.getEyePosition());
        this.setYRot(owner.getYRot());
        this.setXRot(owner.getXRot());
        this.entityData.set(LENGTH, 0.05f);
        this.entityData.set(RETRACTING, false);
    }

    public float getLength() {
        return this.entityData.get(LENGTH);
    }

    public float getInterpolatedLength(float partialTick) {
        return Mth.lerp(partialTick, this.clientPrevLength, this.clientLength);
    }

    public boolean isRetracting() {
        return this.entityData.get(RETRACTING);
    }

    public @Nullable UUID getOwnerUuid() {
        return this.entityData.get(OWNER_UUID).orElse(null);
    }

    public @Nullable Player getOwner() {
        UUID uuid = getOwnerUuid();
        return uuid == null ? null : this.level().getPlayerByUUID(uuid);
    }

    public static boolean hasActiveFor(Player player) {
        if (player.level().isClientSide) {
            return false;
        }
        AABB box = player.getBoundingBox().inflate(48.0);
        for (BambooSpearEntity spear : player.level().getEntitiesOfClass(BambooSpearEntity.class, box)) {
            UUID owner = spear.getOwnerUuid();
            if (owner != null && player.getUUID().equals(owner)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(LENGTH, 0.05f);
        builder.define(OWNER_UUID, Optional.empty());
        builder.define(RETRACTING, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.clientPrevLength = this.clientLength;
            this.clientLength = getLength();
            return;
        }
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        Player owner = getOwner();
        boolean holdsSpear = owner instanceof ServerPlayer sp
                && (sp.getMainHandItem().is(ModItems.BAMBOO_SPEAR) || sp.getOffhandItem().is(ModItems.BAMBOO_SPEAR));
        if (!(owner instanceof ServerPlayer serverOwner) || !GameUtils.isPlayerAliveAndSurvival(serverOwner)
                || (!holdsSpear && !this.itemCheckBypassed)) {
            this.discard();
            return;
        }

        // 命中射线从眼睛（准星中心线）发出：屏幕上指哪打哪，与视觉收敛线在最远端重合
        Vec3 start = serverOwner.getEyePosition();
        Vec3 look = serverOwner.getLookAngle();
        // 注意：实体不再每 tick 跟随所有者移动。传送式跟随会让客户端插值严重滞后，
        // 导致渲染出的竹枪不朝当前视角伸长（伤害判定不受影响，因此此前「打得到但看不到」）。
        // 实体保持在发射时的眼睛位置不动，渲染端按所有者的实时视角绘制整根竹枪。

        float length = getLength();
        boolean retracting = isRetracting();
        if (!retracting) {
            length = Math.min(MAX_LENGTH, length + MAX_LENGTH / EXTEND_TICKS);
        } else {
            length = Math.max(0.0f, length - MAX_LENGTH / RETRACT_TICKS);
            if (length <= 0.05f) {
                this.discard();
                return;
            }
        }

        Vec3 end = start.add(look.scale(length));
        BlockHitResult blockHit = this.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, serverOwner));
        if (blockHit.getType() != HitResult.Type.MISS) {
            length = (float) start.distanceTo(blockHit.getLocation());
            end = start.add(look.scale(Math.max(0.05, length)));
            if (!retracting) {
                retracting = true;
            }
        }

        if (!retracting && !this.killed) {
            AABB sweep = new AABB(start, end).inflate(0.35);
            EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(serverOwner, start, end, sweep,
                    entity -> entity instanceof ServerPlayer target
                            && target != serverOwner
                            && GameUtils.isPlayerAliveAndSurvival(target),
                    length * length);
            if (entityHit != null && entityHit.getEntity() instanceof ServerPlayer target) {
                this.killed = true;
                retracting = true;
                Vec3 hit = entityHit.getLocation();
                serverLevel.sendParticles(ParticleTypes.CRIT, hit.x, hit.y, hit.z, 12, 0.2, 0.2, 0.2, 0.15);
                serverLevel.playSound(null, hit.x, hit.y, hit.z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS,
                        1.0f, 1.1f);
                GameUtils.killPlayer(target, true, serverOwner, Noellesroles.id("bamboo_spear"));
                length = (float) start.distanceTo(hit);
            }
        }

        if (!retracting && (this.tickCount >= EXTEND_TICKS || length >= MAX_LENGTH)) {
            retracting = true;
        }

        this.entityData.set(LENGTH, length);
        this.entityData.set(RETRACTING, retracting);
    }

    /** 设置「持有者不再持有竹枪」的检查豁免（最后一格耐久碎裂时使用）。 */
    public void setItemCheckBypassed(boolean bypassed) {
        this.itemCheckBypassed = bypassed;
    }

    /** 竹枪可以伸出最多 10 格，渲染剔除盒按最大长度扩 大，避免杆身伸出视锥剔除范围被整体裁掉。 */
    @Override
    public AABB getBoundingBoxForCulling() {
        return this.getBoundingBox().inflate(MAX_LENGTH + 1.0F);
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag compoundTag) {
        if (compoundTag.hasUUID("OwnerUuid")) {
            this.entityData.set(OWNER_UUID, Optional.of(compoundTag.getUUID("OwnerUuid")));
        }
        this.entityData.set(RETRACTING, compoundTag.getBoolean("Retracting"));
        this.killed = compoundTag.getBoolean("Killed");
        this.entityData.set(LENGTH, compoundTag.getFloat("Length"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag compoundTag) {
        UUID owner = getOwnerUuid();
        if (owner != null) {
            compoundTag.putUUID("OwnerUuid", owner);
        }
        compoundTag.putBoolean("Retracting", isRetracting());
        compoundTag.putBoolean("Killed", this.killed);
        compoundTag.putFloat("Length", getLength());
    }
}
