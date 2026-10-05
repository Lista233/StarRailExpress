package org.agmas.noellesroles.role.qust.roles.wanderer;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

/**
 * 游荡者 HUD
 */
public class WandererHud {

    // ── 死亡通知覆盖层 ──
    /** 通知开始显示的时间戳（毫秒），-1 表示未激活 */
    private static long notificationStartTime = -1;
    /** 通知总持续时间（毫秒） */
    private static final long NOTIFICATION_DURATION_MS = 8000;
    /** 淡入时间（毫秒） */
    private static final long FADE_IN_MS = 1000;
    /** 淡出时间（毫秒） */
    private static final long FADE_OUT_MS = 1000;

    /** 服务端触发：开始显示死亡通知 */
    public static void showDeathNotification() {
        notificationStartTime = System.currentTimeMillis();
    }

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

            if (!comp.isGhost()) {
                // 存活状态：显示“灵魂出窍”或倒计时
                if (comp.isSoulOutActive()) {
                    float seconds = comp.getSoulOutRemainingTicks() / 20.0f;
                    Component text = Component.translatable("hud.wanderer_qust.soul_out_timer",
                            String.format("%.1f", seconds))
                            .withStyle(ChatFormatting.AQUA);
                    guiGraphics.drawString(font, text, xOffset - font.width(text), dy, 0xFFFFFF);
                    dy -= font.lineHeight + 2;
                } else {
                    // 存活但未出窍：显示技能名“灵魂出窍”
                    Component text = Component.translatable("hud.wanderer_qust.skill_alive")
                            .withStyle(ChatFormatting.DARK_AQUA);
                    guiGraphics.drawString(font, text, xOffset - font.width(text), dy, 0xFFFFFF);
                    dy -= font.lineHeight + 2;
                }
            } else if (!comp.isFinalDeath()) {
                // 幽灵状态（死亡后）：显示“显形”或“隐身”
                Component ghostText;
                if (comp.isGhostVisible()) {
                    ghostText = Component.translatable("hud.wanderer_qust.skill_dead")
                            .withStyle(ChatFormatting.YELLOW);
                } else {
                    ghostText = Component.translatable("hud.wanderer_qust.ghost_hidden")
                            .withStyle(ChatFormatting.GRAY);
                }
                guiGraphics.drawString(font, ghostText, xOffset - font.width(ghostText), dy, 0xFFFFFF);
            }

            // ── 死亡通知覆盖层（8秒，淡入淡出，置顶不闪烁） ──
            renderDeathNotification(guiGraphics, client, screenWidth, screenHeight);
        });
    }

    /**
     * 渲染死亡通知覆盖层。
     * <p>大字"我还不想死"（红色）+ 小字"您已进入游荡者状态，详见职业说明"（灰色），
     * 持续 8 秒，前后各 1 秒淡入淡出，置于最顶层。
     */
    private static void renderDeathNotification(FakeGuiGraphics guiGraphics, Minecraft client,
                                                 int screenWidth, int screenHeight) {
        if (notificationStartTime < 0) return;

        long elapsed = System.currentTimeMillis() - notificationStartTime;
        if (elapsed >= NOTIFICATION_DURATION_MS) {
            notificationStartTime = -1;
            return;
        }

        // 计算 alpha（0~255）
        float alpha;
        if (elapsed < FADE_IN_MS) {
            alpha = (float) elapsed / FADE_IN_MS;
        } else if (elapsed > NOTIFICATION_DURATION_MS - FADE_OUT_MS) {
            alpha = (float) (NOTIFICATION_DURATION_MS - elapsed) / FADE_OUT_MS;
        } else {
            alpha = 1.0f;
        }
        alpha = Math.max(0.0f, Math.min(1.0f, alpha));

        int alphaInt = (int) (alpha * 255);

        // 主标题：大字红色"我还不想死"
        var font = client.font;
        Component mainText = Component.translatable("hud.wanderer_qust.death_notify_title");
        // 使用缩放实现大字效果（2.5倍）
        float scale = 2.5f;
        int mainTextWidth = (int) (font.width(mainText) * scale);
        int mainX = (screenWidth - mainTextWidth) / 2;
        int mainY = screenHeight / 2 - 30;

        var poseStack = guiGraphics.pose();
        poseStack.pushPose();
        poseStack.translate(mainX, mainY, 0);
        poseStack.scale(scale, scale, 1.0f);
        // 红色 + alpha
        int mainColor = (alphaInt << 24) | 0xFF3333;
        guiGraphics.drawString(font, mainText, 0, 0, mainColor, false);
        poseStack.popPose();

        // 副标题：小字灰色"您已进入游荡者状态，详见职业说明"
        Component subText = Component.translatable("hud.wanderer_qust.death_notify_subtitle");
        int subTextWidth = font.width(subText);
        int subX = (screenWidth - subTextWidth) / 2;
        int subY = screenHeight / 2 + 15;
        int subColor = (alphaInt << 24) | 0xAAAAAA;
        guiGraphics.drawString(font, subText, subX, subY, subColor, false);
    }
}
