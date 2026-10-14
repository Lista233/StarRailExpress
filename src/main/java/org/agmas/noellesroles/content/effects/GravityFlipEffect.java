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

package org.agmas.noellesroles.content.effects;

import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 重力颠倒。
 *
 * <p>只做<b>垂直方向</b>的重力反向：你会向天空「坠落」，撞到头顶的方块才算落地，跳跃改为向下蹬。
 *
 * <p>效果本身没有 tick 逻辑，纯标记；物理由以下 mixin 消费：
 * <ul>
 *     <li>{@code GravityFlipPhysicsMixin}：重力取反 + 撞天花板视为落地</li>
 *     <li>{@code GravityFlipJumpMixin}：跳跃方向反转</li>
 * </ul>
 * 模型倒立在 {@code LivingEntityRendererMixin#sre$isEntityUpsideDown} 里统一处理。
 */
public class GravityFlipEffect extends SimpleMobEffect {

    public GravityFlipEffect() {
        super(MobEffectCategory.HARMFUL, 0x7B4FE0);
    }

    /**
     * 是否处于重力颠倒。
     *
     * <p>这是反重力<b>唯一的开关</b>：三个注入点全部以它为闸门，因此效果一旦结束（或玩家死亡、
     * 变旁观者、被移除）就会立刻全部失效，且不会残留任何状态。
     *
     * <p>限制：
     * <ul>
     *     <li>只对玩家生效，其它实体一律 false；</li>
     *     <li>旁观者、创造模式玩家免疫；</li>
     *     <li>已死亡 / 已移除的玩家立即失效（防止尸体或残留实体继续被翻转）。</li>
     * </ul>
     */
    public static boolean isFlipped(Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }
        // 死亡 / 实体已移除：立即失效，不等效果自然到时
        if (player.isRemoved() || !player.isAlive()) {
            return false;
        }
        if (player.isSpectator() || player.isCreative()) {
            return false;
        }
        return player.hasEffect(ModEffects.GRAVITY_FLIP);
    }
}
