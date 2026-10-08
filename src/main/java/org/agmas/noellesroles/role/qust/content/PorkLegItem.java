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

package org.agmas.noellesroles.role.qust.content;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 肘子（猪腿肉）—— 维什戴尔_星魂商店收录的可食物品。
 * <p>
 * 任何玩家吃下后获得「速度 I」效果，持续 {@value #SPEED_DURATION_TICKS} tick（10 秒）。
 * 材质暂时复用原版熟猪排。
 */
public class PorkLegItem extends Item {

    /** 食用后速度 I 的持续时长（ticks），10 秒 */
    public static final int SPEED_DURATION_TICKS = 10 * 20;

    public PorkLegItem(Properties settings) {
        super(settings);
    }

    @Override
    public @NotNull ItemStack finishUsingItem(@NotNull ItemStack stack, @NotNull Level level,
            @NotNull LivingEntity entity) {
        ItemStack result = super.finishUsingItem(stack, level, entity);
        if (!level.isClientSide && entity instanceof Player) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, SPEED_DURATION_TICKS, 0,
                    false, true, true));
        }
        return result;
    }
}
