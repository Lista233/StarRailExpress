package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.CommandBlockDirector;
import org.agmas.noellesroles.game.command_block.SteveMiner;
import java.util.List;
import net.minecraft.world.item.TooltipFlag;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Steve 的拉杆。使用后把下一次命令方块改写换成另一个被标记的玩家，并重新抽职业。
 */
public class SteveLeverItem extends Item {
    public SteveLeverItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!SteveMiner.canAct(player)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level.isClientSide || !(player instanceof ServerPlayer self) || !(level instanceof ServerLevel server)) {
            return InteractionResultHolder.success(stack);
        }
        if (!CommandBlockDirector.rerollNext(server)) {
            self.displayClientMessage(Component.translatable("message.noellesroles.steve.lever_none")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.fail(stack);
        }
        stack.shrink(1);
        server.playSound(null, self.getX(), self.getY(), self.getZ(), SoundEvents.LEVER_CLICK, SoundSource.PLAYERS,
                0.8F, 1.2F);
        self.displayClientMessage(Component.translatable("message.noellesroles.steve.lever_used")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip").withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
