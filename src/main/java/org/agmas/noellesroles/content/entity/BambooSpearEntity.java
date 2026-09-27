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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 竹刀延伸体：右键后从持有者手部沿视线慢慢伸长，最长 10 格、<b>1.5 秒</b>伸到底；
 * 命中玩家则目标立即死亡、竹刀收回（耐久由 {@code BambooSpearItem} 在释放时扣 1 点）。
 */
public class BambooSpearEntity extends Entity {

    public static final float MAX_LENGTH = 10.0f;
    /** 伸到最长所需的时间：1.5 秒。 */
    public static final int EXTEND_TICKS = 30;
    public static final int RETRACT_TICKS = 10;

    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Optional<UUID>> OWNER_UUID = SynchedEntityData.defineId(
            BambooSpearEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<Boolean> RETRACTING = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.BOOLEAN);
    // 每 tick 由服务端把持有者的权威眼睛位置 / 视线方向同步给客户端，
    // 渲染端据此绘制，保证「屏幕上的方向」与「实际命中的方向」完全一致，
    // 且始终跟随持有者当前视角（不会卡在发射瞬间的朝向）。
    private static final EntityDataAccessor<Float> EYE_X = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EYE_Y = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> EYE_Z = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_X = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Y = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DIR_Z = SynchedEntityData.defineId(BambooSpearEntity.class,
            EntityDataSerializers.FLOAT);

    /**
     * 客户端侧「哪些玩家正在放竹刀」：UUID → 最后一次客户端 tick 时的世界时间。
     * <p>
     * 渲染端（手臂姿态 mixin）每帧都要问，所以用一张表代替遍历实体；只在客户端 tick /
     * 实体移除时写，读写都在客户端主线程。存时间戳而不是单纯存 UUID 是为了自愈——
     * 退出重进时客户端世界是整个丢掉的，不保证每个实体都走过 {@link #remove}，
     * 残留条目会因为时间戳对不上而自动失效。
     */
    private static final Map<UUID, Long> CLIENT_ACTIVE_OWNERS = new HashMap<>();

    private boolean killed;
    private float clientLength;
    private float clientPrevLength;
    /** 客户端插值用：上一 tick / 本 tick 的权威眼睛位置与视线方向。 */
    private Vec3 clientPrevEye;
    private Vec3 clientEye;
    private Vec3 clientPrevDir;
    private Vec3 clientDir;
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

    /** 客户端：该玩家是否正在放竹刀（渲染手臂姿态用）。只认这一 tick 或上一 tick 刚更新过的条目。 */
    public static boolean isClientActiveOwner(@Nullable Player player) {
        if (player == null) {
            return false;
        }
        Long stamp = CLIENT_ACTIVE_OWNERS.get(player.getUUID());
        if (stamp == null) {
            return false;
        }
        long now = player.level().getGameTime();
        return stamp == now || stamp == now - 1L;
    }

    /** 插值后的眼睛位置，未就绪返回 null。 */
    public @Nullable Vec3 getInterpolatedEye(float partialTick) {
        if (this.clientEye == null) {
            return null;
        }
        if (this.clientPrevEye == null) {
            return this.clientEye;
        }
        return this.clientPrevEye.lerp(this.clientEye, partialTick);
    }

    /** 插值后的视线方向（已归一化），未就绪返回 null。 */
    public @Nullable Vec3 getInterpolatedDir(float partialTick) {
        if (this.clientDir == null) {
            return null;
        }
        if (this.clientPrevDir == null) {
            return this.clientDir;
        }
        Vec3 lerped = this.clientPrevDir.lerp(this.clientDir, partialTick);
        return lerped.lengthSqr() < 1.0e-6 ? this.clientDir : lerped.normalize();
    }

    /** 服务端每 tick 同步的权威眼睛位置（持有者当前视角下的眼睛）。未就绪返回 null。 */
    public @Nullable Vec3 getSyncedEyePos() {
        float x = this.entityData.get(EYE_X);
        float y = this.entityData.get(EYE_Y);
        float z = this.entityData.get(EYE_Z);
        if (x == 0.0f && y == 0.0f && z == 0.0f) {
            return null;
        }
        return new Vec3(x, y, z);
    }

    /** 服务端每 tick 同步的权威视线方向（已归一化）。未就绪返回 null。 */
    public @Nullable Vec3 getSyncedLookDir() {
        float x = this.entityData.get(DIR_X);
        float y = this.entityData.get(DIR_Y);
        float z = this.entityData.get(DIR_Z);
        if (x == 0.0f && y == 0.0f && z == 0.0f) {
            return null;
        }
        return new Vec3(x, y, z).normalize();
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
        builder.define(EYE_X, 0.0f);
        builder.define(EYE_Y, 0.0f);
        builder.define(EYE_Z, 0.0f);
        builder.define(DIR_X, 0.0f);
        builder.define(DIR_Y, 0.0f);
        builder.define(DIR_Z, 0.0f);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            this.clientPrevLength = this.clientLength;
            this.clientLength = getLength();
            // 同步数据是「每 tick 一跳」的，直接拿来画会让其他玩家看到 20Hz 的抖动；
            // 这里存上一 tick 的值，渲染端再按 partialTick 插值。
            Vec3 eye = getSyncedEyePos();
            Vec3 dir = getSyncedLookDir();
            this.clientPrevEye = this.clientEye == null ? eye : this.clientEye;
            this.clientPrevDir = this.clientDir == null ? dir : this.clientDir;
            this.clientEye = eye;
            this.clientDir = dir;
            UUID owner = getOwnerUuid();
            if (owner != null) {
                CLIENT_ACTIVE_OWNERS.put(owner, this.level().getGameTime());
            }
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
        // 注意：此处视线方向是命中判定的权威方向，切勿取反，否则伤害会打偏到身后。
        Vec3 start = serverOwner.getEyePosition();
        Vec3 look = serverOwner.getLookAngle();
        // 把权威的眼睛位置与视线方向同步给客户端，渲染端据此绘制，
        // 保证视觉方向与命中方向一致，且始终跟随持有者当前视角。
        this.entityData.set(EYE_X, (float) start.x);
        this.entityData.set(EYE_Y, (float) start.y);
        this.entityData.set(EYE_Z, (float) start.z);
        this.entityData.set(DIR_X, (float) look.x);
        this.entityData.set(DIR_Y, (float) look.y);
        this.entityData.set(DIR_Z, (float) look.z);
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

    /** 设置「持有者不再持有竹刀」的检查豁免（最后一格耐久碎裂时使用）。 */
    public void setItemCheckBypassed(boolean bypassed) {
        this.itemCheckBypassed = bypassed;
    }

    @Override
    public void remove(RemovalReason reason) {
        if (this.level().isClientSide) {
            UUID owner = getOwnerUuid();
            if (owner != null) {
                CLIENT_ACTIVE_OWNERS.remove(owner);
            }
        }
        super.remove(reason);
    }

    /** 竹刀可以伸出最多 10 格，渲染剔除盒按最大长度扩大，避免杆身伸出视锥剔除范围被整体裁掉。 */
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
