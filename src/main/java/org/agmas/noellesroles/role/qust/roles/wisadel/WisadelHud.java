package org.agmas.noellesroles.role.qust.roles.wisadel;

import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.agmas.noellesroles.role.qust.QUSTRoles;

/**
 * 维什戴尔_星魂（Wisadel）HUD。
 * <p>
 * 右下角显示当前积攒的「魂灵」层数（X / 5）；当魂灵达到购买祖宗发射器所需的
 * {@link WisadelPlayerComponent#SHOTGUN_SOUL_COST} 层时，数字高亮并追加一行可购买提示。
 */
@Environment(EnvType.CLIENT)
public class WisadelHud {

    /** 上一次渲染时看到的魂灵层数；-1 表示尚未建立基线（重进游戏不误触发提示） */
    private static int lastSouls = -1;
    /** 屏幕中间「获得魂灵」提示的结束时间戳（毫秒），0 表示无活动提示 */
    private static long gainEffectEndMillis = 0L;
    /** 提示总时长：3 秒，前/后各 0.75 秒淡入淡出 */
    private static final long GAIN_EFFECT_DURATION_MS = 3000L;
    private static final long GAIN_EFFECT_FADE_MS = 750L;
    /** 深红色（DARK_RED） */
    private static final int DARK_RED_RGB = 0x8B0000;

    public static void register() {
        RoleHudRenderCallback.EVENT.register(QUSTRoles.WISADEL_ID, (guiGraphics, deltaTracker) -> {
            var client = Minecraft.getInstance();
            if (client.player == null || SREClient.isPlayerSpectator()) {
                return;
            }

            var comp = QUSTComponentKeys.Keys.WISADEL.maybeGet(client.player).orElse(null);
            if (comp == null) {
                return;
            }

            int screenWidth = guiGraphics.guiWidth();
            int screenHeight = guiGraphics.guiHeight();
            var font = client.font;
            int xOffset = screenWidth - 10;
            int yOffset = screenHeight - 10 - font.lineHeight;

            int souls = comp.getSouls();
            boolean enough = comp.hasEnoughSouls();

            // 魂灵增量→触发屏幕中间的获得提示（服务端 addSoul 后同步到本人）
            if (lastSouls >= 0 && souls > lastSouls) {
                gainEffectEndMillis = System.currentTimeMillis() + GAIN_EFFECT_DURATION_MS;
            }
            lastSouls = souls;

            // 魂灵层数：达到购买门槛用金色高亮，否则灰色
            Component soulText = Component
                    .translatable("hud.wisadel.soul", souls, WisadelPlayerComponent.MAX_SOULS)
                    .withStyle(enough ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            guiGraphics.drawString(font, soulText, xOffset - font.width(soulText), yOffset, 0xFFFFFF);

            // 达到购买门槛时提示可购买祖宗发射器
            if (enough) {
                Component readyText = Component
                        .translatable("hud.wisadel.ready")
                        .withStyle(ChatFormatting.LIGHT_PURPLE);
                guiGraphics.drawString(font, readyText, xOffset - font.width(readyText),
                        yOffset - font.lineHeight - 4, 0xFFFFFF);
            }

            // 屏幕中间：深红色「已获得 1 层魂灵」，淡入 → 保持 → 淡出，共 3 秒
            long remain = gainEffectEndMillis - System.currentTimeMillis();
            if (gainEffectEndMillis > 0 && remain <= 0) {
                gainEffectEndMillis = 0L;
            }
            if (gainEffectEndMillis > 0) {
                long elapsed = GAIN_EFFECT_DURATION_MS - remain;
                float fade;
                if (elapsed < GAIN_EFFECT_FADE_MS) {
                    fade = (float) elapsed / GAIN_EFFECT_FADE_MS;
                } else if (remain < GAIN_EFFECT_FADE_MS) {
                    fade = (float) remain / GAIN_EFFECT_FADE_MS;
                } else {
                    fade = 1.0F;
                }
                fade = Math.min(1.0F, Math.max(0.0F, fade));
                int alpha = (int) (255 * fade);
                int color = (alpha << 24) | DARK_RED_RGB;
                Component gainedText = Component.translatable("hud.wisadel.soul_gained");
                int gx = (screenWidth - font.width(gainedText)) / 2;
                int gy = screenHeight / 2;
                guiGraphics.drawString(font, gainedText, gx, gy, color);
            }
        });
    }
}
