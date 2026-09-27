package org.agmas.noellesroles.content.entity;

import io.wifi.starrailexpress.content.entity.no_water_influenced.NoHeavyWaterInfluencedThrowableItemProjectile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

/**
 * 康复试剂投掷物（护士体系）。
 *
 * <p>丢出后落地碎裂，使落地点半径 4 格内的玩家获得
 * {@link ModEffects#VIRTUAL_HEALTH_RESTORE}（虚拟血量恢复 I）效果，持续 30 秒。
 * 结构参考 {@link MushroomEssenceEntity}。
 */
public class RecoveryReagentEntity extends NoHeavyWaterInfluencedThrowableItemProjectile {

    /** 效果作用半径（格）。 */
    public static final double RADIUS = 4.0;
    /** 效果持续时长（tick）＝ 30 秒。 */
    public static final int EFFECT_DURATION_TICKS = 30 * 20;

    public RecoveryReagentEntity(EntityType<? extends NoHeavyWaterInfluencedThrowableItemProjectile> type,
            Level level) {
        super(type, level);
    }

    @Override
    protected Item getDefaultItem() {
        return ModItems.RECOVERY_REAGENT;
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.playSound(null, getX(), getY(), getZ(), SoundEvents.GLASS_BREAK,
                    SoundSource.NEUTRAL, 1.0F, 1.2F);
            AABB area = new AABB(getX() - RADIUS, getY() - 2, getZ() - RADIUS,
                    getX() + RADIUS, getY() + 2, getZ() + RADIUS);
            for (ServerPlayer player : serverLevel.getEntitiesOfClass(ServerPlayer.class, area,
                    player -> player.isAlive() && !player.isSpectator())) {
                player.addEffect(new MobEffectInstance(ModEffects.VIRTUAL_HEALTH_RESTORE,
                        EFFECT_DURATION_TICKS, 0, false, true, true));
            }
            discard();
        }
    }
}
