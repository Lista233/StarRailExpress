package org.agmas.noellesroles.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.role.qust.roles.bettor.BettorPayload;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 恶魔轮盘抽取界面。
 * <p>
 * 滚动阶段：中央显示快速跳动的随机数字（1-1000），背景使用末影水晶纹理。
 * 结果阶段：显示最终数字和获得的效果描述，数秒后自动关闭。
 */
public class DevilRouletteScreen extends Screen {

    // 末影水晶纹理
    private static final ResourceLocation ENDER_CRYSTAL_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/endercrystal/endercrystal.png");

    // 状态
    private boolean rolling = false;
    private int resultNumber = 0;
    private String resultDescription = "";
    private int resultDisplayTicks = 0;
    private static final int RESULT_DURATION = 200; // 10秒

    // 滚动数字视觉
    private int currentDisplayNumber = 1;
    private int rollingTickCounter = 0;
    private static final int ROLLING_CHANGE_INTERVAL = 2;

    // 末影水晶动画
    private float crystalAnimTime = 0;

    public DevilRouletteScreen() {
        super(GameNarrator.NO_TITLE);
    }

    /**
     * 开始滚动（由网络处理器调用）
     */
    public void startRolling() {
        this.rolling = true;
        this.resultNumber = 0;
        this.resultDescription = "";
        this.resultDisplayTicks = 0;
        this.currentDisplayNumber = ThreadLocalRandom.current().nextInt(1, 1001);
        this.rollingTickCounter = 0;
    }

    /**
     * 显示结果（由网络处理器调用）
     */
    public void showResult(int number, String description) {
        this.rolling = false;
        this.resultNumber = number;
        this.resultDescription = description;
        this.resultDisplayTicks = RESULT_DURATION;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        crystalAnimTime += 0.05f;

        if (rolling) {
            rollingTickCounter++;
            if (rollingTickCounter >= ROLLING_CHANGE_INTERVAL) {
                rollingTickCounter = 0;
                currentDisplayNumber = ThreadLocalRandom.current().nextInt(1, 1001);
            }
        }
        // 结果视图不再自动关闭，由玩家按 ESC 退出
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        // 半透明黑色背景
        renderBackground(g, mouseX, mouseY, delta);

        int centerX = width / 2;
        int centerY = height / 2;

        if (rolling) {
            renderRollingView(g, centerX, centerY);
        } else if (resultDisplayTicks > 0) {
            renderResultView(g, centerX, centerY);
        }

        super.render(g, mouseX, mouseY, delta);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 半透明黑色覆盖
        g.fill(0, 0, width, height, 0xCC000000);
    }

    /**
     * 渲染滚动中的视图
     */
    private void renderRollingView(GuiGraphics g, int centerX, int centerY) {
        // 绘制末影水晶纹理作为背景装饰
        renderCrystalTexture(g, centerX, centerY - 50);

        // 标题
        Component title = Component.translatable("screen.bettor.devil_roulette.title")
                .withStyle(net.minecraft.ChatFormatting.DARK_RED, net.minecraft.ChatFormatting.BOLD);
        g.drawCenteredString(font, title, centerX, centerY - 10, 0xFFFF4444);

        // 滚动数字（大号）
        String numStr = String.valueOf(currentDisplayNumber);
        Component numText = Component.literal(numStr)
                .withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD);
        int numWidth = font.width(numText);
        g.pose().pushPose();
        g.pose().translate(centerX - numWidth * 1.5f, centerY + 15, 0);
        g.pose().scale(3.0f, 3.0f, 1.0f);
        g.drawString(font, numText, 0, 0, 0xFFFFFF00);
        g.pose().popPose();

        // 提示
        Component hint = Component.translatable("screen.bettor.devil_roulette.hint")
                .withStyle(net.minecraft.ChatFormatting.GRAY);
        g.drawCenteredString(font, hint, centerX, centerY + 55, 0xFFAAAAAA);
    }

    /**
     * 渲染结果视图
     */
    private void renderResultView(GuiGraphics g, int centerX, int centerY) {
        // 绘制末影水晶纹理
        renderCrystalTexture(g, centerX, centerY - 60);

        // 标题
        Component title = Component.translatable("screen.bettor.devil_roulette.result_title")
                .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD);
        g.drawCenteredString(font, title, centerX, centerY - 15, 0xFFFFD700);

        // 数字
        Component numText = Component.literal(String.valueOf(resultNumber))
                .withStyle(net.minecraft.ChatFormatting.YELLOW, net.minecraft.ChatFormatting.BOLD);
        int numWidth = font.width(numText);
        g.pose().pushPose();
        g.pose().translate(centerX - numWidth * 1.5f, centerY + 5, 0);
        g.pose().scale(3.0f, 3.0f, 1.0f);
        g.drawString(font, numText, 0, 0, 0xFFFFFF00);
        g.pose().popPose();

        // 效果描述
        if (resultDescription != null && !resultDescription.isEmpty()) {
            Component descText = Component.translatable(resultDescription);
            // 根据结果类型着色
            int descColor = getResultColor(resultDescription);
            g.drawCenteredString(font, descText, centerX, centerY + 40, descColor);
        }

        // ESC 退出提示
        Component escHint = Component.translatable("screen.bettor.devil_roulette.esc_hint")
                .withStyle(net.minecraft.ChatFormatting.GRAY);
        g.drawCenteredString(font, escHint, centerX, centerY + 65, 0xFF888888);
    }

    /**
     * 渲染末影水晶纹理（简化版，使用原版纹理的 beam 部分）
     */
    private void renderCrystalTexture(GuiGraphics g, int centerX, int centerY) {
        int size = 40;
        // 使用简单的菱形 + 发光效果模拟水晶
        float pulse = (float) (Math.sin(crystalAnimTime * 2) * 0.3 + 0.7);
        int alpha = (int) (pulse * 180);

        // 尝试渲染末影水晶纹理
        try {
            g.pose().pushPose();
            // 绘制纹理（使用底部 32x32 区域作为装饰）
            g.blit(ENDER_CRYSTAL_TEXTURE,
                    centerX - size / 2, centerY - size / 2,
                    size, size,
                    0, 32, 32, 32,
                    64, 64);
            // 发光叠加
            g.fill(centerX - size / 2, centerY - size / 2,
                    centerX + size / 2, centerY + size / 2,
                    (alpha << 24) | 0xCC44FF);
            g.pose().popPose();
        } catch (Exception e) {
            // 纹理加载失败时使用简单方块
            g.fill(centerX - 16, centerY - 16, centerX + 16, centerY + 16, 0xCC8844FF);
        }
    }

    /**
     * 根据效果描述翻译键返回颜色
     */
    private int getResultColor(String desc) {
        if (desc.contains("win")) return 0xFFFFD700;       // 金色 - 胜利
        if (desc.contains("death")) return 0xFFFF4444;      // 红色 - 死亡
        if (desc.contains("derringer") || desc.contains("revolver")) return 0xFF44FF44; // 绿色 - 武器
        if (desc.contains("shield")) return 0xFF44AAFF;     // 蓝色 - 护盾
        if (desc.contains("invincible")) return 0xFFFF44FF; // 紫色 - 无敌
        if (desc.contains("speed")) return 0xFF44FFFF;      // 青色 - 速度
        return 0xFFFFFFFF;                                   // 白色 - 其他
    }

    @Override
    public boolean shouldCloseOnEsc() {
        // 滚动中不允许 ESC 关闭（防止误操作），结果视图可以 ESC 退出
        return !rolling;
    }

    /**
     * 鼠标点击：滚动中任意点击发送停止请求到服务端。
     */
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (rolling) {
            // 发送 C2S 包请求停止轮盘
            ClientPlayNetworking.send(new BettorPayload.StopRoulette());
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
}
