package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.CommandBlockThrownEntity;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 右键沿视线伸出，贯穿命中的玩家后收回手里。冷却 20 秒。 */
public class StoneSpearItem extends Item {
    public static final int COOLDOWN = 20 * 20;

    public StoneSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this) || player.isSpectator()) {
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!CommandBlockThrownEntity.thrustSpear(serverPlayer)) {
                return InteractionResultHolder.fail(stack);
            }
            player.getCooldowns().addCooldown(this, COOLDOWN);
        }
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity entity,
            InteractionHand hand) {
        return use(player.level(), player, hand).getResult();
    }

    public static Item.Properties properties() {
        return new Item.Properties().stacksTo(1).durability(64);
    }
}
