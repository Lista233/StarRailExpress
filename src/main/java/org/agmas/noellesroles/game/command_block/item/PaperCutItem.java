package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.game.command_block.CommandBlockCombat;
import org.agmas.noellesroles.game.command_block.PaperDollRole;
import org.agmas.noellesroles.role.ModRoles;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 普通的短视线斩。没有标记条件，落空不进冷却。 */
public class PaperCutItem extends Item {
    public static final int COOLDOWN = 12 * 20;
    public static final double RANGE = 5.0D;

    public PaperCutItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer self)) {
            return InteractionResultHolder.success(stack);
        }
        if (self.isSpectator() || self.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (!SREGameWorldComponent.KEY.get(level).isRole(self, ModRoles.PAPER_DOLL)) {
            return InteractionResultHolder.fail(stack);
        }
        List<ServerPlayer> hit = GazeRays.playersAlong(self, RANGE, 0.4D);
        if (hit.isEmpty()) {
            self.displayClientMessage(Component.translatable("message.noellesroles.paper_doll.missed")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.fail(stack);
        }
        boolean killed = CommandBlockCombat.tryKill(self, hit.get(0), PaperDollRole.DEATH_CUT, false,
                Component.translatable("message.noellesroles.paper_doll.blocked"));
        if (killed) {
            self.getCooldowns().addCooldown(this, COOLDOWN);
            org.agmas.noellesroles.game.command_block.CommandBlockFeedback.play(level, self,
                    net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_SWEEP, 0.55F, 1.6F);
        }
        return killed ? InteractionResultHolder.consume(stack) : InteractionResultHolder.fail(stack);
    }
}