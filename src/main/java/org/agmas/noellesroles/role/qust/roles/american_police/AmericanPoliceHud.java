package org.agmas.noellesroles.role.qust.roles.american_police;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.cca.SREAbilityPlayerComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.agmas.noellesroles.role.qust.QUSTRoles;

import java.awt.*;

/**
 * 美国警察 HUD 显示
 * <ul>
 *   <li>技能剩余使用次数</li>
 *   <li>技能冷却时间</li>
 *   <li>已完成任务数</li>
 * </ul>
 */
public class AmericanPoliceHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(QUSTRoles.AMERICAN_POLICE_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator()) return;

            Font font = client.font;
            int sw = context.guiWidth();
            int sy = context.guiHeight();

            var ability = SREAbilityPlayerComponent.KEY.get(client.player);
            int charges = ability.charges;
            float cooldownSec = ability.getCooldownSeconds();

            // 技能次数
            Component chargesText = Component.translatable(
                    "hud.noellesroles.american_police.charges", charges);
            context.drawString(font, chargesText, sw - font.width(chargesText) - 8, sy - 36,
                    new Color(255, 215, 0).getRGB());

            // 冷却时间
            if (cooldownSec > 0) {
                Component cooldownText = Component.translatable(
                        "hud.noellesroles.american_police.cooldown", String.format("%.1f", cooldownSec));
                context.drawString(font, cooldownText, sw - font.width(cooldownText) - 8, sy - 48,
                        new Color(255, 100, 100).getRGB());
            }

            // 任务进度
            var comp = QUSTComponentKeys.Keys.AMERICAN_POLICE.maybeGet(client.player).orElse(null);
            if (comp != null) {
                Component taskText = Component.translatable(
                        "hud.noellesroles.american_police.tasks", comp.getCompletedTaskCount());
                context.drawString(font, taskText, sw - font.width(taskText) - 8, sy - 60,
                        new Color(200, 200, 200).getRGB());
            }
        });
    }
}
