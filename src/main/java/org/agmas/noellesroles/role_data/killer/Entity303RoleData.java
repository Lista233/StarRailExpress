package org.agmas.noellesroles.role_data.killer;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.agmas.noellesroles.game.command_block.item.GazeRays;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.morph.MorphApi;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/** 实体303：记住偷过的脸，戴着脸时是空壳。 */
public class Entity303RoleData extends SimpleRoleData {
    public static final int MORPH_TICKS = 18 * 20;
    public static final int RECOGNIZE_LOCK_TICKS = 12 * 20;
    public static final int KILL_LOCK_TICKS = 15 * 20;

    private final Set<UUID> known = new HashSet<>();
    private UUID copied;
    private boolean wearing;
    private long morphLockUntil;

    public Entity303RoleData(RoleDataContext context) {
        super(context);
    }

    public boolean knows(UUID id) {
        return id != null && known.contains(id);
    }

    public boolean morphLocked(long now) {
        return now < morphLockUntil;
    }

    public void remember(ServerPlayer target, long now) {
        known.add(target.getUUID());
        copied = target.getUUID();
        wearing = true;
        sync();
    }

    public void onKill(ServerPlayer self, long now) {
        morphLockUntil = now + KILL_LOCK_TICKS;
        self.addEffect(new MobEffectInstance(MobEffects.GLOWING, 8 * 20, 0, false, false, true));
        sync();
    }

    @Override
    public void clear() {
        if (player instanceof ServerPlayer sp) {
            MorphApi.clearMorph(sp);
        }
        wearing = false;
        copied = null;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer self) || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return;
        }
        long now = self.serverLevel().getGameTime();
        boolean morphed = MorphApi.isMorphed(self);
        if (wearing && !morphed) {
            wearing = false;
            copied = null;
            self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 4 * 20, 0, false, false, true));
            sync();
            return;
        }
        if (!morphed || copied == null) {
            return;
        }
        if (now % 15 == 0) {
            self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
        }
        ServerPlayer original = self.serverLevel().getServer().getPlayerList().getPlayer(copied);
        if (original != null && GameUtils.isPlayerAliveAndSurvival(original)
                && self.distanceToSqr(original) <= 12.0D * 12.0D
                && GazeRays.lookingAt(original, self, 0.55D)) {
            shatter(self, now);
        }
    }

    private void shatter(ServerPlayer self, long now) {
        MorphApi.clearMorph(self);
        wearing = false;
        copied = null;
        morphLockUntil = now + RECOGNIZE_LOCK_TICKS;
        self.addEffect(new MobEffectInstance(MobEffects.GLOWING, 8 * 20, 0, false, false, true));
        self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 6 * 20, 1, false, false, true));
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_303.recognized")
                .withStyle(ChatFormatting.RED), true);
        sync();
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        ListTag list = new ListTag();
        for (UUID id : known) {
            list.add(StringTag.valueOf(id.toString()));
        }
        tag.put("known", list);
        tag.putBoolean("wearing", wearing);
        tag.putLong("lock", morphLockUntil);
        if (copied != null) {
            tag.putUUID("copied", copied);
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        known.clear();
        if (tag.contains("known", Tag.TAG_LIST)) {
            ListTag list = tag.getList("known", Tag.TAG_STRING);
            for (Tag entry : list) {
                try {
                    known.add(UUID.fromString(entry.getAsString()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        wearing = tag.getBoolean("wearing");
        morphLockUntil = tag.getLong("lock");
        copied = tag.contains("copied") ? tag.getUUID("copied") : null;
    }
}
