package org.agmas.noellesroles.game.command_block.item;

import org.agmas.noellesroles.game.command_block.SteveMiner;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Steve 的铁镐。只能挖任务点，挖掉后不拆方块，换成随机基础掉落。
 * 耐久由注册处单独压到 32 次。
 */
public class StevePickaxeItem extends PickaxeItem {
    public StevePickaxeItem(Properties properties) {
        super(Tiers.IRON, properties);
    }

    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return SteveMiner.canMine(player, pos);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return 8.0F;
    }
}
