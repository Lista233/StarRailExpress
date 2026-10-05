/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.client.screen;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.TMMRoles;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GameNarrator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.PageButton;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderPlayerComponent;
import org.agmas.noellesroles.utils.RoleUtils;

import java.util.*;

/**
 * 真相之书界面 — 复古书页风格，复用推理之书的布局。
 *
 * <p>
 * 每页显示一名玩家的 ID 与职业名。
 * <strong>优先显示未被超级记录员标记的玩家的真实职业</strong>，然后显示已标记的玩家。
 * 左右方向键或翻页按钮切换。
 */
public class TruthBookScreen extends Screen {

    // ---------- 纹理与布局常量 ----------
    private static final ResourceLocation BOOK_TEXTURE = ResourceLocation
            .parse("noellesroles:textures/gui/deduction_book.png");
    private static final int ORIG_IMG_WIDTH = 300;
    private static final int ORIG_IMG_HEIGHT = 200;

    // 内容区域在原始纹理中的相对坐标
    private static final int CONTENT_REL_X = 60;
    private static final int CONTENT_REL_Y = 70;
    private static final int CONTENT_REL_WIDTH = 160;
    private static final int CONTENT_REL_HEIGHT = 100;

    // 标题和副标题的相对 Y 坐标
    private static final int TITLE_REL_Y = 36;
    private static final int SUBTITLE_REL_Y = 55;

    // 翻页按钮在原始纹理中的相对坐标
    private static final int PAGE_BTN_REL_X = 248;
    private static final int PAGE_BTN_REL_Y = 174;

    // 屏幕边距
    private static final int SCREEN_MARGIN = 20;

    // ---------- 页面状态 ----------
    private int page = 0;

    // ---------- 布局计算值 ----------
    private int bookX, bookY, bookWidth, bookHeight;
    private int contentX, contentY, contentWidth, contentHeight;
    private int titleY, subtitleY;
    private float scale;

    // ---------- 翻页按钮 ----------
    private PageButton backButton;
    private PageButton forwardButton;

    // ---------- 缓存的标记列表 ----------
    private List<Map.Entry<UUID, String>> markedEntries = new ArrayList<>();

    public TruthBookScreen() {
        super(GameNarrator.NO_TITLE);
    }

    private SuperRecorderPlayerComponent component() {
        if (minecraft == null || minecraft.player == null) return null;
        return QUSTComponentKeys.Keys.SUPER_RECORDER.maybeGet(minecraft.player).orElse(null);
    }

    // ==================== 初始化 ====================

    @Override
    protected void init() {
        super.init();
        refreshMarkedEntries();
        calculateBookSize();
        createButtons();
        clampPage();
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        super.resize(minecraft, width, height);
        calculateBookSize();
        this.clearWidgets();
        createButtons();
        clampPage();
    }

    private void refreshMarkedEntries() {
        markedEntries.clear();
        SuperRecorderPlayerComponent comp = component();
        if (comp == null) return;

        // 获取所有标记的玩家
        Map<UUID, String> marked = comp.getMarkedPlayers();
        Map<UUID, String> startPlayers = comp.getStartPlayers();

        // 优先显示未被标记的玩家（显示他们的真实职业）
        List<Map.Entry<UUID, String>> unmarkedEntries = new ArrayList<>();
        List<Map.Entry<UUID, String>> markedPlayerEntries = new ArrayList<>();

        // 遍历所有开局玩家，找出未标记的
        for (Map.Entry<UUID, String> entry : startPlayers.entrySet()) {
            UUID uuid = entry.getKey();
            if (!marked.containsKey(uuid)) {
                // 未标记的玩家 - 获取其真实职业
                if (minecraft != null && minecraft.level != null) {
                    var player = minecraft.level.getPlayerByUUID(uuid);
                    if (player != null) {
                        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(minecraft.level);
                        var role = gameWorld.getRole(player);
                        if (role != null) {
                            // 添加到未标记列表（使用真实职业ID）
                            unmarkedEntries.add(new AbstractMap.SimpleEntry<>(uuid, role.identifier().toString()));
                        }
                    }
                }
            }
        }

        // 添加已标记的玩家
        markedPlayerEntries.addAll(marked.entrySet());

        // 优先显示未标记的玩家
        markedEntries.addAll(unmarkedEntries);
        markedEntries.addAll(markedPlayerEntries);
    }

    private void calculateBookSize() {
        int availW = width - 2 * SCREEN_MARGIN;
        int availH = height - 2 * SCREEN_MARGIN;
        double ratio = (double) ORIG_IMG_WIDTH / ORIG_IMG_HEIGHT;
        int w, h;
        if (availW / (double) availH > ratio) {
            h = availH;
            w = (int) (h * ratio);
        } else {
            w = availW;
            h = (int) (w / ratio);
        }
        bookWidth = w;
        bookHeight = h;
        bookX = (width - w + 15) / 2;
        bookY = (height - h) / 2;

        scale = (float) bookWidth / ORIG_IMG_WIDTH;

        contentX = (int) (bookX + CONTENT_REL_X * scale);
        contentY = (int) (bookY + CONTENT_REL_Y * scale);
        contentWidth = (int) (CONTENT_REL_WIDTH * scale);
        contentHeight = (int) (CONTENT_REL_HEIGHT * scale);

        titleY = (int) (bookY + TITLE_REL_Y * scale);
        subtitleY = (int) (bookY + SUBTITLE_REL_Y * scale);
    }

    private void createButtons() {
        int btnX = (int) (bookX + PAGE_BTN_REL_X * scale);
        int btnY = (int) (bookY + PAGE_BTN_REL_Y * scale);
        int leftX = btnX - 28;
        int rightX = btnX;

        this.backButton = this.addRenderableWidget(
                new PageButton(leftX, btnY, false, b -> pageBack(), true));
        this.forwardButton = this.addRenderableWidget(
                new PageButton(rightX, btnY, true, b -> pageForward(), true));

        updateButtonVisibility();
    }

    private void updateButtonVisibility() {
        if (backButton != null) backButton.visible = page > 0;
        if (forwardButton != null) forwardButton.visible = page < markedEntries.size() - 1;
    }

    private void clampPage() {
        if (markedEntries.isEmpty()) {
            page = 0;
        } else {
            if (page < 0) page = 0;
            if (page >= markedEntries.size()) page = markedEntries.size() - 1;
        }
    }

    // ==================== 翻页 ====================

    private void pageForward() {
        if (page < markedEntries.size() - 1) {
            page++;
            updateButtonVisibility();
        }
    }

    private void pageBack() {
        if (page > 0) {
            page--;
            updateButtonVisibility();
        }
    }

    // ==================== 渲染背景 ====================

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.blit(BOOK_TEXTURE,
                bookX, bookY, bookWidth, bookHeight,
                0, 0, ORIG_IMG_WIDTH, ORIG_IMG_HEIGHT,
                ORIG_IMG_WIDTH, ORIG_IMG_HEIGHT);
    }

    // ==================== 暂停屏幕 ====================

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ==================== 渲染内容 ====================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);

        // ---- 标题 ----
        Component titleComp = Component.translatable("screen.super_recorder.truth_book_title");
        drawScaledCentered(g, titleComp, titleY, 1.3f, 0xFFD4A344);

        if (markedEntries.isEmpty()) {
            Component emptyText = Component.translatable("screen.super_recorder.truth_book_empty")
                    .withStyle(ChatFormatting.GRAY);
            drawScaledCentered(g, emptyText, subtitleY + (int) (16 * scale), 1.0f, 0xFF888888);
            return;
        }

        clampPage();
        Map.Entry<UUID, String> entry = markedEntries.get(page);
        UUID targetUuid = entry.getKey();
        String roleIdStr = entry.getValue();

        // 判断是否为已标记玩家
        SuperRecorderPlayerComponent comp = component();
        boolean isMarked = comp != null && comp.getMarkedPlayers().containsKey(targetUuid);

        // ---- 页码标签 ----
        Component pageLabel = Component.translatable(
                "screen.super_recorder.truth_book_page", page + 1, markedEntries.size());
        drawScaledCentered(g, pageLabel, subtitleY, 1.0f, 0xFFEEDDAA);

        // ---- 玩家名 + 职业名 ----
        int lineY = contentY;
        int lineHeight = (int) (12 * scale);

        // 获取玩家名（从组件的 startPlayers 中取）
        String playerName = null;
        if (comp != null) {
            playerName = comp.getStartPlayers().get(targetUuid);
        }
        if (playerName == null) {
            // 回退：尝试从在线玩家中获取
            if (minecraft != null && minecraft.level != null) {
                var onlinePlayer = minecraft.level.getPlayerByUUID(targetUuid);
                if (onlinePlayer != null) {
                    playerName = onlinePlayer.getName().getString();
                }
            }
        }
        if (playerName == null) {
            playerName = targetUuid.toString().substring(0, 8);
        }

        // 获取职业名
        Component roleNameComp = null;
        ResourceLocation roleId = ResourceLocation.tryParse(roleIdStr);
        if (roleId != null) {
            SRERole role = TMMRoles.ROLES.get(roleId);
            if (role != null) {
                roleNameComp = RoleUtils.getRoleName(role);
            }
        }
        if (roleNameComp == null) {
            roleNameComp = Component.literal(roleIdStr);
        }

        // 绘制玩家 ID
        Component playerLine = Component.literal("❯ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("screen.super_recorder.truth_book_player",
                        Component.literal(playerName).withStyle(ChatFormatting.WHITE)));
        drawContentLine(g, playerLine, lineY, 0xFF99AABB);
        lineY += lineHeight;

        // 绘制职业名（带角色颜色）
        int roleColor = 0xFFCCBB99;
        if (roleId != null) {
            SRERole role = TMMRoles.ROLES.get(roleId);
            if (role != null) {
                roleColor = role.color() | 0xFF000000; // 确保 alpha 为 255
            }
        }
        Component roleLine = Component.literal("❯ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(Component.translatable("screen.super_recorder.truth_book_role",
                        roleNameComp.copy().withStyle(ChatFormatting.WHITE)));
        drawContentLine(g, roleLine, lineY, roleColor);
        lineY += lineHeight;

        // 绘制状态标签（未标记 / 已标记）
        lineY += (int) (6 * scale);
        Component statusLine;
        int statusColor;
        if (isMarked) {
            statusLine = Component.literal("❯ ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable("screen.super_recorder.truth_book_marked")
                            .withStyle(ChatFormatting.GREEN));
            statusColor = 0xFF88FF88;
        } else {
            statusLine = Component.literal("❯ ").withStyle(ChatFormatting.DARK_GRAY)
                    .append(Component.translatable("screen.super_recorder.truth_book_unmarked")
                            .withStyle(ChatFormatting.YELLOW));
            statusColor = 0xFFFFFF88;
        }
        drawContentLine(g, statusLine, lineY, statusColor);
        lineY += lineHeight;
    }

    // ==================== 辅助绘制方法 ====================

    private void drawContentLine(GuiGraphics g, Component text, int screenY, int color) {
        if (text.getString().isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(contentX, screenY, 0);
        g.pose().scale(scale, scale, 1.0f);
        g.drawString(font, text, 0, 0, color, false);
        g.pose().popPose();
    }

    private void drawScaledCentered(GuiGraphics g, Component text, int screenY, float relScale, int color) {
        String raw = text.getString();
        if (raw.isEmpty()) return;
        g.pose().pushPose();
        g.pose().translate(bookX + bookWidth / 2.0f, screenY, 0);
        g.pose().scale(scale * relScale, scale * relScale, 1.0f);
        g.drawCenteredString(font, text, 0, 0, color);
        g.pose().popPose();
    }

    // ==================== 键盘交互 ====================

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) return true;
        if (keyCode == 263 || keyCode == 266) { // 左方向键 或 Page Up
            pageBack();
            return true;
        }
        if (keyCode == 262 || keyCode == 267) { // 右方向键 或 Page Down
            pageForward();
            return true;
        }
        return false;
    }
}
