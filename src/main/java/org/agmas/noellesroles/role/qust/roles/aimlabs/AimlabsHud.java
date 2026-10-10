package org.agmas.noellesroles.role.qust.roles.aimlabs;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.CommonHudRenderCallback;

/**
 * Aimlabs HUD 渲染：支持计时模式和计次模式。
 */
public class AimlabsHud {

    public static void register() {
        CommonHudRenderCallback.EVENT.register((ctx, deltaTracker) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) return;

            int screenWidth = ctx.guiWidth();
            int screenHeight = ctx.guiHeight();
            int centerX = screenWidth / 2;

            // ── 最终成绩显示（练习结束后 5 秒） ──
            if (AimlabsClientState.getFinalScoreDisplayTicks() > 0 && AimlabsClientState.getFinalScore() >= 0) {
                ctx.drawCenteredString(client.font,
                        Component.literal("练习结束！").withStyle(ChatFormatting.GOLD),
                        centerX, screenHeight / 2 - 30, 0xFFFFFF);

                // 计次模式显示用时，计时模式显示得分
                if (AimlabsClientState.getMode() == AimlabsBlockEntity.GameMode.COUNT) {
                    float seconds = AimlabsClientState.getFinalElapsedTicks() / 20.0f;
                    String timeText = String.format("用时: %.2f 秒", seconds);
                    ctx.drawCenteredString(client.font,
                            Component.literal(timeText).withStyle(ChatFormatting.AQUA),
                            centerX, screenHeight / 2 - 10, 0xFFFFFF);
                } else {
                    String scoreText = "最终得分: " + AimlabsClientState.getFinalScore();
                    ctx.drawCenteredString(client.font,
                            Component.literal(scoreText).withStyle(ChatFormatting.YELLOW),
                            centerX, screenHeight / 2 - 10, 0xFFFFFF);
                }
                return;
            }

            // ── 活跃会话 ──
            if (!AimlabsClientState.isActive()) return;

            if (AimlabsClientState.getMode() == AimlabsBlockEntity.GameMode.COUNT) {
                // ── 计次模式：击中数 + 已用时间 ──
                int hits = AimlabsClientState.getScore();
                int target = AimlabsClientState.getTargetHits();
                float elapsed = AimlabsClientState.getElapsedTicks() / 20.0f;

                String hitsText = hits + " / " + target + " 击中";
                ctx.drawCenteredString(client.font,
                        Component.literal(hitsText).withStyle(ChatFormatting.YELLOW),
                        centerX, 20, 0xFFFFFF);

                String timeText = String.format("%.1f 秒", elapsed);
                ctx.drawCenteredString(client.font,
                        Component.literal(timeText).withStyle(ChatFormatting.AQUA),
                        centerX, 35, 0xFFFFFF);
            } else {
                // ── 计时模式：倒计时 + 分数 ──
                int remaining = AimlabsClientState.getRemainingSeconds();
                int score = AimlabsClientState.getScore();

                ChatFormatting timeColor = remaining <= 5 ? ChatFormatting.RED : ChatFormatting.WHITE;
                ctx.drawCenteredString(client.font,
                        Component.literal(remaining + "s").withStyle(timeColor),
                        centerX, 20, 0xFFFFFF);

                ctx.drawCenteredString(client.font,
                        Component.literal("得分: " + score).withStyle(ChatFormatting.YELLOW),
                        centerX, 35, 0xFFFFFF);
            }
        });
    }
}
