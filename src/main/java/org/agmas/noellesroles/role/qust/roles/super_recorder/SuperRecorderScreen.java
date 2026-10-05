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
import org.agmas.noellesroles.client.widget.RecorderPlayerWidget;
import org.agmas.noellesroles.client.widget.RecorderRoleWidget;
import org.agmas.noellesroles.utils.RoleUtils;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * 超级记录员标记界面
 * <p>
 * 两阶段选择：
 * 1. 选择目标玩家（显示开局玩家列表，带皮肤）
 * 2. 选择角色（显示所有可用角色，不同职业颜色不同）
 * <p>
 * 完全参考 RecorderScreen，使用相同的 Widget 系统
 */
@Environment(EnvType.CLIENT)
public class SuperRecorderScreen extends Screen {

    // 需要排除的职业ID列表
    private static final List<ResourceLocation> IGNORED_ROLE_IDS = Arrays.asList(
    );

    private static final int ROLES_PER_PAGE = 12;
    private int phase = 0; // 0 = 选玩家, 1 = 选角色
    private UUID selectedPlayer = null;
    private String selectedPlayerName = "";

    private EditBox searchWidget = null;
    private SuperRecorderPlayerComponent comp = null;
    private List<SRERole> roles = new ArrayList<>();

    // Widget 列表
    private List<RecorderPlayerWidget> playerWidgets = new ArrayList<>();
    private List<RecorderRoleWidget> roleWidgets = new ArrayList<>();

    // 翻页相关
    private int currentRolePage = 0;
    private int totalPages = 0;
    private Button prevPageButton;
    private Button nextPageButton;

    /** 界面模式：0 = 记录员笔记（全部玩家），1 = 真相之书（仅未标记玩家，优先显示未标记玩家） */
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
        if (comp == null) {
            onClose();
            return;
        }
        // 清空旧的 widget
        playerWidgets.clear();
        roleWidgets.clear();

        if (phase == 0) {
            initPlayerSelection();
        } else {
            initRoleSelection();
        }
    }

    /**
     * 初始化玩家选择阶段
     */
    private void initPlayerSelection() {
        refreshPlayerSelection(null);
    }

    /**
     * 玩家选择：搜索玩家
     */
    private void onPlayerSearch(String text) {
        if (text == null || minecraft == null || minecraft.level == null || minecraft.player == null)
            return;

        // 重新初始化玩家选择以应用搜索过滤
        refreshPlayerSelection(text);
    }

    /**
     * 刷新玩家选择界面，可带搜索过滤
     */
    private void refreshPlayerSelection(String searchText) {
        if (minecraft == null || minecraft.level == null || minecraft.player == null)
            return;

        Map<UUID, String> startPlayers = comp.getStartPlayers();
        Set<UUID> markedSet = comp.getMarkedPlayers().keySet();

        List<UUID> playerUuids = new ArrayList<>();
        Map<UUID, String> playerNames = new HashMap<>();

        if (!startPlayers.isEmpty()) {
            for (var entry : startPlayers.entrySet()) {
                // 真相之书模式（mode=1）：只显示未标记玩家
                if (mode == 1 && markedSet.contains(entry.getKey())) continue;
                playerUuids.add(entry.getKey());
                playerNames.put(entry.getKey(), entry.getValue());
            }
        } else {
            // 回退到当前在线玩家
            for (AbstractClientPlayer p : minecraft.level.players()) {
                if (!p.getUUID().equals(minecraft.player.getUUID())) {
                    if (mode == 1 && markedSet.contains(p.getUUID())) continue;
                    playerUuids.add(p.getUUID());
                    playerNames.put(p.getUUID(), p.getName().getString());
                }
            }
        }

        if (playerUuids.isEmpty()) {
            onClose();
            return;
        }

        // 如果有搜索文本，则过滤玩家列表
        List<UUID> filteredPlayerUuids = new ArrayList<>();
        if (searchText != null && !searchText.trim().isEmpty()) {
            String lowerCaseSearch = searchText.toLowerCase();
            for (UUID uuid : playerUuids) {
                String playerName = playerNames.get(uuid);
                if (playerName.toLowerCase().contains(lowerCaseSearch)) {
                    filteredPlayerUuids.add(uuid);
                }
            }
        } else {
            filteredPlayerUuids.addAll(playerUuids);
        }

        // 清除现有的widgets
        for (RecorderPlayerWidget widget : playerWidgets) {
            this.removeWidget(widget);
        }
        playerWidgets.clear();
        boolean isSearchEmpty = false;
        if (filteredPlayerUuids.isEmpty()) {
            isSearchEmpty = true;
            // 如果没有匹配的玩家，但仍有原始玩家列表，则显示全部
            filteredPlayerUuids.addAll(playerUuids);
        }
        // 计算布局
        int columns = Math.min(filteredPlayerUuids.size(), 8);
        int rows = (int) Math.ceil(filteredPlayerUuids.size() / 8.0);
        int widgetSize = 32;
        int spacing = 8;
        int totalWidth = columns * (widgetSize + spacing) - spacing;
        int totalHeight = rows * (widgetSize + spacing) - spacing;
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2 + 20;

        // 创建搜索框
        if (searchWidget == null) {
            searchWidget = new EditBox(font, startX, startY - 40, totalWidth, 20,
                    Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setEditable(true);
            searchWidget.setResponder((text) -> {
                onPlayerSearch(text);
            });
        }
        if (isSearchEmpty) {
            // 如果没有匹配的玩家，则变红
            searchWidget.setTextColor(Color.RED.getRGB());
        } else {
            // 变回白色
            searchWidget.setTextColor(Color.WHITE.getRGB());
        }
        addRenderableWidget(searchWidget);

        // 创建过滤后的玩家widgets
        for (int i = 0; i < filteredPlayerUuids.size(); i++) {
            int col = i % 8;
            int row = i / 8;
            int x = startX + col * (widgetSize + spacing);
            int y = startY + row * (widgetSize + spacing);

            UUID uuid = filteredPlayerUuids.get(i);
            String name = playerNames.get(uuid);

            // 获取皮肤
            ResourceLocation skin = DefaultPlayerSkin.get(uuid).texture();
            if (minecraft.getConnection() != null) {
                PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
                if (info != null) {
                    skin = info.getSkin().texture();
                }
            }
            boolean hasGuessed = markedSet.contains(uuid);

            RecorderPlayerWidget widget = new RecorderPlayerWidget(
                    new RecorderScreenAdapter(), x, y, widgetSize, uuid, name, skin, i, hasGuessed);
            playerWidgets.add(widget);
            addRenderableWidget(widget);
        }
    }

    /**
     * 玩家被选中时调用
     */
    public void onPlayerSelected(UUID playerUuid, String playerName) {
        this.selectedPlayer = playerUuid;
        this.selectedPlayerName = playerName;
        this.phase = 1;
        this.currentRolePage = 0;
        this.searchWidget = null;
        clearWidgets();
        init();
    }

    /**
     * 初始化角色选择阶段
     */
    private void initRoleSelection() {
        searchWidget = null;
        clearWidgets();
        nextPageButton = null;
        prevPageButton = null;
        roleWidgets.clear();
        currentRolePage = 0;
        refreshRoleSelection(null);
    }

    private void onRoleSearch(String text) {
        if (text == null || minecraft == null || minecraft.level == null || minecraft.player == null)
            return;
        currentRolePage = 0;
        refreshRoleSelection(text);
    }

    /**
     * 刷新角色选择界面
     */
    private void refreshRoleSelection(String searchText) {

        for (int i = 0; i < roleWidgets.size(); i++) {
            this.removeWidget(roleWidgets.get(i));
        }
        roleWidgets.clear();

        if (nextPageButton != null)
            this.removeWidget(nextPageButton);
        if (prevPageButton != null)
            this.removeWidget(prevPageButton);

        if (minecraft == null || minecraft.player == null)
            return;

        // 从组件获取当前局有的身份
        var availableRoleIds = Noellesroles.getAllRolesSorted(false);

        roles.clear();
        roles.addAll(availableRoleIds);
        roles.removeIf(r -> r != null && r.identifier().equals(org.agmas.noellesroles.role.ModRoles.MERCENARY_ID));
        // 排除所有DNF职业
        roles.removeIf(r -> r != null && IGNORED_ROLE_IDS.contains(r.identifier()));
        // 排除所有修机模式职业
        roles.removeIf(r -> r != null && r instanceof net.exmo.sre.repair.role.RepairRole);

        // 如果列表为空（可能是单人测试或者数据未同步），回退到显示所有角色
        if (roles.isEmpty()) {
            roles = Noellesroles.getAllRolesSorted(false);
        }

        if (roles.isEmpty()) {
            onClose();
            return;
        }

        // 确保当前页码有效
        if (totalPages != 0) {
            if (currentRolePage >= totalPages) {
                currentRolePage = totalPages - 1;
            }
            if (currentRolePage < 0) {
                currentRolePage = 0;
            }
        }

        // 计算当前页的角色范围
        int startIndex = currentRolePage * ROLES_PER_PAGE;
        int endIndex = Math.min(startIndex + ROLES_PER_PAGE, roles.size());
        int rolesOnThisPage = endIndex - startIndex;

        // 计算布局 - 每页最多12个角色，4列3行
        int columns = Math.min(rolesOnThisPage, 4);
        int rows = (int) Math.ceil(rolesOnThisPage / 4.0);
        int widgetWidth = 90;
        int widgetHeight = 24;
        int spacingX = 10;
        int spacingY = 6;
        int totalWidth = columns * (widgetWidth + spacingX) - spacingX;
        int totalHeight = rows * (widgetHeight + spacingY) - spacingY;
        int startX = (width - totalWidth) / 2;
        int startY = (height - totalHeight) / 2 + 10;

        int totalRoles = 0;
        // 添加当前页的角色
        int k = 0;
        String lowerCasedSearchText = searchText;
        if (searchText != null) {
            lowerCasedSearchText = searchText.toLowerCase();
        }
        for (SRERole role : roles) {
            String roleName = RoleUtils.getRoleName(role).getString();
            String roleId = "";
            if (role != null) {
                roleId = role.identifier().toString();
            }
            if (roleName == null)
                continue;
            roleName = roleName.toLowerCase();
            if (searchText == null || searchText == "" || roleName.contains(lowerCasedSearchText)
                    || roleId.contains(lowerCasedSearchText) || PinYinUtils.contains(searchText, roleName)) {
                if (totalRoles >= startIndex && totalRoles < endIndex) {
                    int col = k % 4;
                    int row = k / 4;
                    int x = startX + col * (widgetWidth + spacingX);
                    int y = startY + row * (widgetHeight + spacingY);

                    RecorderRoleWidget widget = new RecorderRoleWidget(
                            new RecorderScreenAdapter(), x, y, widgetWidth, widgetHeight, role, k);
                    roleWidgets.add(widget);
                    addRenderableWidget(widget);
                    k++;
                }
                totalRoles++;
            }
        }

        // 添加翻页按钮
        int buttonWidth = 60;
        int buttonHeight = 20;
        int buttonY = startY + totalHeight + 20;
        totalPages = (int) Math.ceil(totalRoles / (double) ROLES_PER_PAGE);
        // 上一页按钮
        prevPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.prev_page"),
                button -> {
                    if (currentRolePage > 0) {
                        currentRolePage--;
                        refreshRoleSelection(searchText);
                    }
                }).bounds(width / 2 - buttonWidth - 30, buttonY, buttonWidth, buttonHeight).build();
        prevPageButton.active = currentRolePage > 0;
        addRenderableWidget(prevPageButton);

        // 下一页按钮
        nextPageButton = Button.builder(
                Component.translatable("screen.noellesroles.conspirator.next_page"),
                button -> {
                    if (currentRolePage < totalPages - 1) {
                        currentRolePage++;
                        refreshRoleSelection(searchText);
                    }
                }).bounds(width / 2 + 30, buttonY, buttonWidth, buttonHeight).build();
        nextPageButton.active = currentRolePage < totalPages - 1;
        addRenderableWidget(nextPageButton);
        if (searchWidget == null) {
            searchWidget = new EditBox(font, startX, startY - 40, totalWidth, 20,
                    Component.nullToEmpty(""));
            searchWidget.setHint(Component.translatable("screen.noellesroles.search.placeholder")
                    .withStyle(ChatFormatting.GRAY));
            searchWidget.setEditable(true);
            searchWidget.setResponder((text) -> {
                onRoleSearch(text);
            });
            addRenderableWidget(searchWidget);
        }
        if (totalRoles <= 0) {
            searchWidget.setTextColor(Color.RED.getRGB());
        } else {
            searchWidget.setTextColor(Color.WHITE.getRGB());
        }
    }

    /**
     * 角色被选中时调用
     */
    public void onRoleSelected(SRERole role) {
        if (selectedPlayer == null)
            return;
        if (minecraft == null || minecraft.player == null)
            return;

        // 超级记录员：发送 SuperRecorderPayload.MarkPlayer
        ClientPlayNetworking.send(new SuperRecorderPayload.MarkPlayer(
                selectedPlayer, role.identifier().toString()));

        // 关闭屏幕
        onClose();
    }

    @Override
    public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
        // 渲染背景
        renderBackground(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);

        // 渲染标题
        Component title;
        if (phase == 0) {
            title = Component.translatable("screen.super_recorder.select_player")
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        } else {
            title = Component.translatable("screen.super_recorder.select_role", selectedPlayerName)
                    .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);
        }

        context.drawCenteredString(font, title, width / 2, 30, 0xFFFFFF);

        // 渲染页码信息（仅在角色选择阶段）
        if (phase == 1 && roles.size() > ROLES_PER_PAGE) {
            Component pageInfo = Component.translatable("screen.noellesroles.conspirator.page_info",
                    currentRolePage + 1, totalPages)
                    .withStyle(ChatFormatting.YELLOW);
            context.drawCenteredString(font, pageInfo, width / 2, 45, 0xFFFFFF);
        }

        // 渲染标记进度信息
        if (comp != null) {
            Component markInfo = Component.translatable("screen.super_recorder.mark_progress",
                    comp.getMarkCount(), comp.getRequiredMarkCount())
                    .withStyle(ChatFormatting.AQUA);
            context.drawCenteredString(font, markInfo, width / 2, height - 40, 0xFFFFFF);
        }

        // 渲染提示
        Component hint = Component.translatable("screen.super_recorder.hint")
                .withStyle(ChatFormatting.GRAY);
        context.drawCenteredString(font, hint, width / 2, height - 30, 0x888888);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // ESC 键返回上一阶段或关闭
        if (keyCode == 256) { // ESC
            if (phase == 1) {
                // 返回玩家选择阶段
                phase = 0;
                selectedPlayer = null;
                selectedPlayerName = "";
                currentRolePage = 0;
                clearWidgets();
                init();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * RecorderScreen 适配器，用于 Widget 回调
     */
    private class RecorderScreenAdapter extends org.agmas.noellesroles.client.screen.RecorderScreen {
        public RecorderScreenAdapter() {
            super(null); // 不需要实际的 player
        }

        @Override
        public void onPlayerSelected(UUID playerUuid, String playerName) {
            SuperRecorderScreen.this.onPlayerSelected(playerUuid, playerName);
        }

        @Override
        public void onRoleSelected(SRERole role) {
            SuperRecorderScreen.this.onRoleSelected(role);
        }
    }
}
