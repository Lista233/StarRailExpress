package org.agmas.noellesroles.role.qust.roles.bettor;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTRoles;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 筹客HUD：显示恶魔轮盘滚动数字和结果。
 */
public class BettorHud implements HudRenderCallback {

    private static BettorHud INSTANCE;

    public static void register() {
        if (INSTANCE == null) {
            INSTANCE = new BettorHud();
            HudRenderCallback.EVENT.register(INSTANCE);
        }
    }

    // 滚动动画状态
    private static boolean rolling = false;
    private static int resultNumber = 0;
    private static String resultDescription = "";
    private static int resultDisplayTicks = 0;
    private static final int RESULT_DURATION = 200; // 10秒
    
    // 滚动数字状态（客户端本地随机，纯视觉效果）
    private static int currentDisplayNumber = 1;
    private static int rollingTickCounter = 0;
    private static final int ROLLING_CHANGE_INTERVAL = 2; // 每2tick换一个数字

    /**
     * 开始滚动动画（从网络包调用）
     */
    public static void startRolling() {
        rolling = true;
        resultNumber = 0;
        resultDescription = "";
        resultDisplayTicks = 0;
        currentDisplayNumber = ThreadLocalRandom.current().nextInt(1, 1001);
        rollingTickCounter = 0;
    }

    /**
     * 显示结果（从网络包调用）
     */
    public static void showResult(int number, String description) {
        rolling = false;
        resultNumber = number;
        resultDescription = description;
        resultDisplayTicks = RESULT_DURATION;
    }

    @Override
    public void onHudRender(GuiGraphics guiGraphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null || player.level() == null) return;

        var gameWorld = SREGameWorldComponent.KEY.getNullable(player.level());
        if (gameWorld == null) return;

        var role = gameWorld.getRole(player);
        if (role == null || !role.identifier().equals(QUSTRoles.BETTOR_ID)) return;

        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;

        if (rolling) {
            // 渲染滚动数字（大号字体）
            renderRollingNumber(guiGraphics, centerX, centerY);
        } else if (resultDisplayTicks > 0) {
            // 渲染结果
            renderResult(guiGraphics, centerX, centerY);
            resultDisplayTicks--;
        }
    }

    /**
     * 渲染滚动中的随机数字（客户端本地随机，纯视觉效果）
     */
    private void renderRollingNumber(GuiGraphics guiGraphics, int centerX, int centerY) {
        // 每 ROLLING_CHANGE_INTERVAL tick 换一个随机数字
        rollingTickCounter++;
        if (rollingTickCounter >= ROLLING_CHANGE_INTERVAL) {
            rollingTickCounter = 0;
            currentDisplayNumber = ThreadLocalRandom.current().nextInt(1, 1001);
        }
        int displayNum = currentDisplayNumber;

        // 背景半透明黑色
        int boxWidth = 200;
        int boxHeight = 80;
        int boxX = centerX - boxWidth / 2;
        int boxY = centerY - boxHeight / 2;

        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xCC000000);

        // 红色边框
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFFFF0000);
        guiGraphics.fill(boxX, boxY + boxHeight - 2, boxX + boxWidth, boxY + boxHeight, 0xFFFF0000);
        guiGraphics.fill(boxX, boxY, boxX + 2, boxY + boxHeight, 0xFFFF0000);
        guiGraphics.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxHeight, 0xFFFF0000);

        // 标题
        Component title = Component.literal("§4§l恶魔轮盘");
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, title, centerX, boxY + 10, 0xFFFFFFFF);

        // 滚动数字（大号）
        String numStr = String.valueOf(displayNum);
        Component numText = Component.literal("§e§l" + numStr);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, numText, centerX, centerY - 5, 0xFFFFFF00);

        // 提示
        Component hint = Component.literal("§7右键停止");
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, hint, centerX, boxY + boxHeight - 18, 0xFFAAAAAA);
    }

    /**
     * 渲染最终结果
     */
    private void renderResult(GuiGraphics guiGraphics, int centerX, int centerY) {
        // 背景
        int boxWidth = 260;
        int boxHeight = 100;
        int boxX = centerX - boxWidth / 2;
        int boxY = centerY - boxHeight / 2;

        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + boxHeight, 0xDD000000);

        // 金色边框
        guiGraphics.fill(boxX, boxY, boxX + boxWidth, boxY + 2, 0xFFFFD700);
        guiGraphics.fill(boxX, boxY + boxHeight - 2, boxX + boxWidth, boxY + boxHeight, 0xFFFFD700);
        guiGraphics.fill(boxX, boxY, boxX + 2, boxY + boxHeight, 0xFFFFD700);
        guiGraphics.fill(boxX + boxWidth - 2, boxY, boxX + boxWidth, boxY + boxHeight, 0xFFFFD700);

        // 标题
        Component title = Component.literal("§6§l轮盘结果");
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, title, centerX, boxY + 10, 0xFFFFFFFF);

        // 数字
        Component numText = Component.literal("§e§l" + resultNumber);
        guiGraphics.drawCenteredString(Minecraft.getInstance().font, numText, centerX, centerY - 10, 0xFFFFFF00);

        // 效果描述
        if (!resultDescription.isEmpty()) {
            Component descText = Component.translatable(resultDescription);
            guiGraphics.drawCenteredString(Minecraft.getInstance().font, descText, centerX, centerY + 10, 0xFFFFFFFF);
        }
    }
}
