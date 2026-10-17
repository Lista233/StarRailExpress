package org.agmas.noellesroles.role_data.killer;

import org.agmas.noellesroles.game.command_block.LegendFx;
import org.agmas.noellesroles.game.command_block.StandSpots;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

/** 纸人：一次替身，挡住就退后，然后只虚弱一小会儿。 */
public class PaperDollRoleData extends SimpleRoleData {
    public static final int ARM_TICKS = 8 * 20;
    public static final int WEAK_TICKS = 3 * 20;
    public static final int GLOW_TICKS = 2 * 20;
    public static final double BLINK = 4.0D;

    private boolean charge;
    private long armedUntil;
    private long dodgedAt = Long.MIN_VALUE;

    public PaperDollRoleData(RoleDataContext context) {
        super(context);
    }

    public boolean arm(long now) {
        if (charge && now < armedUntil) {
            return false;
        }
        charge = true;
        armedUntil = now + ARM_TICKS;
        sync();
        return true;
    }

    /**
     * @return 0 没挡住，1 刚刚挡住，2 同一刻的后续判定
     */
    public int absorb(ServerPlayer self, ServerPlayer attacker, boolean tellAttacker) {
        if (self == null || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return 0;
        }
        long now = self.serverLevel().getGameTime();
        if (now == dodgedAt) {
            return 2;
        }
        if (!charge || now >= armedUntil) {
            return 0;
        }
        charge = false;
        armedUntil = 0L;
        dodgedAt = now;
        sync();
        Vec3 from = self.position();
        ServerLevel level = self.serverLevel();
        Vec3 spot = StandSpots.find(level, from, StandSpots.horizontal(self, -1.0D), BLINK);
        if (spot != null) {
            StandSpots.place(self, spot);
        }
        self.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, WEAK_TICKS, 0, false, false, true));
        self.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_TICKS, 0, false, false, true));
        level.sendParticles(ParticleTypes.CLOUD, from.x, from.y + 1.0D, from.z, 16, 0.25D, 0.45D, 0.25D, 0.02D);
        LegendFx.ring(level, from.add(0.0D, 0.2D, 0.0D), 1.2F, LegendFx.PAPER);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.45F, 1.45F);
        self.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.dodged")
                .withStyle(ChatFormatting.WHITE), true);
        if (tellAttacker && attacker != null && attacker != self) {
            attacker.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.hit_paper")
                    .withStyle(ChatFormatting.WHITE), true);
        }
        return 1;
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("charge", charge);
        tag.putLong("until", armedUntil);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        charge = tag.getBoolean("charge");
        armedUntil = tag.getLong("until");
    }
}