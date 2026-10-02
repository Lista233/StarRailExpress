package org.agmas.noellesroles.role.qust.roles.pressure_monster;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

/**
 * 压力怪 HUD 显示
 * <p>
 * 显示技能冷却状态。
 */
@Environment(EnvType.CLIENT)
public class PressureMonsterHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(QUSTRoles.PRESSURE_MONSTER_ID, (guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            if (client.player == null) return;

            var comp = QUSTComponentKeys.Keys.PRESSURE_MONSTER.maybeGet(client.player).orElse(null);
            if (comp == null) return;

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight;
            int xOffset = screenWidth - 10;

            int dy = yOffset;
            int cd = comp.getCooldownSeconds();

            Component statusText;
            if (cd > 0) {
                statusText = Component.translatable("hud.pressure_monster.cooldown", cd)
                        .withStyle(ChatFormatting.RED);
            } else {
                statusText = Component.translatable("hud.pressure_monster.ready")
                        .withStyle(ChatFormatting.GREEN);
            }
            guiGraphics.drawString(font, statusText, xOffset - font.width(statusText), dy, 0xFFFFFF);
        });
    }
}
