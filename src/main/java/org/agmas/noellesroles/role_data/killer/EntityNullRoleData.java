package org.agmas.noellesroles.role_data.killer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.CommandBlockCombat;
import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.api.data.RoleDataContext;
import io.wifi.starrailexpress.api.impl.SimpleRoleData;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;

/** Null：抹除有次数，亮处站不稳，地上的东西捡不起来。 */
public class EntityNullRoleData extends SimpleRoleData {
    public static final int MAX_ERASES = 2;
    public static final int WINDUP_TICKS = 2 * 20;
    public static final double ERASE_RANGE = 12.0D;
    public static final double ERASE_KEEP = 14.0D;
    public static final ResourceLocation DEATH = Noellesroles.id("entity_null_erase");

    private int erasesLeft = MAX_ERASES;
    private UUID windupTarget;
    private long windupAt;

    public EntityNullRoleData(RoleDataContext context) {
        super(context);
    }

    public int erasesLeft() {
        return erasesLeft;
    }

    public boolean beginErase(ServerPlayer self, ServerPlayer target, long now) {
        if (erasesLeft <= 0 || windupTarget != null) {
            return false;
        }
        windupTarget = target.getUUID();
        windupAt = now + WINDUP_TICKS;
        self.addEffect(new MobEffectInstance(MobEffects.GLOWING, WINDUP_TICKS + 10, 0, false, false, true));
        self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WINDUP_TICKS + 10, 1, false, false, true));
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.charging", target.getName())
                .withStyle(ChatFormatting.DARK_GRAY), true);
        sync();
        return true;
    }

    @Override
    public void serverTick() {
        if (!(player instanceof ServerPlayer self) || !GameUtils.isPlayerAliveAndSurvival(self)) {
            return;
        }
        long now = self.serverLevel().getGameTime();
        if (now % 20 == 0 && self.level().getRawBrightness(self.blockPosition(), 0) >= 12) {
            self.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0, false, false, true));
        }
        if (windupTarget == null || now < windupAt) {
            return;
        }
        UUID targetId = windupTarget;
        windupTarget = null;
        windupAt = 0;
        ServerPlayer target = self.serverLevel().getServer().getPlayerList().getPlayer(targetId);
        boolean kept = target != null && GameUtils.isPlayerAliveAndSurvival(target)
                && self.distanceToSqr(target) <= ERASE_KEEP * ERASE_KEEP
                && self.hasLineOfSight(target);
        if (!kept) {
            self.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 4 * 20, 0, false, false, true));
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.slipped")
                    .withStyle(ChatFormatting.GRAY), true);
            sync();
            return;
        }
        pay(self);
        self.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 8 * 20, 1, false, false, true));
        self.serverLevel().playSound(null, self.blockPosition(), SoundEvents.WITHER_SHOOT, SoundSource.PLAYERS, 0.6F,
                0.5F);
        boolean killed = CommandBlockCombat.tryKill(self, target, DEATH, false,
                Component.translatable("message.noellesroles.entity_null.blocked"));
        if (killed) {
            erasesLeft--;
        }
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.left", erasesLeft)
                .withStyle(ChatFormatting.DARK_GRAY), true);
        sync();
    }

    private static void pay(ServerPlayer self) {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < self.getInventory().getContainerSize(); i++) {
            ItemStack stack = self.getInventory().getItem(i);
            if (!stack.isEmpty() && !stack.is(ModItems.VOID_STEP)) {
                slots.add(i);
            }
        }
        if (slots.isEmpty()) {
            self.hurt(self.damageSources().magic(), 6.0F);
            self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.paid_self")
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        int slot = slots.get(self.getRandom().nextInt(slots.size()));
        self.getInventory().getItem(slot).shrink(1);
        self.displayClientMessage(Component.translatable("message.noellesroles.entity_null.paid_item")
                .withStyle(ChatFormatting.DARK_GRAY), true);
    }

    public static ServerPlayer lookTarget(ServerPlayer self) {
        List<ServerPlayer> hit = GazeRays.playersAlong(self, ERASE_RANGE, 0.4D);
        return hit.isEmpty() ? null : hit.get(0);
    }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("left", erasesLeft);
        tag.putLong("windupAt", windupAt);
        if (windupTarget != null) {
            tag.putUUID("windup", windupTarget);
        }
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        erasesLeft = tag.contains("left") ? tag.getInt("left") : MAX_ERASES;
        windupAt = tag.getLong("windupAt");
        windupTarget = tag.contains("windup") ? tag.getUUID("windup") : null;
    }
}
