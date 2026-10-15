package org.agmas.noellesroles.role.qust.roles.hacker;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * 被标记玩家的信息框 HUD。
 * <p>
 * 「您已被标记」大标题下方（屏幕中央偏下）以信息框形式展示昵称、UUID、
 * IP 地址与 IP 属地，替代原先刷在聊天栏的详细信息。
 */
public class BeenMarkedHud implements HudRenderCallback {

    private static BeenMarkedHud INSTANCE;

    public static void register() {
        if (INSTANCE == null) {
            INSTANCE = new BeenMarkedHud();
            HudRenderCallback.EVENT.register(INSTANCE);
        }
    }

    // 信息显示状态（使用时间戳而非 tick 计数，避免移动/转视角时渲染回调不稳定导致提前消失）
    private static String displayName = null;
    private static String displayUuid = null;
    private static String displayIp = null;
    private static String displayLocation = null;
    private static long displayEndTime = 0;
    private static final long DISPLAY_DURATION_MS = 1500; // 1.5秒

    /**
     * 显示被标记信息（传入已掩码的 IP 与属地查询结果）
     */
    public static void show(String playerName, String uuid, String maskedIp, String location) {
        displayName = playerName;
        displayUuid = uuid;
        displayIp = maskedIp;
        displayLocation = location;
        displayEndTime = System.currentTimeMillis() + DISPLAY_DURATION_MS;
    }

    @Override
    public void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || displayName == null || System.currentTimeMillis() >= displayEndTime) {
            if (displayName != null && System.currentTimeMillis() >= displayEndTime) {
                displayName = null;
                displayUuid = null;
                displayIp = null;
                displayLocation = null;
            }
            return;
        }

        int centerX = mc.getWindow().getGuiScaledWidth() / 2;
        int centerY = mc.getWindow().getGuiScaledHeight() / 2;

        // 信息框位于屏幕中央偏下：原版副标题在中央下方约 10~20px，再往下留出空隙
        int boxWidth = 300;
        int boxHeight = 88;
        int boxX = centerX - boxWidth / 2;
        int boxY = centerY + 30;

        // 背景半透明黑色 + 红色边框（与黑客侧标记信息框风格一致）
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xDD000000);
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFFFF0000);
        guiGraphics.fill(boxX, boxY + boxHeight - 2, boxX + boxWidth, boxY + boxHeight, 0xFFFF0000);
        guiGraphics.fill(boxX, boxY, boxX + 2, boxY + boxHeight, 0xFFFF0000);
        guiGraphics.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxHeight, 0xFFFF0000);

        String obfuscated = "§k||||||||||||||||||||||||||||||||";
        int textY = boxY + 8;

        // 标题装饰（混淆字符）
        guiGraphics.drawCenteredString(mc.font, Component.literal(obfuscated), centerX, textY, 0xFFFFFFFF);
        textY += 12;

        // 详细信息
        guiGraphics.drawString(mc.font, Component.literal("§c§l玩家昵称：§f" + displayName), boxX + 10, textY, 0xFFFFFFFF);
        textY += 12;
        guiGraphics.drawString(mc.font, Component.literal("§c§lUUID：§f" + displayUuid), boxX + 10, textY, 0xFFFFFFFF);
        textY += 12;
        guiGraphics.drawString(mc.font, Component.literal("§c§lIP 地址：§f" + displayIp), boxX + 10, textY, 0xFFFFFFFF);
        textY += 12;
        guiGraphics.drawString(mc.font, Component.literal("§c§lIP 属地：§f" + displayLocation), boxX + 10, textY, 0xFFFFFFFF);
        textY += 12;

        // 底部装饰（混淆字符）
        guiGraphics.drawCenteredString(mc.font, Component.literal(obfuscated), centerX, textY, 0xFFFFFFFF);

        // 使用时间戳判断结束，不再依赖每帧递减
    }
}
