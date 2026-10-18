package org.agmas.noellesroles.game.command_block;

import java.util.List;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import org.agmas.noellesroles.role.ModRoles;

/**
 * 命令方块事件的纯规则：杀手位占用、绑定职业互斥、破坏配额。
 * 不碰世界，方便对照数值。
 */
public final class CommandBlockRules {
    private CommandBlockRules() {
    }

    /** n = ceil(原杀手数量 * 1.5)。至少 1，避免 0 杀手配置把事件做成空转。 */
    public static int markedCount(int originalKillerCount) {
        int killers = Math.max(1, originalKillerCount);
        return (int) Math.ceil(killers * 1.5D);
    }

    public static int destructionStage(int killerDeaths, int quota) {
        if (quota <= 0 || killerDeaths <= 0) {
            return 0;
        }
        if (killerDeaths >= quota) {
            return 3;
        }
        return Math.min(2, killerDeaths * 3 / quota);
    }

    /** 会占杀手名额，或像承太郎一样绑定在杀手职业上。 */
    public static boolean isKillerOccupation(SRERole role) {
        return role != null
                && role.canUseKiller()
                && !role.isInnocent()
                && !role.isNeutrals()
                && !role.isNeutralForKiller();
    }

    public static boolean isExcluded(SRERole role) {
        if (role == null || role == TMMRoles.CIVILIAN) {
            return false;
        }
        if (role == ModRoles.COMMAND_BLOCK || role == ModRoles.ABNORMAL || role == ModRoles.STEVE
                || isPoolRole(role)) {
            return true;
        }
        if (isKillerOccupation(role) || role.isKiller()) {
            return true;
        }
        for (SRERole parent : role.occupationedRoles) {
            if (isKillerOccupation(parent)) {
                return true;
            }
        }
        return false;
    }

    /** 异常改写池。Dream 复用现有杀手（铁斧 / 制酒 / 商店），不另写一套。 */
    public static List<SRERole> pool() {
        return List.of(ModRoles.STONE_MAN, ModRoles.HIM, ModRoles.ERROR_404, ModRoles.DREAM,
                ModRoles.ENTITY_303, ModRoles.ENTITY_NULL, ModRoles.FARLANDS, ModRoles.WHITE_EYES,
                ModRoles.STRINGER, ModRoles.PAPER_DOLL, ModRoles.FOG_GUEST, ModRoles.PENDULUM,
                ModRoles.CANDLE_SHADE, ModRoles.DREADLORD);
    }

    public static boolean isPoolRole(SRERole role) {
        return role != null && pool().contains(role);
    }
}
