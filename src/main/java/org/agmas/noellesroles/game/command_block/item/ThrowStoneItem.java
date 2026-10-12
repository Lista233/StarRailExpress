package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.init.ModEffects;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 命中后眩晕 5 秒。买到的石头用一次少一个。 */
public class ThrowStoneItem extends Item {
    public ThrowStoneItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isSpectator()) {
            return InteractionResultHolder.fail(stack);
        }
        List<ServerPlayer> hit = GazeRays.playersAlong(serverPlayer, 16.0D, 0.35D);
        if (hit.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        ServerPlayer target = hit.get(0);
        target.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 5 * 20, 0, false, true, true));
        target.addEffect(new MobEffectInstance(ModEffects.TURN_BANED, 5 * 20, 0, false, true, true));
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }
}
