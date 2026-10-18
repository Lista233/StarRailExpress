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

package io.wifi.starrailexpress.progression;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 通行证可用操作（任务目标类型）。网站管理员只能从这份列表里编写任务。
 */
public final class ProgressionObjectives {
    private ProgressionObjectives() {
    }

    public static final String PLAY_MATCH = "PLAY_MATCH";
    public static final String WIN_MATCH = "WIN_MATCH";
    public static final String KILL_PLAYER = "KILL_PLAYER";
    public static final String KILL_PLAYER_DIFFERENT_TEAM = "KILL_PLAYER_DIFFERENT_TEAM";
    public static final String COMPLETE_ROUND_QUEST = "COMPLETE_ROUND_QUEST";
    public static final String COMPLETE_SPECIFIC_QUEST = "COMPLETE_SPECIFIC_QUEST";
    public static final String PLAY_AS_FACTION = "PLAY_AS_FACTION";
    public static final String BECOME_FACTION = "BECOME_FACTION";
    public static final String USE_ITEM = "USE_ITEM";
    public static final String WIN_AS_FACTION = "WIN_AS_FACTION";
    public static final String SURVIVE_MATCH = "SURVIVE_MATCH";
    public static final String PICKUP_ITEM = "PICKUP_ITEM";
    public static final String PLAY_AS_ROLE = "PLAY_AS_ROLE";
    public static final String WIN_AS_ROLE = "WIN_AS_ROLE";
    public static final String PLAY_GAME_MODE = "PLAY_GAME_MODE";
    public static final String WIN_GAME_MODE = "WIN_GAME_MODE";
    public static final String SURVIVE_AS_FACTION = "SURVIVE_AS_FACTION";
    public static final String KILL_AS_FACTION = "KILL_AS_FACTION";
    public static final String KILL_AS_ROLE = "KILL_AS_ROLE";
    public static final String LOVER_WIN = "LOVER_WIN";
    public static final String GOLD_SPENT = "GOLD_SPENT";
    public static final String GAME_TOKEN_SPENT = "GAME_TOKEN_SPENT";
    public static final String VENDING_PURCHASE = "VENDING_PURCHASE";
    public static final String LOTTERY_PURCHASE = "LOTTERY_PURCHASE";
    public static final String NEUTRAL_INDEPENDENT_WIN = "NEUTRAL_INDEPENDENT_WIN";
    public static final String REPORT_BODY = "REPORT_BODY";
    public static final String CALL_MEETING = "CALL_MEETING";
    public static final String CAST_VOTE = "CAST_VOTE";
    public static final String BUY_SHOP_ITEM = "BUY_SHOP_ITEM";
    public static final String USE_SKILL = "USE_SKILL";
    public static final String MINIGAME_TASK_COMPLETE = "MINIGAME_TASK_COMPLETE";
    public static final String SANITY_RESTORED = "SANITY_RESTORED";
    public static final String STAMINA_CONSUMED = "STAMINA_CONSUMED";
    public static final String STATUS_BAR_RESTORED = "STATUS_BAR_RESTORED";
    public static final String PLAYER_DEATH = "PLAYER_DEATH";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";
    public static final String REVIVED = "REVIVED";
    public static final String VIRTUAL_DAMAGE_DEALT = "VIRTUAL_DAMAGE_DEALT";
    public static final String SHIELD_GAINED = "SHIELD_GAINED";
    public static final String MEETING_PARTICIPATED = "MEETING_PARTICIPATED";
    public static final String CHAT_MESSAGES = "CHAT_MESSAGES";
    public static final String ZIPLINE_USED = "ZIPLINE_USED";
    public static final String WATER_DISPENSER_USED = "WATER_DISPENSER_USED";
    public static final String REDEEM_CODE = "REDEEM_CODE";
    public static final String PLAY_SPECIFIC_MAP = "PLAY_SPECIFIC_MAP";

    public record Definition(
            String id,
            String label,
            String description,
            boolean usesKey,
            String keyHint) {
    }

    public static final List<Definition> ALL = List.of(
            def(PLAY_MATCH, "完成对局", "任意模式完成一局", false, ""),
            def(WIN_MATCH, "赢得对局", "任意模式获胜一局", false, ""),
            def(KILL_PLAYER, "击杀玩家", "击杀任意玩家", false, ""),
            def(KILL_PLAYER_DIFFERENT_TEAM, "击杀敌对阵营", "击杀不同阵营的玩家", false, ""),
            def(COMPLETE_ROUND_QUEST, "完成局内任务", "完成列车小游戏/校准等局内任务", false, ""),
            def(COMPLETE_SPECIFIC_QUEST, "完成指定局内任务", "objectiveKey 为局内任务名", true, "signal_calibration"),
            def(PLAY_AS_FACTION, "以指定阵营对局", "objectiveKey=killer/civilian/neutral", true, "killer"),
            def(BECOME_FACTION, "成为指定阵营", "开局被分配到该阵营", true, "civilian,neutral"),
            def(USE_ITEM, "使用物品", "objectiveKey 为物品 ID，逗号分隔；空=任意物品", true, "minecraft:golden_apple"),
            def(WIN_AS_FACTION, "以指定阵营获胜", "objectiveKey=killer/civilian/neutral", true, "civilian"),
            def(SURVIVE_MATCH, "存活至结束", "对局结束时仍存活", false, ""),
            def(PICKUP_ITEM, "拾取物品", "objectiveKey 为物品 ID；空=任意物品", true, "starrailexpress:gun"),
            def(PLAY_AS_ROLE, "以指定职业对局", "objectiveKey 为职业 ID", true, "starrailexpress:civilian"),
            def(WIN_AS_ROLE, "以指定职业获胜", "objectiveKey 为职业 ID", true, "noellesroles:sheriff"),
            def(PLAY_GAME_MODE, "游玩指定模式", "objectiveKey 为模式 ID", true, "starrailexpress:murder"),
            def(WIN_GAME_MODE, "赢得指定模式", "objectiveKey 为模式 ID", true, "starrailexpress:murder"),
            def(SURVIVE_AS_FACTION, "以指定阵营存活", "objectiveKey=killer/civilian/neutral", true, "civilian"),
            def(KILL_AS_FACTION, "以指定阵营击杀", "objectiveKey=killer/civilian/neutral", true, "killer"),
            def(KILL_AS_ROLE, "以指定职业击杀", "objectiveKey 为职业 ID", true, "noellesroles:sheriff"),
            def(LOVER_WIN, "恋人胜利", "作为恋人阵营获胜一局", false, ""),
            def(GOLD_SPENT, "消耗金币", "累计消耗的游戏内金币(coin)", false, ""),
            def(GAME_TOKEN_SPENT, "消耗游戏币", "累计消耗的游戏代币(minigametoken)", false, ""),
            def(VENDING_PURCHASE, "售货机购买", "在售货机完成一次购买", true, "minecraft:golden_apple"),
            def(LOTTERY_PURCHASE, "抽奖机购买", "在抽奖机完成一次抽奖", false, ""),
            def(NEUTRAL_INDEPENDENT_WIN, "中立独立胜利", "中立玩家达成独立胜利", false, ""),
            def(REPORT_BODY, "报告尸体", "召开尸体会议", false, ""),
            def(CALL_MEETING, "召开紧急会议", "摇铃/紧急按钮开会", false, ""),
            def(CAST_VOTE, "参与投票", "地图/模式等投票", false, ""),
            def(BUY_SHOP_ITEM, "购买商店物品", "objectiveKey 为物品 ID；空=任意购买", true, "starrailexpress:gun"),
            def(USE_SKILL, "使用技能", "objectiveKey 为技能 ID；空=任意技能", true, ""),
            def(MINIGAME_TASK_COMPLETE, "完成小游戏任务", "完成一个局内小游戏任务", true, "starrailexpress:signal_calibration"),
            def(SANITY_RESTORED, "恢复理智值", "累计恢复的理智值", false, ""),
            def(STAMINA_CONSUMED, "消耗体力值", "累计消耗的体力值", false, ""),
            def(STATUS_BAR_RESTORED, "恢复状态条", "保暖/口渴/饥饿恢复量、污染减少量累计", true, "warmth"),
            def(PLAYER_DEATH, "玩家死亡", "因指定死因死亡一次", true, "starrailexpress:thirst"),
            def(ROLE_CHANGED, "转职次数", "转变职业一次", true, "noellesroles:sheriff"),
            def(REVIVED, "被复活次数", "被复活一次", false, ""),
            def(VIRTUAL_DAMAGE_DEALT, "造成虚拟伤害", "累计对其他玩家造成的虚拟血量伤害", false, ""),
            def(SHIELD_GAINED, "获得护盾层数", "累计获得护盾层数(普通/限时/弱效/虚拟)", true, "normal"),
            def(MEETING_PARTICIPATED, "参与会议", "参与地图会议或愚者会议", true, "map"),
            def(CHAT_MESSAGES, "聊天发言", "在聊天栏发言次数", false, ""),
            def(ZIPLINE_USED, "滑索交互", "使用滑索次数", false, ""),
            def(WATER_DISPENSER_USED, "取水次数", "从饮水机取水次数", false, ""),
            def(REDEEM_CODE, "兑换指定口令", "兑换含指定口令的兑换码自动完成", true, ""),
            def(PLAY_SPECIFIC_MAP, "游玩指定地图", "objectiveKey 为地图 ID；空=任意地图", true, ""));

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return PLAY_MATCH;
        }
        String id = raw.trim().toUpperCase(Locale.ROOT);
        for (Definition definition : ALL) {
            if (definition.id.equals(id)) {
                return definition.id;
            }
        }
        return PLAY_MATCH;
    }

    public static boolean isKnown(String raw) {
        if (raw == null || raw.isBlank()) {
            return false;
        }
        String id = raw.trim().toUpperCase(Locale.ROOT);
        return ALL.stream().anyMatch(definition -> definition.id.equals(id));
    }

    public static List<String> ids() {
        return ALL.stream().map(Definition::id).toList();
    }

    public static boolean keyMatches(String questKey, String triggerKey) {
        if (questKey == null || questKey.isBlank()) {
            return true;
        }
        if (triggerKey == null || triggerKey.isBlank()) {
            return false;
        }
        if (questKey.equalsIgnoreCase(triggerKey)) {
            return true;
        }
        return Arrays.stream(questKey.split("[,|;]"))
                .map(String::trim)
                .anyMatch(candidate -> triggerKey.equalsIgnoreCase(candidate));
    }

    private static Definition def(String id, String label, String description, boolean usesKey, String keyHint) {
        return new Definition(id, label, description, usesKey, keyHint);
    }
}
