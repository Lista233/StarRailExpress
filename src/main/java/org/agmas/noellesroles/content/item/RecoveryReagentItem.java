package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.index.TMMSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.content.entity.RecoveryReagentEntity;
import org.agmas.noellesroles.init.ModEntities;

import java.util.List;

/**
 * 康复试剂（护士体系）。
 *
 * <p>右键丢出，落地后使半径 4 格内的玩家获得「虚拟血量恢复」效果 30 秒
 * （见 {@link RecoveryReagentEntity}）。
 */
public class RecoveryReagentItem extends Item {

    public RecoveryReagentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            RecoveryReagentEntity entity = new RecoveryReagentEntity(ModEntities.RECOVERY_REAGENT, level);
            entity.setOwner(player);
            entity.setPosRaw(player.getX(), player.getEyeY() - 0.1, player.getZ());
            entity.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 0.5F, 1.0F);
            level.addFreshEntity(entity);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), TMMSounds.ITEM_GRENADE_THROW,
                SoundSource.NEUTRAL, 0.5F, 1.0F);
        player.awardStat(Stats.ITEM_USED.get(this));
        stack.consume(1, player);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, flag);
    }
}
