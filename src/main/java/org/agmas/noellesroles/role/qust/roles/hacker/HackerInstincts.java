package org.agmas.noellesroles.role.qust.roles.hacker;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;

/**
 * 黑客本能透视注册。
 * <p>
 * 黑客开启直觉透视时：已标记的玩家显示红色高亮，未标记的玩家显示灰色高亮。
 * <p>
 * 使用 {@code OBSERVER_HIGHLIGHT_EVENT}（按观察者角色 ID 触发），
 * 因为该逻辑取决于“看的人是黑客”而非“被看的是谁”。
 */
public class HackerInstincts {

    public static void register() {
        // 黑客看已标记玩家 → 红色高亮
        RoleInstinctEvents.OBSERVER_HIGHLIGHT_EVENT.register(QUSTRoles.HACKER_ID,
                (client, self, target, hasInstinct) -> {
                    if (!hasInstinct)
                        return TrueFalseAndCustomResult.pass();
                    if (SREClient.gameComponent == null)
                        return TrueFalseAndCustomResult.pass();
                    if (!SREClient.isPlayerAliveAndInSurvival())
                        return TrueFalseAndCustomResult.pass();
                    if (!(target instanceof Player targetPlayer))
                        return TrueFalseAndCustomResult.pass();

                    // 获取黑客自己的组件数据（已通过 CCA 同步到客户端）
                    var hackerData = QUSTComponentKeys.Keys.HACKER.maybeGet(self).orElse(null);
                    if (hackerData == null)
                        return TrueFalseAndCustomResult.pass();

                    // 已标记玩家 → 红色高亮，未标记 → 灰色高亮
                    if (hackerData.isMarked(targetPlayer.getUUID())) {
                        return TrueFalseAndCustomResult.custom(new java.awt.Color(255, 50, 50).getRGB());
                    }
                    return TrueFalseAndCustomResult.custom(new java.awt.Color(128, 128, 128).getRGB());
                });
    }
}
