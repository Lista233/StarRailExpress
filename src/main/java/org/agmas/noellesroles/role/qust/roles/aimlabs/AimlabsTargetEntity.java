package org.agmas.noellesroles.role.qust.roles.aimlabs;

import io.wifi.starrailexpress.api.hit.HitPriority;
import io.wifi.starrailexpress.api.hit.HitType;
import io.wifi.starrailexpress.api.hit.IsTargetObject;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Aimlabs 隐形靶标实体：无碰撞、无重力、悬浮在空中，可被枪械命中。
 * <p>
 * 实现 {@link IsTargetObject} 接口，自动进入 {@code SREHitManager} 射线拾取。
 */
public class AimlabsTargetEntity extends Entity implements IsTargetObject {

    /** 所属会话方块位置（用于命中后通知会话）。 */
    private static final EntityDataAccessor<Integer> ORIGIN_X = SynchedEntityData.defineId(
            AimlabsTargetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ORIGIN_Y = SynchedEntityData.defineId(
            AimlabsTargetEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ORIGIN_Z = SynchedEntityData.defineId(
            AimlabsTargetEntity.class, EntityDataSerializers.INT);

    /** 球体半径（同步给客户端用于渲染）。 */
    private static final EntityDataAccessor<Float> SPHERE_RADIUS = SynchedEntityData.defineId(
            AimlabsTargetEntity.class, EntityDataSerializers.FLOAT);

    public AimlabsTargetEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.setInvisible(true);
        this.setNoGravity(true);
        this.noPhysics = true;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(ORIGIN_X, 0);
        builder.define(ORIGIN_Y, 0);
        builder.define(ORIGIN_Z, 0);
        builder.define(SPHERE_RADIUS, 0.375f);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        // 无需持久化
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        // 无需持久化
    }

    // ── 数据设置/获取 ──

    public void setOrigin(net.minecraft.core.BlockPos pos) {
        this.entityData.set(ORIGIN_X, pos.getX());
        this.entityData.set(ORIGIN_Y, pos.getY());
        this.entityData.set(ORIGIN_Z, pos.getZ());
    }

    public net.minecraft.core.BlockPos getOrigin() {
        return new net.minecraft.core.BlockPos(
                entityData.get(ORIGIN_X),
                entityData.get(ORIGIN_Y),
                entityData.get(ORIGIN_Z));
    }

    public void setSphereRadius(float radius) {
        this.entityData.set(SPHERE_RADIUS, radius);
    }

    public float getSphereRadius() {
        return this.entityData.get(SPHERE_RADIUS);
    }

    // ── IsTargetObject 接口 ──

    @Override
    public boolean isValidTarget(Player attacker, HitType type) {
        if (type != HitType.GUN) return false;
        // 只允许会话拥有者击中自己的靶标
        if (level().isClientSide) return true;
        net.minecraft.core.BlockPos origin = getOrigin();
        for (AimlabsSession session : AimlabsSession.getSessionsByBlock(origin)) {
            if (session.getPlayer().getUUID().equals(attacker.getUUID())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public HitPriority getTargetPriority(HitType type) {
        return HitPriority.PRIMARY;
    }

    @Override
    public boolean onWeaponHit(Player attacker, HitType type) {
        onPracticeHit(attacker);
        return true;
    }

    // ── 命中处理 ──

    /** 被练习手枪或普通枪械命中时调用。 */
    public void onPracticeHit(Player attacker) {
        if (level().isClientSide || isRemoved()) return;

        net.minecraft.core.BlockPos origin = getOrigin();
        // 查找对应的会话（找到攻击者拥有的会话）
        for (AimlabsSession session : AimlabsSession.getSessionsByBlock(origin)) {
            if (session.getPlayer().getUUID().equals(attacker.getUUID())) {
                session.onTargetHit(this, attacker);
                break;
            }
        }

        // 击中特效
        Vec3 pos = position();
        ((ServerLevel) level()).sendParticles(ParticleTypes.CRIT,
                pos.x, pos.y, pos.z, 15, 0.2, 0.2, 0.2, 0.15);
        level().playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, 1.6F);
    }
}
