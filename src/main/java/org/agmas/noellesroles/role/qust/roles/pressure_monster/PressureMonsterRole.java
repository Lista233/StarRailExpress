package org.agmas.noellesroles.role.qust.roles.pressure_monster;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.NormalRole;
import net.minecraft.resources.ResourceLocation;

/**
 * 压力怪（Pressure Monster）角色定义
 * <ul>
 *   <li>杀手阵营，与普通杀手刷新方式相同</li>
 *   <li>拥有正常杀手商店</li>
 *   <li>技能：降低周围玩家当前 60% 的 san 值</li>
 * </ul>
 */
public class PressureMonsterRole extends NormalRole {

    public PressureMonsterRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            SRERole.MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }
}
