package org.agmas.noellesroles.role.qust.roles.dragon_girl;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

/**
 * 龙娘 HUD 显示
 * <ul>
 *   <li>显示两个技能的冷却时间与就绪状态</li>
 *   <li>魅惑持续期间显示进度条</li>
 *   <li>咆哮引导期间显示引导进度条</li>
 * </ul>
 */
public class DragonGirlHud {

    public static void register() {
        RoleHudRenderCallback.EVENT.register(QUSTRoles.DRAGON_GIRL_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null)
                return;
            if (!SREClient.isPlayerAliveAndInSurvival())
                return;

            DragonGirlPlayerComponent comp = QUSTComponentKeys.Keys.DRAGON_GIRL.get(client.player);
            if (comp == null)
                return;

            Font font = client.font;
            int screenWidth = client.getWindow().getGuiScaledWidth();
            int screenHeight = client.getWindow().getGuiScaledHeight();
            int rightMargin = 10;
            int bottomBaseY = screenHeight - 34;
            int centerX = screenWidth / 2;
            int lowerCenterY = screenHeight - 50;

            // ── 持续/引导效果：水平居中、垂直靠下，带进度条 ──
            if (comp.isCharmActive()) {
                float pct = (float) comp.charmActiveTicks / comp.charmDurationTicks;
                Component charmActiveText = Component.translatable("hud.noellesroles.dragon_girl.charm_active")
                        .withStyle(ChatFormatting.LIGHT_PURPLE);
                int textX = centerX - font.width(charmActiveText) / 2;
                context.drawString(font, charmActiveText, textX, lowerCenterY, 0xFFFFFF);
                drawProgressBar(context, centerX - 50, lowerCenterY + 12, pct, 0xFFFF66FF, 0xFF8B008B);
            } else if (comp.isRoarChanneling()) {
                float pct = 1f - (float) comp.roarChannelTicks / comp.roarChannelDurationTicks;
                Component roarChannelText = Component.translatable("hud.noellesroles.dragon_girl.roar_channeling")
                        .withStyle(ChatFormatting.AQUA);
                int textX = centerX - font.width(roarChannelText) / 2;
                context.drawString(font, roarChannelText, textX, lowerCenterY, 0xFFFFFF);
                drawProgressBar(context, centerX - 50, lowerCenterY + 12, pct, 0xFF00FFFF, 0xFF008B8B);
            }

            // ── 技能冷却/就绪状态：右下角纯文本，无进度条 ──
            Component charmStatus;
            if (comp.charmCooldown > 0) {
                int secondsLeft = (comp.charmCooldown + 19) / 20;
                charmStatus = Component.translatable("hud.noellesroles.dragon_girl.charm_cooldown", secondsLeft)
                        .withStyle(ChatFormatting.RED);
            } else {
                charmStatus = Component.translatable("hud.noellesroles.dragon_girl.charm_ready")
                        .withStyle(ChatFormatting.GREEN);
            }
            int charmX = screenWidth - rightMargin - font.width(charmStatus);
            context.drawString(font, charmStatus, charmX, bottomBaseY, 0xFFFFFF);

            Component roarStatus;
            if (comp.roarCooldown > 0) {
                int secondsLeft = (comp.roarCooldown + 19) / 20;
                roarStatus = Component.translatable("hud.noellesroles.dragon_girl.roar_cooldown", secondsLeft)
                        .withStyle(ChatFormatting.RED);
            } else {
                roarStatus = Component.translatable("hud.noellesroles.dragon_girl.roar_ready")
                        .withStyle(ChatFormatting.GREEN);
            }
            int roarX = screenWidth - rightMargin - font.width(roarStatus);
            context.drawString(font, roarStatus, roarX, bottomBaseY + 12, 0xFFFFFF);
        });
    }

    /**
     * 绘制进度条
     * @param context 渲染上下文
     * @param x 起始 x
     * @param y 起始 y
     * @param pct 进度 0~1
     * @param fillColor 填充颜色（ARGB）
     * @param bgColor 背景颜色（ARGB）
     */
    private static void drawProgressBar(FakeGuiGraphics context, int x, int y, float pct, int fillColor, int bgColor) {
        int barWidth = 100;
        int barHeight = 5;
        // 背景
        context.fill(x - 1, y - 1, x + barWidth + 1, y + barHeight + 1, 0xAA000000);
        context.fill(x, y, x + barWidth, y + barHeight, bgColor);
        // 填充
        int fillWidth = (int) (barWidth * Math.max(0, Math.min(1, pct)));
        context.fill(x, y, x + fillWidth, y + barHeight, fillColor);
    }
}
