package org.agmas.noellesroles.role.qust.roles.mascot;

import io.wifi.starrailexpress.client.SREClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.agmas.noellesroles.role.qust.QUSTRoles;

import java.awt.*;

/**
 * 吉祥物 HUD 显示
 * <ul>
 *   <li>任务进度 (x/y)</li>
 *   <li>发光倒计时 / 发光剩余时间</li>
 * </ul>
 */
public class MascotHud {
    public static void register() {
        RoleHudRenderCallback.EVENT.register(QUSTRoles.MASCOT_ID, (context, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator()) return;

            var mascot = QUSTComponentKeys.Keys.MASCOT.get(client.player);
            Font font = client.font;
            int sw = context.guiWidth();
            int sy = context.guiHeight();

            Component inventoryText = Component.translatable(
                    "hud.noellesroles.mascot.task_progress", mascot.getCompletedTaskCnt(), mascot.getRequiredTaskCnt());
            context.drawString(font, inventoryText, sw - font.width(inventoryText) - 8, sy - 36, new Color(255, 215, 0).getRGB());
            if (mascot.isCutDownFinished() && !mascot.isGlowDurationTimerFinished()) {
                Component cutDownText = Component.translatable(
                        "hud.noellesroles.mascot.glowing", mascot.getGlowDurationTimer() / 20);
                context.drawString(font, cutDownText, sw - font.width(cutDownText) - 8, sy - 48, new Color(255, 174, 0).getRGB());
            } else if (!mascot.isCutDownFinished()) {
                Component cutDownText = Component.translatable(
                        "hud.noellesroles.mascot.glow_cutdown", mascot.getGlowCutDownTimer() / 20);
                context.drawString(font, cutDownText, sw - font.width(cutDownText) - 8, sy - 48, new Color(255, 255, 255).getRGB());
            }
        });
    }
}
