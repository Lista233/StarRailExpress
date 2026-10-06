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

import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * 真相之书（超级记录员专属）。
 *
 * 商店购买后右键打开标记界面（仅显示未标记玩家）；可丢出。
 * 每次使用消耗并生成新快照，揭示当前未标记玩家的身份。
 * 消耗后自动将所有未标记玩家以正确职业标记到记录笔记中。
 */
public class TruthBookItem extends Item {

    /** 静态回调，由客户端设置用于打开 GUI。 */
    public static Runnable openScreenCallback = null;

    public TruthBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        // 必须存活（旁观/死亡不可用）
        if (!GameUtils.isPlayerAliveAndSurvival(user)) {
            return InteractionResultHolder.fail(stack);
        }

        // 服务端：自动标记所有未标记玩家（使用正确职业）
        if (!world.isClientSide()) {
            // 标记逻辑已由客户端发送 AutoMarkFromTruthBook 包在服务端处理
        }

        if (world.isClientSide()) {
            // 发送 C2S 包请求服务端自动标记
            net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
                    new org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderPayload.AutoMarkFromTruthBook());
            if (openScreenCallback != null) {
                openScreenCallback.run();
            }
        }

        // 消耗物品（每本书只能用一次）
        stack.shrink(1);
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }
}
