package org.agmas.noellesroles.role.qust.roles.super_doctor;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

/**
 * 超级医生 HUD：显示 G 键治疗技能冷却状态。
 */
public class SuperDoctorHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(QUSTRoles.SUPER_DOCTOR_ID, (guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            if (client.player == null) return;

            var compOpt = QUSTComponentKeys.Keys.SUPER_DOCTOR.maybeGet(client.player);
            if (compOpt.isEmpty()) return;
            SuperDoctorPlayerComponent comp = compOpt.get();

            var font = client.font;
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            int xOffset = screenWidth - 10;
            int dy = screenHeight - 10 - font.lineHeight;

            // 治疗技能冷却
            if (comp.isHealReady()) {
                var ready = Component.translatable("hud.super_doctor.heal_ready")
                        .withStyle(ChatFormatting.GREEN);
                guiGraphics.drawString(font, ready, xOffset - font.width(ready), dy, 0xFFFFFF);
            } else {
                int seconds = (comp.getHealCooldownTicks() + 19) / 20;
                var cd = Component.translatable("hud.super_doctor.heal_cooldown", seconds)
                        .withStyle(ChatFormatting.YELLOW);
                guiGraphics.drawString(font, cd, xOffset - font.width(cd), dy, 0xFFFFFF);
            }
        });
    }
}
