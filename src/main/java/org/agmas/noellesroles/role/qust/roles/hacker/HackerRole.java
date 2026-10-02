package org.agmas.noellesroles.role.qust.roles.hacker;

import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.utils.RoleUtils;

/**
 * 黑客职业（林然） - 中立阵营
 * <ul>
 *   <li>中立阵营 (isNeutrals = true)</li>
 *   <li>不能使用杀手能力 (canUseKiller = false)</li>
 *   <li>伪装心情 (MoodType.FAKE)</li>
 *   <li>胜利条件：标记并发送场上3/4的人（上限15人）</li>
 *   <li>技能1：干扰芯片 - 标记玩家并显示其IP/UUID等信息（5s CD）</li>
 *   <li>技能2：发送终端 - 向所有被标记的玩家发送"您已被标记"消息（5s CD）</li>
 * </ul>
 */
public class HackerRole extends CustomWinnerRole {

    public HackerRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                      MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public WinStatus checkWin(ServerPlayer player, WinStatus winStatus) {
        // 检查黑客是否达成胜利条件
        if (RoleUtils.isPlayerTheJob(player, this)) {
            var data = org.agmas.noellesroles.role.qust.QUSTComponentKeys.Keys.HACKER.maybeGet(player).orElse(null);
            if (data != null && data.hasWon()) {
                return WinStatus.CUSTOM;
            }
        }
        return WinStatus.NOT_MODIFY;
    }

    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        if (winStatus == WinStatus.CUSTOM && RoleUtils.isPlayerTheJob(player, this)) {
            var data = org.agmas.noellesroles.role.qust.QUSTComponentKeys.Keys.HACKER.maybeGet(player).orElse(null);
            return data != null && data.hasWon();
        }
        return original;
    }
}
