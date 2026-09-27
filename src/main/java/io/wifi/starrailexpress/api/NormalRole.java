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

package io.wifi.starrailexpress.api;

import net.minecraft.resources.ResourceLocation;

public class NormalRole extends SRERole {
    /**
     * 「阵营」的兼容别名。
     * <p>
     * 阵营判定现已统一收敛到唯一的权威枚举 {@link RoleTeam}（中立细分、写入文学的 Taisho 化都改在那里）。
     * 这里保留 {@code RoleType} 这个名字，是为了让既有的 {@code RoleType.XXX} 写法继续可用：
     * 每个常量的值本身就是对应的 {@link RoleTeam} 实例，因此调用处无需替换，语义也自然由 RoleTeam 决定。
     * <p>
     * 新代码推荐直接写 {@link RoleTeam}。
     */
    public static final class RoleType {
        /** 杀手阵营 */
        public static final RoleTeam KILLER = RoleTeam.KILLER;
        /** 平民阵营 */
        public static final RoleTeam CIVILIAN = RoleTeam.CIVILIAN;
        /** 警长阵营 */
        public static final RoleTeam VIGILANTE = RoleTeam.SHERIFF;
        /** 中立（泛） */
        public static final RoleTeam NEUTRALS = RoleTeam.NEUTRAL;
        /** 杀手方中立 */
        public static final RoleTeam NEUTRALS_FOR_KILLERS = RoleTeam.NEUTRAL_KILLER;
        /** 偏好中立（好人方中立） */
        public static final RoleTeam NEUTRALS_FOR_INNOCENT = RoleTeam.NEUTRAL_INNOCENT;
        /** 特殊中立 */
        public static final RoleTeam SPECIAL_NEUTRAL = RoleTeam.NEUTRAL_SPECIAL;
        /** 事件中立 */
        public static final RoleTeam EVENT_NEUTRAL = RoleTeam.NEUTRAL_EVENT;
        /** 独立胜利中立 */
        public static final RoleTeam INDEPENDENT_WIN_NEUTRAL = RoleTeam.NEUTRAL_INDEPENDENT_WIN;

        private RoleType() {
        }
    }

    /**
     * @param identifier    the mod id and name of the role
     * @param color         the role announcement color
     * @param isInnocent    whether the gun drops when a person with this role is
     *                      shot and is considered a civilian to the win conditions
     * @param canUseKiller  can see and use the killer features
     * @param moodType      the mood type a role has
     * @param maxSprintTime the maximum sprint time in ticks
     * @param canSeeTime    if the role can see the game timer
     */
    public NormalRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        this.setPassiveIncome(canUseKiller);
        this.setNeutrals(isInnocent == false && canUseKiller == false);
    }

    /**
     * 另一种构造方法
     * 
     * @param identifier
     * @param color
     * @param team     阵营枚举 {@link RoleTeam}（也可用 {@link RoleType} 的兼容常量）
     * @param moodType
     * @param maxSprintTime
     * @param canSeeTime
     */
    public NormalRole(ResourceLocation identifier, int color, RoleTeam team,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        // 必须第一行调用 this，参数直接用三元表达式计算
        this(identifier, color,
                team == RoleTeam.CIVILIAN || team == RoleTeam.SHERIFF, // isInnocent
                team == RoleTeam.KILLER, // canUseKiller
                moodType, maxSprintTime, canSeeTime);

        // 根据阵营设置额外属性（这些 setter 方法来自父类 SRERole）
        if (team == RoleTeam.SHERIFF) {
            setVigilanteTeam(true);
        }
        // 所有中立系（偏好 / 杀手方 / 事件 / 特殊 / 独立胜利）都属于中立阵营
        if (team == RoleTeam.NEUTRAL || team == RoleTeam.NEUTRAL_INNOCENT
                || team == RoleTeam.NEUTRAL_KILLER || team == RoleTeam.NEUTRAL_SPECIAL
                || team == RoleTeam.NEUTRAL_EVENT || team == RoleTeam.NEUTRAL_INDEPENDENT_WIN) {
            setNeutrals(true);
        }
        if (team == RoleTeam.NEUTRAL_INNOCENT) {
            setNeutralForInnocent(true);
        }
        if (team == RoleTeam.NEUTRAL_KILLER) {
            setNeutralForKiller(true);
        }
        if (team == RoleTeam.NEUTRAL_SPECIAL) {
            setSpecialNeutral(true);
        }
        if (team == RoleTeam.NEUTRAL_EVENT) {
            setEventNeutral(true);
        }
    }
}
