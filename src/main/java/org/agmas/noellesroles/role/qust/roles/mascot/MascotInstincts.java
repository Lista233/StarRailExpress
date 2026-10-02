package org.agmas.noellesroles.role.qust.roles.mascot;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.client.RoleInstinctEvents;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import io.wifi.starrailexpress.util.TrueFalseAndCustomResult;

/**
 * 吉祥物本能透视注册。
 * <p>
 * 杀手开启直觉透视看吉祥物时：若吉祥物正在发光，显示金色高亮。
 * <p>
 * 使用 {@code TARGET_HIGHLIGHT_EVENT}（按被看者角色 ID 触发），
 * 因为该逻辑取决于"被看的是吉祥物"而非"看的人是吉祥物"。
 */
public class MascotInstincts {

    public static void register() {
        // 杀手看发光中的吉祥物 → 金色高亮
        RoleInstinctEvents.TARGET_HIGHLIGHT_EVENT.register(QUSTRoles.MASCOT_ID,
                (client, self, target, hasInstinct) -> {
                    if (!hasInstinct)
                        return TrueFalseAndCustomResult.pass();
                    if (SREClient.gameComponent == null)
                        return TrueFalseAndCustomResult.pass();
                    if (!SREClient.isPlayerAliveAndInSurvival())
                        return TrueFalseAndCustomResult.pass();
                    // 只有杀手才能看到金色
                    SRERole selfRole = SREClient.gameComponent.getRole(self);
                    if (selfRole == null || !selfRole.isKillerTeam())
                        return TrueFalseAndCustomResult.pass();
                    if (!(target instanceof Player targetPlayer))
                        return TrueFalseAndCustomResult.pass();

                    MascotPlayerComponent mascotComp = QUSTComponentKeys.Keys.MASCOT.maybeGet(targetPlayer).orElse(null);
                    if (mascotComp != null && mascotComp.isGlowing()) {
                        return TrueFalseAndCustomResult.custom(new java.awt.Color(255, 215, 0).getRGB()); // 金色
                    }
                    return TrueFalseAndCustomResult.pass();
                });
    }
}
