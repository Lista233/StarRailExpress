package org.agmas.noellesroles.role.qust.roles.hacker;

import com.mojang.blaze3d.systems.RenderSystem;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.util.SimpleIPLocator;

import java.util.UUID;

/**
 * 黑客HUD：显示标记玩家的信息和胜利进度
 */
public class HackerHud implements HudRenderCallback {

    private static HackerHud INSTANCE;

    public static void register() {
        if (INSTANCE == null) {
            INSTANCE = new HackerHud();
            HudRenderCallback.EVENT.register(INSTANCE);
        }
    }

    // 标记信息显示状态
    private static String displayPlayerName = null;
    private static String displayUUID = null;
    private static String displayIP = null;
    private static String displayLocation = null;
    private static int displayTicks = 0;
    private static final int DISPLAY_DURATION = 100; // 5秒 (100 ticks)

    // 发送确认信息
    private static String sendConfirmMessage = null;
    private static int sendConfirmTicks = 0;
    private static final int SEND_CONFIRM_DURATION = 60; // 3秒

    /**
     * 显示标记玩家信息（从服务端调用客户端）
     */
    public static void showMarkedInfo(String playerName, UUID uuid, String ip) {
        displayPlayerName = playerName;
        displayUUID = uuid.toString();
        displayIP = maskIP(ip);

        // 查询IP属地
        SimpleIPLocator.IPInfo info = SimpleIPLocator.locate(ip);
        displayLocation = info.getProvinceCity();

        displayTicks = DISPLAY_DURATION;
    }

    /**
     * 显示发送确认消息
     */
    public static void showSendConfirm(int count) {
        sendConfirmMessage = "已向 " + count + " 名玩家发送标记信息";
        sendConfirmTicks = SEND_CONFIRM_DURATION;
    }

    /**
     * 掩码IP地址（隐藏后两段）
     */
    private static String maskIP(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".XX.XX";
        }
        return "192.168.XX.XX";
    }

    @Override
    public void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || player.level() == null) {
            return;
        }

        // 检查玩家是否是黑客
        var gameWorld = SREGameWorldComponent.KEY.getNullable(player.level());
        if (gameWorld == null) {
            return;
        }

        var role = gameWorld.getRole(player);
        if (role == null || !role.identifier().equals(QUSTRoles.HACKER_ID)) {
            return;
        }

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();

        // 绘制标记信息（屏幕中央）
        if (displayTicks > 0) {
            renderMarkedInfo(guiGraphics, screenWidth, screenHeight);
            displayTicks--;
            if (displayTicks == 0) {
                clearDisplayInfo();
            }
        }

        // 绘制发送确认消息
        if (sendConfirmTicks > 0) {
            renderSendConfirm(guiGraphics, screenWidth, screenHeight);
            sendConfirmTicks--;
        }

        // 绘制右下角进度信息
        renderProgress(guiGraphics, screenWidth, screenHeight, player);
    }

    /**
     * 渲染标记玩家的信息（屏幕中央）
     */
    private void renderMarkedInfo(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (displayPlayerName == null) return;

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        // 背景半透明黑色
        int boxWidth = 300;
        int boxHeight = 110;
        int boxX = centerX - boxWidth / 2;
        int boxY = centerY - boxHeight / 2;

        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xDD000000);

        // 绘制边框（红色）
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFFFF0000);
        guiGraphics.fill(boxX, boxY + boxHeight - 2, boxX + boxWidth, boxY + boxHeight, 0xFFFF0000);
        guiGraphics.fill(boxX, boxY, boxX + 2, boxY + boxHeight, 0xFFFF0000);
        guiGraphics.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxHeight, 0xFFFF0000);

        // 绘制文字
        int textY = boxY + 10;

        // 标题（混淆字符）
        String obfuscated = "§k||||||||||||||||||||||||||||||||";
        Component titleObf1 = Component.literal(obfuscated);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, titleObf1, centerX, textY, 0xFFFFFFFF);
        textY += 12;

        Component titleMain = Component.literal("§4§l已标记玩家");
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, titleMain, centerX, textY, 0xFFFFFFFF);
        textY += 12;

        Component titleObf2 = Component.literal(obfuscated);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, titleObf2, centerX, textY, 0xFFFFFFFF);
        textY += 18;

        // 玩家信息
        Component nameText = Component.literal("§c§l玩家昵称：§f" + displayPlayerName);
        guiGraphics.drawString(Minecraft.getInstance().font, nameText, boxX + 10, textY, 0xFFFFFFFF);
        textY += 12;

        Component uuidText = Component.literal("§c§lUUID：§f" + displayUUID);
        guiGraphics.drawString(Minecraft.getInstance().font, uuidText, boxX + 10, textY, 0xFFFFFFFF);
        textY += 12;

        Component ipText = Component.literal("§c§lIP 地址：§f" + displayIP);
        guiGraphics.drawString(Minecraft.getInstance().font, ipText, boxX + 10, textY, 0xFFFFFFFF);
        textY += 12;

        Component locText = Component.literal("§c§lIP 属地：§f" + displayLocation);
        guiGraphics.drawString(Minecraft.getInstance().font, locText, boxX + 10, textY, 0xFFFFFFFF);
    }

    /**
     * 渲染发送确认消息
     */
    private void renderSendConfirm(GuiGraphics guiGraphics, int screenWidth, int screenHeight) {
        if (sendConfirmMessage == null) return;

        int centerX = screenWidth / 2;
        int y = screenHeight / 2 + 80;

        Component msg = Component.literal("§a§l" + sendConfirmMessage);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, msg, centerX, y, 0xFFFFFFFF);
    }

    /**
     * 渲染右下角进度信息
     */
    private void renderProgress(GuiGraphics guiGraphics, int screenWidth, int screenHeight, Player player) {
        var data = org.agmas.noellesroles.role.qust.QUSTComponentKeys.Keys.HACKER.maybeGet(player).orElse(null);
        if (data == null) return;

        String progress = data.getProgress();
        Component progressText = Component.literal("§e标记进度: §f" + progress);

        int x = screenWidth - 120;
        int y = screenHeight - 30;

        guiGraphics.drawString(Minecraft.getInstance().font, progressText, x, y, 0xFFFFFFFF);

        // 显示已标记人数
        int markedCount = data.markedPlayers.size();
        Component markedText = Component.literal("§7已标记: §f" + markedCount);
        guiGraphics.drawString(Minecraft.getInstance().font, markedText, x, y + 10, 0xFFFFFFFF);
    }

    /**
     * 清除显示信息
     */
    private static void clearDisplayInfo() {
        displayPlayerName = null;
        displayUUID = null;
        displayIP = null;
        displayLocation = null;
    }
}
