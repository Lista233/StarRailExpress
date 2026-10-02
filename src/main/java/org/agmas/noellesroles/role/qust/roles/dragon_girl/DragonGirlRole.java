package org.agmas.noellesroles.role.qust.roles.dragon_girl;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import net.minecraft.resources.ResourceLocation;

/**
 * 龙娘角色 - 乘客阵营
 * <ul>
 *   <li>好人阵营 (isInnocent = true)</li>
 *   <li>不能使用杀手能力 (canUseKiller = false)</li>
 *   <li>真实心情 (MoodType.REAL)</li>
 *   <li>普通冲刺时间</li>
 *   <li>可以看见金币</li>
 *   <li>技能1 (G): 龙娘魅惑 - 发光+吸引5格内玩家+禁用物品栏/技能+强制移动4s</li>
 *   <li>技能2 (Shift+G): 恶龙咆哮 - 引导1.5s+范围击退(衰减)+粒子效果</li>
 * </ul>
 */
public class DragonGirlRole extends NormalRole {
    public DragonGirlRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                           SRERole.MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }
}
