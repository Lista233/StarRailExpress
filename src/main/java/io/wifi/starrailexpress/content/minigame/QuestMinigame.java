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

package io.wifi.starrailexpress.content.minigame;

import net.minecraft.network.chat.Component;

/**
 * 小游戏任务点的小游戏接口（纯服务端安全）
 * 只存储小游戏的 ID 和显示名称，不引用任何客户端类
 *
 * @param coinReward 小游戏达人完成该小游戏后获得的金币数
 */
public record QuestMinigame(String id, Component displayName, int coinReward) {

    /** 默认金币奖励（普通难度小游戏） */
    public static final int DEFAULT_COIN_REWARD = 20;

    public static QuestMinigame of(String id, String translationKey) {
        return new QuestMinigame(id, Component.translatable(translationKey), DEFAULT_COIN_REWARD);
    }

    /** 指定自定义金币奖励 */
    public static QuestMinigame of(String id, String translationKey, int coinReward) {
        return new QuestMinigame(id, Component.translatable(translationKey), coinReward);
    }
}
