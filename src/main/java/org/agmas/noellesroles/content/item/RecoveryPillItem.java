package org.agmas.noellesroles.content.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.roles.killer.dream.DreamHealthComponent;

import java.util.List;

/**
 * 康复药丸（护士体系）。
 *
 * <p>食用后将自己的虚拟血量条补满（{@link DreamHealthComponent}）。
 * 虚拟血量已满时使用不消耗。
 */
public class RecoveryPillItem extends Item {

    public RecoveryPillItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            DreamHealthComponent health = DreamHealthComponent.KEY.get(player);
            // 服务端结算：直接回满（restore 内部会判满血/存活）
            boolean restored = health.restore(DreamHealthComponent.maxHealth());
            if (!restored) {
                player.displayClientMessage(
                        Component.translatable("message.noellesroles.recovery_pill.full"), true);
                return InteractionResultHolder.fail(stack);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.HONEY_BLOCK_PLACE, SoundSource.PLAYERS, 0.8F, 1.4F);
            player.displayClientMessage(
                    Component.translatable("message.noellesroles.recovery_pill.used"), true);
            stack.consume(1, player);
        }
        player.awardStat(Stats.ITEM_USED.get(this));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
