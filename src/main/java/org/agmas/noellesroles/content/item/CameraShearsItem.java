/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.content.block.CameraBlock;
import io.wifi.starrailexpress.content.block_entity.CameraBlockEntity;
import io.wifi.starrailexpress.util.AdventureUsable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.List;

/**
 * 钳子（滞时鬼初始物品）
 * <p>
 * - 无耐久，可无限次使用
 * - 右键摄像头：使其失灵 90 秒
 * - 每成功破坏一个摄像头，进入 30 秒原版物品冷却
 * </p>
 */
public class CameraShearsItem extends Item implements AdventureUsable {
    private static final int USE_COOLDOWN = 30 * 20; // 每次成功破坏摄像头后的原版物品冷却
    private static final int BROKEN_TIME = 90 * 20;

    public CameraShearsItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();

        Player player = context.getPlayer();
        if (player == null) {
            return InteractionResult.PASS;
        }
        if (player.getCooldowns().isOnCooldown(this))
            return InteractionResult.PASS;
        BlockPos clickedPos = context.getClickedPos();
        BlockEntity be = level.getBlockEntity(clickedPos);
        if (!(be instanceof CameraBlockEntity cbe)) {
            return InteractionResult.PASS;
        }

        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (cbe.isBroken()) {
            player.displayClientMessage(Component.translatable("item.noellesroles.camera_shears.use_failed_broken"),
                    true);
            return InteractionResult.FAIL;
        }
        // 无耐久消耗；每成功破坏一个摄像头即进入 30 秒原版物品冷却
        player.getCooldowns().addCooldown(this, USE_COOLDOWN);
        cbe.setBroken(BROKEN_TIME);
        CameraBlock.sendTip(player, level, clickedPos);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag type) {
        tooltip.add(Component.translatable("item.noellesroles.camera_shears.tooltip")
                .withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, tooltip, type);
    }
}
