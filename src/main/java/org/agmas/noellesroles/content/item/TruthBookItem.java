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
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderPlayerComponent;

/**
 * 真相之书（超级记录员专属）。
 *
 * 商店购买后右键打开标记界面（仅显示未标记玩家）；可丢出。
 * 首次使用时生成快照，之后内容固定不变（不会因标记进度而刷新）。
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

        // 服务端：首次使用时生成真相之书快照（内容固定，不再刷新）
        if (!world.isClientSide() && user instanceof net.minecraft.server.level.ServerPlayer sp) {
            var comp = QUSTComponentKeys.Keys.SUPER_RECORDER.maybeGet(sp).orElse(null);
            if (comp != null && !comp.hasTruthBookSnapshot()) {
                comp.captureTruthBookSnapshot();
            }
        }

        if (world.isClientSide()) {
            if (openScreenCallback != null) {
                openScreenCallback.run();
            }
        }

        // 不消耗物品
        return InteractionResultHolder.sidedSuccess(stack, world.isClientSide());
    }
}
