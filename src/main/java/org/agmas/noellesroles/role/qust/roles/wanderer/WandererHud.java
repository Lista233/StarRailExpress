package org.agmas.noellesroles.role.qust.roles.wanderer;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

/**
 * 游荡者 HUD
 */
public class WandererHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(QUSTRoles.WANDERER_ID, (guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            if (client.player == null) return;

            var comp = QUSTComponentKeys.Keys.WANDERER.maybeGet(client.player).orElse(null);
            if (comp == null) return;

            var font = client.font;
            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            int xOffset = screenWidth - 10;
            int dy = screenHeight - 10 - font.lineHeight;

            // 灵魂出窍状态
            if (comp.isSoulOutActive()) {
                float seconds = comp.getSoulOutRemainingTicks() / 20.0f;
                Component text = Component.translatable("hud.wanderer_qust.soul_out",
                        String.format("%.1f", seconds))
                        .withStyle(ChatFormatting.AQUA);
                guiGraphics.drawString(font, text, xOffset - font.width(text), dy, 0xFFFFFF);
                dy -= font.lineHeight + 2;
            }

            // 幽灵状态
            if (comp.isGhost() && !comp.isFinalDeath()) {
                Component ghostText;
                if (comp.isGhostVisible()) {
                    ghostText = Component.translatable("hud.wanderer_qust.ghost_visible")
                            .withStyle(ChatFormatting.YELLOW);
                } else {
                    ghostText = Component.translatable("hud.wanderer_qust.ghost_hidden")
                            .withStyle(ChatFormatting.GRAY);
                }
                guiGraphics.drawString(font, ghostText, xOffset - font.width(ghostText), dy, 0xFFFFFF);
                dy -= font.lineHeight + 2;
            }
        });
    }
}
