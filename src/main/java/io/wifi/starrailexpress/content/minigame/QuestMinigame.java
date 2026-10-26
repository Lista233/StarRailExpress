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
 * @param tier 奖励档位；具体金币数在发放时从 {@code QUSTConfig} 读取，便于服主动态调整
 */
public record QuestMinigame(String id, Component displayName, RewardTier tier) {

    /** 小游戏达人完成小游戏的金币档位（金额由 QUSTConfig 配置） */
    public enum RewardTier {
        /** 基础档：未单独分档的小游戏 */
        BASE,
        /** 低档：三张牌、水果忍者、记忆游戏等 */
        LOW,
        /** 中档：扫雷 */
        MID,
        /** 华容道（券刷到） */
        KLOTSKI
    }

    public static QuestMinigame of(String id, String translationKey) {
        return new QuestMinigame(id, Component.translatable(translationKey), RewardTier.BASE);
    }

    /** 指定奖励档位 */
    public static QuestMinigame of(String id, String translationKey, RewardTier tier) {
        return new QuestMinigame(id, Component.translatable(translationKey), tier);
    }
}
