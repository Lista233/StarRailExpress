package org.agmas.noellesroles.role.qust.roles.super_recorder;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

/**
 * 超级记录员 HUD 显示
 * <p>
 * 显示标记进度和超级亡命徒时刻状态。
 */
@Environment(EnvType.CLIENT)
public class SuperRecorderHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(QUSTRoles.SUPER_RECORDER_ID, (guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            if (client.player == null) return;

            var comp = QUSTComponentKeys.Keys.SUPER_RECORDER.maybeGet(client.player).orElse(null);
            if (comp == null) return;

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int yOffset = screenHeight - 10 - font.lineHeight;
            int xOffset = screenWidth - 10;

            int dy = yOffset;

            // 标记进度
            Component markText = Component.translatable("hud.super_recorder.mark_progress",
                    comp.getMarkCount(), comp.getRequiredMarkCount())
                    .withStyle(comp.isOutlawMode() ? ChatFormatting.DARK_RED : ChatFormatting.AQUA);
            guiGraphics.drawString(font, markText, xOffset - font.width(markText), dy, 0xFFFFFF);

            // 亡命徒时刻状态
            if (comp.isOutlawMode()) {
                dy -= font.lineHeight + 2;
                Component outlawText = Component.translatable("hud.super_recorder.outlaw_mode")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD);
                guiGraphics.drawString(font, outlawText,
                        xOffset - font.width(outlawText), dy, 0xFF4444);
            }
        });
    }
}
