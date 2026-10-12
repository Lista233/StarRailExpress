package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.CommandBlockDirector;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 命中后燃烧 0.5 秒再死亡。冷却 30 秒。 */
public class HimFireballItem extends Item {
    public static final int COOLDOWN = 30 * 20;

    public HimFireballItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isSpectator() || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        List<ServerPlayer> hit = GazeRays.playersAlong(serverPlayer, 24.0D, 0.4D);
        if (hit.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        ServerPlayer target = hit.get(0);
        target.setRemainingFireTicks(10);
        CommandBlockDirector.scheduleKill(target, serverPlayer, Noellesroles.id("him_fireball"), 10);
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }
}
