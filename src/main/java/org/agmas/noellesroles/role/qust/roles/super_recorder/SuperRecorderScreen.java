package org.agmas.noellesroles.role.qust.roles.super_recorder;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.util.PinYinUtils;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.utils.RoleUtils;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 超级记录员标记界面
 * <p>
 * 两阶段选择：
 * 1. 选择目标玩家（显示开局玩家列表）
 * 2. 选择角色（显示所有可用角色）
 */
@Environment(EnvType.CLIENT)
public class SuperRecorderScreen extends Screen {

    private static final int ROLES_PER_PAGE = 12;
    private int phase = 0; // 0 = 选玩家, 1 = 选角色
    private UUID selectedPlayer = null;
    private String selectedPlayerName = "";

    private EditBox searchWidget = null;
    private SuperRecorderPlayerComponent comp = null;
    private List<SRERole> roles = new ArrayList<>();
    private List<Button> dynamicButtons = new ArrayList<>();
    private int currentRolePage = 0;
    private int totalPages = 0;
    private Button prevPageButton;
    private Button nextPageButton;

    /** 界面模式：0 = 记录员笔记（全部玩家），1 = 真相之书（仅未标记玩家） */
    private final int mode;

    public SuperRecorderScreen(int mode) {
        super(Component.translatable("screen.super_recorder.mark_title"));
        this.mode = mode;
        if (net.minecraft.client.Minecraft.getInstance().player != null) {
            comp = QUSTComponentKeys.Keys.SUPER_RECORDER.maybeGet(
                    net.minecraft.client.Minecraft.getInstance().player).orElse(null);
        }
    }

    @Override
    protected void init() {
        super.init();
        if (comp == null) { onClose(); return; }
        dynamicButtons.clear();
        prevPageButton = null;
        nextPageButton = null;
        if (phase == 0) {
            initPlayerSelection();
        } else {
            initRoleSelection();
        }
    }

    // ==================== 阶段一：选择玩家 ====================

    private void initPlayerSelection() {
        clearDynamicButtons();
        if (comp == null || minecraft == null || minecraft.level == null || minecraft.player == null) return;

        Map<UUID, String> startPlayers = comp.getStartPlayers();
        Set<UUID> markedSet = comp.getMarkedPlayers().keySet();

        List<UUID> playerUuids = new ArrayList<>();
        Map<UUID, String> playerNames = new HashMap<>();

        if (!startPlayers.isEmpty()) {
            for (var entry : startPlayers.entrySet()) {
                if (mode == 1 && markedSet.contains(entry.getKey())) continue;
                playerUuids.add(entry.getKey());
                playerNames.put(entry.getKey(), entry.getValue());
            }
        } else {
            for (AbstractClientPlayer p : minecraft.level.players()) {
                if (p.getUUID().equals(minecraft.player.getUUID())) continue;
                if (mode == 1 && markedSet.contains(p.getUUID())) continue;
                playerUuids.add(p.getUUID());
                playerNames.put(p.getUUID(), p.getName().getString());
            }
        }

        if (playerUuids.isEmpty()) { onClose(); return; }

        int columns = Math.min(playerUuids.size(), 8);
        int rows = (int) Math.ceil(playerUuids.size() / 8.0);
        int widgetSize = 32;
        int spacing = 8;
        int totalWidth = columns * (widgetSize + spacing) - spacing;
        int startX = (width - totalWidth) / 2;
        int startY = (height - rows * (widgetSize + spacing)) / 2 + 20;

        // 搜索框
        searchWidget = new EditBox(font, startX, startY - 40, totalWidth, 20,
                Component.nullToEmpty(""));
        searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                .withStyle(ChatFormatting.GRAY));
        searchWidget.setEditable(true);
        addRenderableWidget(searchWidget);

        int i = 0;
        for (UUID uuid : playerUuids) {
            String name = playerNames.get(uuid);
            boolean marked = markedSet.contains(uuid);
            int col = i % 8, row = i / 8;
            int x = startX + col * (widgetSize + spacing);
            int y = startY + row * (widgetSize + spacing);

            String label = marked ? "✓" : name.substring(0, Math.min(2, name.length()));
            int bgColor = marked ? 0xFF444444 : 0xFF222222;
            Button btn = Button.builder(Component.literal(label), b -> onPlayerSelected(uuid, name))
                    .bounds(x, y, widgetSize, widgetSize).build();
            dynamicButtons.add(btn);
            addRenderableWidget(btn);
            i++;
        }
    }

    public void onPlayerSelected(UUID playerUuid, String playerName) {
        this.selectedPlayer = playerUuid;
        this.selectedPlayerName = playerName;
        this.phase = 1;
        this.currentRolePage = 0;
        clearDynamicButtons();
        init();
    }

    // ==================== 阶段二：选择角色 ====================

    private void initRoleSelection() {
        clearDynamicButtons();
        roles.clear();
        roles.addAll(Noellesroles.getAllRolesSorted(false));
        roles.removeIf(r -> r instanceof net.exmo.sre.repair.role.RepairRole);
        if (roles.isEmpty()) { onClose(); return; }
        refreshRoleSelection(null);
    }

    private void refreshRoleSelection(String searchText) {
        clearDynamicButtons();
        if (minecraft == null || minecraft.player == null) return;

        int widgetWidth = 90, widgetHeight = 24, spacingX = 10, spacingY = 6;
        int columns = Math.min(4, roles.size());
        int totalWidth = columns * (widgetWidth + spacingX) - spacingX;
        int startX = (width - totalWidth) / 2;
        int startY = (height - 100) / 2 + 10;

        String lower = searchText != null ? searchText.toLowerCase() : null;
        int totalRoles = 0, k = 0;

        for (SRERole role : roles) {
            String roleName = RoleUtils.getRoleName(role).getString();
            if (roleName == null) continue;
            String roleId = role.identifier().toString();
            if (lower != null && !roleName.toLowerCase().contains(lower)
                    && !roleId.contains(lower)
                    && !PinYinUtils.contains(lower, roleName.toLowerCase())) continue;

            if (totalRoles >= currentRolePage * ROLES_PER_PAGE
                    && totalRoles < (currentRolePage + 1) * ROLES_PER_PAGE) {
                int col = k % 4, row = k / 4;
                int x = startX + col * (widgetWidth + spacingX);
                int y = startY + row * (widgetHeight + spacingY);
                Button btn = Button.builder(RoleUtils.getRoleName(role), b -> onRoleSelected(role))
                        .bounds(x, y, widgetWidth, widgetHeight).build();
                dynamicButtons.add(btn);
                addRenderableWidget(btn);
                k++;
            }
            totalRoles++;
        }

        totalPages = (int) Math.ceil(totalRoles / (double) ROLES_PER_PAGE);

        int btnW = 60, btnH = 20, btnY = startY + 4 * (widgetHeight + spacingY) + 10;
        if (totalPages > 1) {
            prevPageButton = Button.builder(
                    Component.translatable("screen.noellesroles.conspirator.prev_page"),
                    b -> { if (currentRolePage > 0) { currentRolePage--; refreshRoleSelection(searchText); } })
                    .bounds(width / 2 - btnW - 30, btnY, btnW, btnH).build();
            prevPageButton.active = currentRolePage > 0;
            addRenderableWidget(prevPageButton);

            nextPageButton = Button.builder(
                    Component.translatable("screen.noellesroles.conspirator.next_page"),
                    b -> { if (currentRolePage < totalPages - 1) { currentRolePage++; refreshRoleSelection(searchText); } })
                    .bounds(width / 2 + 30, btnY, btnW, btnH).build();
            nextPageButton.active = currentRolePage < totalPages - 1;
        }

        if (searchWidget == null) {
            searchWidget = new EditBox(font, startX, startY - 40, totalWidth, 20,
                    Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setEditable(true);
            searchWidget.setResponder(text -> {
                currentRolePage = 0;
                refreshRoleSelection(text);
            });
            addRenderableWidget(searchWidget);
        }
    }

    public void onRoleSelected(SRERole role) {
        if (selectedPlayer == null || minecraft == null) return;
        ClientPlayNetworking.send(new SuperRecorderPayload.MarkPlayer(
                selectedPlayer, role.identifier().toString()));
        onClose();
    }

    // ==================== 渲染 ====================

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        Component title;
        if (phase == 0) {
            title = Component.translatable("screen.super_recorder.select_player")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        } else {
            title = Component.translatable("screen.super_recorder.select_role", selectedPlayerName)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }
        context.drawCenteredString(font, title, width / 2, 30, 0xFFFFFF);

        if (phase == 1 && roles.size() > ROLES_PER_PAGE) {
            Component pageInfo = Component.translatable("screen.noellesroles.conspirator.page_info",
                    currentRolePage + 1, totalPages).withStyle(ChatFormatting.YELLOW);
            context.drawCenteredString(font, pageInfo, width / 2, 45, 0xFFFFFF);
        }

        if (comp != null) {
            Component markInfo = Component.translatable("screen.super_recorder.mark_progress",
                    comp.getMarkCount(), comp.getRequiredMarkCount())
                    .withStyle(ChatFormatting.AQUA);
            context.drawCenteredString(font, markInfo, width / 2, height - 40, 0xFFFFFF);
        }

        Component hint = Component.translatable("screen.super_recorder.hint")
                .withStyle(ChatFormatting.GRAY);
        context.drawCenteredString(font, hint, width / 2, height - 20, 0x888888);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            if (phase == 1) {
                phase = 0;
                selectedPlayer = null;
                selectedPlayerName = "";
                currentRolePage = 0;
                clearDynamicButtons();
                init();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void clearDynamicButtons() {
        for (var btn : dynamicButtons) removeWidget(btn);
        dynamicButtons.clear();
        if (searchWidget != null) { removeWidget(searchWidget); searchWidget = null; }
        if (prevPageButton != null) { removeWidget(prevPageButton); prevPageButton = null; }
        if (nextPageButton != null) { removeWidget(nextPageButton); nextPageButton = null; }
    }
}
