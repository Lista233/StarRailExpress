package org.agmas.noellesroles.game.command_block.item;

import java.util.List;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.CommandBlockCombat;

import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** 投出后贯穿视线上的玩家，矛留在手里。冷却 20 秒。 */
public class StoneSpearItem extends Item {
    public static final int COOLDOWN = 20 * 20;

    public StoneSpearItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (player.getCooldowns().isOnCooldown(this) || player.isSpectator()) {
            return InteractionResultHolder.fail(stack);
        }
        CommandBlockCombat.pierce(serverPlayer, 18.0D, Noellesroles.id("stone_spear"));
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    public static Item.Properties properties() {
        return new Item.Properties().stacksTo(1).durability(64);
    }
}
