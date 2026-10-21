package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.client.gui.SREPanelStyle;
import io.wifi.starrailexpress.client.gui.screen.EditorLayout;
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.editor.EchoEditorActionPayload;
import org.agmas.noellesroles.game.modes.echo.map.EchoMapDraft;

import java.util.List;
import java.util.Locale;

/**
 * 回响地图编辑工具的界面：会话 / 点位 / 体积 / 校验四个页签。
 *
 * <p>界面本身不读文件也不写世界，每个按钮只发一条 {@link EchoEditorActionPayload}；
 * 服务端回的快照是唯一事实来源，所以关掉界面再打开不会出现「本地以为改了」的错觉。
 * 遵循 {@code docs/ui_style.md} 的暗金面板与 SreButton。
 */
public final class EchoEditorScreen extends Screen {
    private static final String[] TAB_KEYS = {"session", "point", "volume", "check"};
    /** 删除点位时可选的字段（与 state 快照的 section 字段一致）。 */
    private static final List<String> FIELDS = List.of("carrierSpawns", "interceptorSpawns", "brokerSpawns",
            "beacons", "crystalNodes", "altars", "supplyCrates", "blackCrystalNodes", "blackDropoffs", "doors");

    private final Screen parent;
    private int x, y, w, h, tab;
    private long openedAt;
    private EditBox mapField, idField, thresholdField, nonceField;
    private String mapText = "", idText = "", thresholdText = "6", nonceText = "";
    private int kindIndex, volumeIndex, fieldIndex;
    /** 换页只登记，真正重建放到 tick：按钮回调里改控件列表会打乱 Screen 的点击遍历。 */
    private boolean rebuildPending;

    // ==================== 滚轮滚动（与地图搭建助手 MapBuildHelperScreen 同一套几何） ====================
    /** 滚轮一格滚动的像素、滚动条宽度。 */
    private static final int WHEEL_STEP = 26;
    private static final int SCROLL_W = SREPanelStyle.SCROLL_WIDTH;
    /** 列表滚动偏移 + 滚动条拖拽状态。 */
    private int scrollOffset;
    private boolean isDraggingScroll;
    private double dragStartY;
    private int dragStartScroll;

    public EchoEditorScreen(Screen parent) {
        super(Component.translatable("echo.editor.title"));
        this.parent = parent;
    }

    @Override protected void init() {
        w = Math.min(600, width - 16);
        h = Math.min(320, height - 16);
        x = (width - w) / 2;
        y = (height - h) / 2;
        if (openedAt == 0) {
            openedAt = System.currentTimeMillis();
            if (minecraft != null) minecraft.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK, 1F));
        }
        build();
    }

    /** 每次换页都重建控件；输入内容保存在本对象的字符串里，不会因为重建丢失。 */
    private void build() {
        clearWidgets();
        // 换页签回到列表顶部，滚动条拖拽也一并复位
        scrollOffset = 0;
        isDraggingScroll = false;
        int tabW = Math.max(48, (w - 16 - 12) / 4);
        for (int n = 0; n < TAB_KEYS.length; n++) {
            int index = n;
            var button = new SreButton(Component.translatable("echo.editor.tab." + TAB_KEYS[n]),
                    ignored -> { tab = index; rebuildPending = true; });
            place(button, x + 8 + n * (tabW + 4), y + 6, tabW, 16);
        }
        switch (tab) {
            case 0 -> buildSession();
            case 1 -> buildPoint();
            case 2 -> buildVolume();
            default -> buildCheck();
        }
        var back = new SreButton(Component.translatable("echo.ui.back"), ignored -> onClose());
        place(back, x + w - 72, y + h - 22, 64, 16);
    }

    private void buildSession() {
        mapField = field(mapText, x + 8, y + 30, w - 16, "echo.editor.hint.map_id");
        mapField.setResponder(text -> mapText = text);
        addRenderableWidget(mapField);
        int by = y + 54, bw = Math.max(64, (w - 16 - 9) / 4);
        action("echo.editor.button.new", by, 0, bw, () -> EchoEditorActionPayload.Op.NEW, () -> mapText);
        action("echo.editor.button.edit", by, 1, bw, () -> EchoEditorActionPayload.Op.EDIT, () -> mapText);
        button("echo.editor.button.cancel_session", x + 8 + 2 * (bw + 3), by, bw,
                () -> send(EchoEditorActionPayload.Op.CANCEL));
        button("echo.editor.button.refresh", x + 8 + 3 * (bw + 3), by, bw,
                () -> send(EchoEditorActionPayload.Op.LIST));
        int gy = y + 78, gw = Math.max(88, (w - 16 - 3) / 2);
        button("echo.editor.button.gate_on", x + 8, gy, gw, () -> send(EchoEditorActionPayload.Op.GATE_ON));
        button("echo.editor.button.gate_off", x + 11 + gw, gy, gw, () -> send(EchoEditorActionPayload.Op.GATE_OFF));
    }

    private void buildPoint() {
        int half = (w - 16 - 6) / 2;
        idField = field(idText, x + 8, y + 30, half - 40, "echo.editor.hint.id");
        idField.setResponder(text -> idText = text);
        addRenderableWidget(idField);
        // 人数档输入框：与 ID 框同行右侧，只对信标/晶簇/物资箱类点位生效。
        thresholdField = field(thresholdText, x + 12 + (half - 40), y + 30, 36, "echo.editor.hint.min");
        thresholdField.setResponder(text -> thresholdText = text);
        addRenderableWidget(thresholdField);
        button("echo.editor.button.mark", x + 14 + half, y + 30, half, () ->
                send(EchoEditorActionPayload.Op.MARK, EchoMapDraft.KINDS.get(kindIndex), idText, thresholdText));
        button("echo.editor.button.remove", x + 14 + half, y + 52, half, () ->
                send(EchoEditorActionPayload.Op.REMOVE, FIELDS.get(fieldIndex), idText));
    }

    private void buildVolume() {
        idField = field(idText, x + 8, y + 30, (w - 16 - 6) / 2, "echo.editor.hint.id");
        idField.setResponder(text -> idText = text);
        addRenderableWidget(idField);
        int bx = x + 14 + (w - 16 - 6) / 2, bw = Math.max(56, (w - 16 - 6) / 4);
        button("echo.editor.button.corner_a", bx, y + 30, bw, () -> send(EchoEditorActionPayload.Op.CORNER_A));
        button("echo.editor.button.corner_b", bx + bw + 3, y + 30, bw, () -> send(EchoEditorActionPayload.Op.CORNER_B));
        button("echo.editor.button.volume", bx + 2 * (bw + 3), y + 30, Math.max(64, bw + 24), () ->
                send(EchoEditorActionPayload.Op.VOLUME, EchoMapDraft.VOLUMES.get(volumeIndex), idText));
    }

    private void buildCheck() {
        nonceField = field(nonceText, x + 8, y + 30, w - 16, "echo.editor.hint.nonce");
        nonceField.setResponder(text -> nonceText = text);
        addRenderableWidget(nonceField);
        int by = y + 54, bw = Math.max(64, (w - 16 - 9) / 4);
        button("echo.editor.button.validate", x + 8, by, bw, () -> send(EchoEditorActionPayload.Op.VALIDATE));
        button("echo.editor.button.preview", x + 8 + bw + 3, by, bw, () -> send(EchoEditorActionPayload.Op.PREVIEW));
        button("echo.editor.button.save", x + 8 + 2 * (bw + 3), by, bw, () -> send(EchoEditorActionPayload.Op.SAVE));
        button("echo.editor.button.save_confirm", x + 8 + 3 * (bw + 3), by, bw,
                () -> send(EchoEditorActionPayload.Op.SAVE_CONFIRM, nonceText));
    }

    // ==================== 控件工具 ====================

    private void place(SreButton button, int bx, int by, int bw, int bh) {
        button.setX(bx); button.setY(by); button.setWidth(bw); button.setHeight(bh);
        addRenderableWidget(button);
    }

    private void button(String key, int bx, int by, int bw, Runnable action) {
        place(new SreButton(Component.translatable(key), ignored -> action.run()), bx, by, bw, 16);
    }

    private void action(String key, int by, int column, int bw,
                        java.util.function.Supplier<EchoEditorActionPayload.Op> op,
                        java.util.function.Supplier<String> arg) {
        button(key, x + 8 + column * (bw + 3), by, bw, () -> send(op.get(), arg.get()));
    }

    private void send(EchoEditorActionPayload.Op op) { send(op, "", "", ""); }

    private void send(EchoEditorActionPayload.Op op, String a) { send(op, a, "", ""); }

    private void send(EchoEditorActionPayload.Op op, String a, String b) { send(op, a, b, ""); }

    private void send(EchoEditorActionPayload.Op op, String a, String b, String c) {
        EchoEditorClient.send(op, a == null ? "" : a, b == null ? "" : b, c == null ? "" : c);
    }

    private EditBox field(String value, int fx, int fy, int fw, String hintKey) {
        EditBox box = new EditBox(font, fx, fy, Math.max(40, fw), 16, Component.empty());
        box.setMaxLength(64);
        box.setValue(value);
        box.setHint(io.wifi.starrailexpress.client.gui.SREPanelStyle.hint(Component.translatable(hintKey)));
        return box;
    }

    // ==================== 绘制 ====================

    @Override public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float p = EchoAnim.openProgress(openedAt, 180);
        g.fillGradient(0, 0, width, height, EchoAnim.fade(0xF018120A, p), EchoAnim.fade(0xF0061018, p));
        g.fill(x, y, x + w, y + h, EchoAnim.fade(0xF01A1008, p));
        g.renderOutline(x, y, w, h, EchoAnim.fade(0xFF8B6914, p));
        g.fill(x + 1, y + 1, x + w - 1, y + 3, EchoAnim.fade(0x33FFE8C0, p));
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        clampScroll();
        var state = EchoEditorClient.state();
        int y2 = y + 24;
        g.drawString(font, title, x + 8, y2, 0xFFD4AF37, false);
        // 标题行右侧：当前页签的一句话说明；超出面板宽度的部分裁掉，不遮挡其它内容。
        Component tip = Component.translatable("echo.editor.tip." + TAB_KEYS[tab]);
        int tipX = x + 8 + font.width(title) + 8;
        g.enableScissor(tipX, y + 22, Math.max(tipX + 1, x + w - 8), y + 34);
        g.drawString(font, tip, tipX, y + 24, 0xFF9E8B6E, false);
        g.disableScissor();
        if (state == null) {
            g.drawString(font, Component.translatable("echo.ui.waiting"), x + 8, y + 48, 0xFF9E8B6E, false);
            return;
        }
        // 顶栏状态：地图 / 草稿 / revision / 模式声明
        Component line = Component.translatable("echo.editor.status",
                state.mapName().isEmpty() ? Component.translatable("echo.editor.none") : Component.literal(state.mapName()),
                state.mapId().isEmpty() ? Component.translatable("echo.editor.none") : Component.literal(state.mapId()),
                state.revision(),
                state.declared() ? Component.translatable("echo.editor.hud.gate_on") : Component.translatable("echo.editor.hud.gate_off"));
        g.drawString(font, line, x + 8, y + h - 40, state.declared() ? 0xFF72C17B : 0xFFE0AD5B, false);
        Component message = EchoEditorClient.message();
        if (!message.getString().isEmpty()) g.drawString(font, message, x + 8, y + h - 30, 0xFFFFA080, false);
        // 点位/校验页的列表区可滚轮滚动：固定标签画在裁剪区外，列表整体裁剪 + 平移。
        switch (tab) {
            case 0 -> renderSession(g);
            case 1 -> {
                g.drawString(font, Component.translatable("echo.editor.label.fields"), fieldsX(), contentTop() - 10, 0xFF8B6914, false);
                g.enableScissor(x + 4, contentTop(), x + w - 6 - SCROLL_W, contentBottom());
                renderPoint(g, state, mouseX, mouseY);
                g.disableScissor();
            }
            case 2 -> renderVolume(g, state);
            default -> {
                g.enableScissor(x + 4, contentTop(), x + w - 6 - SCROLL_W, contentBottom());
                renderIssues(g, state);
                g.disableScissor();
            }
        }
        drawScrollbar(g, mouseX, mouseY);
    }

    /** 会话页空档：制图三步流程，让第一次用的管理员知道顺序。 */
    private void renderSession(GuiGraphics g) {
        int fy = y + 100;
        g.drawString(font, Component.translatable("echo.editor.tip.flow1"), x + 8, fy, 0xFF9E8B6E, false);
        g.drawString(font, Component.translatable("echo.editor.tip.flow2"), x + 8, fy + 10, 0xFF9E8B6E, false);
        g.drawString(font, Component.translatable("echo.editor.tip.flow3"), x + 8, fy + 20, 0xFF9E8B6E, false);
    }

    /** 校验/保存反馈的问题列表单独一块：只在校验页绘制，避免盖住其它页签的列表。 */
    private void renderIssues(GuiGraphics g, org.agmas.noellesroles.game.modes.echo.editor.EchoEditorStatePayload state) {
        int issueY = contentTop() - scrollOffset;
        for (var issue : state.issues()) {
            if (issueY > contentBottom()) break;
            g.drawString(font, Component.literal(issue), x + 8, issueY, 0xFFE06B65, false);
            issueY += 10;
        }
    }

    // ==================== 滚动几何与输入 ====================

    /** 可滚动内容顶端：点位/校验页的列表区。 */
    private int contentTop() { return y + 78; }
    /** 可滚动内容底端：给底部状态行、消息行留位。 */
    private int contentBottom() { return y + h - 48; }
    private int scrollbarX() { return x + w - 6 - SCROLL_W; }
    private int scrollbarTrackH() { return Math.max(1, contentBottom() - contentTop()); }

    /** 当前页签可滚动内容高度；0 表示整页放得下，不出现滚动条。 */
    private int contentHeight(org.agmas.noellesroles.game.modes.echo.editor.EchoEditorStatePayload state) {
        return switch (tab) {
            case 1 -> {
                int kinds = Math.min(10, EchoMapDraft.KINDS.size()) * 11;
                int right = FIELDS.size() * 11 + 4 + 10 + state.sections().size() * 10;
                yield Math.max(kinds, right);
            }
            case 3 -> state.issues().size() * 10;
            default -> 0;
        };
    }

    private int maxScroll() {
        var state = EchoEditorClient.state();
        if (state == null) return 0;
        return Math.max(0, contentHeight(state) - Math.max(1, contentBottom() - contentTop()));
    }

    private void clampScroll() { scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll())); }

    private void drawScrollbar(GuiGraphics g, int mouseX, int mouseY) {
        int max = maxScroll();
        if (max <= 0) return;
        int trackY = contentTop(), trackH = scrollbarTrackH();
        int thumbH = EditorLayout.thumbHeight(trackH, max);
        int thumbY = EditorLayout.thumbY(trackY, trackH, thumbH, scrollOffset, max);
        boolean hover = isDraggingScroll
                || (mouseX >= scrollbarX() - 1 && mouseX < scrollbarX() + SCROLL_W + 1 && mouseY >= thumbY && mouseY < thumbY + thumbH);
        SREPanelStyle.drawScrollbar(g, scrollbarX(), trackY, trackH, thumbY, thumbH, hover);
    }

    @Override public boolean mouseScrolled(double mx, double my, double horiz, double vert) {
        // 只在面板内容区内滚动（与地图搭建助手一致）：鼠标在面板外时不抢滚轮
        if (maxScroll() > 0 && mx >= x + 4 && mx < x + w - 8 && my >= contentTop() && my < contentBottom()) {
            // Math.round(float) 返回 int；用 double 重载会得到 long，赋值给 int 编译不过
            scrollOffset = Math.max(0, Math.min(scrollOffset + Math.round((float) (-vert * WHEEL_STEP)), maxScroll()));
            return true;
        }
        return super.mouseScrolled(mx, my, horiz, vert);
    }

    /** 点位页几何：kind 两列收在左半区，字段/计数在右半区；渲染与点击共用同一套算式。 */
    private int kindColumnWidth() { return Math.max(56, ((w - 16) / 2 - 12) / 2); }
    private int fieldsX() { return x + 8 + (w - 16) / 2 + 8; }

    private void renderPoint(GuiGraphics g, org.agmas.noellesroles.game.modes.echo.editor.EchoEditorStatePayload state,
                             int mouseX, int mouseY) {
        // 「字段」标签由 render() 固定在裁剪区上沿；这里整块列表按 scrollOffset 平移。
        int startY = contentTop() - scrollOffset, columnWidth = kindColumnWidth();
        for (int n = 0; n < EchoMapDraft.KINDS.size(); n++) {
            int column = n / 10, row = n % 10;
            int rx = x + 8 + column * (columnWidth + 4);
            int ry = startY + row * 11;
            boolean active = n == kindIndex;
            boolean hover = mouseX >= rx && mouseX < rx + columnWidth && mouseY >= ry && mouseY < ry + 10;
            g.drawString(font, Component.literal(EchoMapDraft.KINDS.get(n)), rx, ry,
                    active ? 0xFFD4AF37 : hover ? 0xFFFFF4DC : 0xFF9E8B6E, false);
        }
        int fx = fieldsX();
        for (int n = 0; n < FIELDS.size(); n++) {
            int ry = startY + n * 11;
            boolean active = n == fieldIndex;
            g.drawString(font, Component.literal(FIELDS.get(n)), fx, ry,
                    active ? 0xFFD4AF37 : 0xFF9E8B6E, false);
        }
        int listY = startY + FIELDS.size() * 11 + 4;
        g.drawString(font, Component.translatable("echo.editor.label.counts"), fx, listY, 0xFF8B6914, false);
        int ly = listY + 10;
        for (var section : state.sections()) {
            if (ly > contentBottom()) break;
            g.drawString(font, Component.literal(String.format(Locale.ROOT, "%s %d %s", section.field(), section.count(),
                    String.join(",", section.ids()))), fx, ly, 0xFFC8B898, false);
            ly += 10;
        }
    }

    private void renderVolume(GuiGraphics g, org.agmas.noellesroles.game.modes.echo.editor.EchoEditorStatePayload state) {
        int startY = y + 60;
        for (int n = 0; n < EchoMapDraft.VOLUMES.size(); n++) {
            int ry = startY + n * 12;
            g.drawString(font, Component.literal(EchoMapDraft.VOLUMES.get(n)), x + 8, ry,
                    n == volumeIndex ? 0xFFD4AF37 : 0xFF9E8B6E, false);
        }
        g.drawString(font, Component.translatable("echo.editor.label.corners",
                state.cornerA() ? "A" : "-", state.cornerB() ? "B" : "-"), x + w / 2, startY, 0xFFC8B898, false);
        g.drawString(font, Component.translatable("echo.editor.hint.volume"), x + w / 2, startY + 14, 0xFF9E8B6E, false);
    }

    // ==================== 交互 ====================

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 滚动条：点 thumb 开始拖拽；点轨道直接把 thumb 中心挪到鼠标处
        if (button == 0 && maxScroll() > 0 && mouseX >= scrollbarX() - 1 && mouseX < scrollbarX() + SCROLL_W + 1
                && mouseY >= contentTop() && mouseY < contentBottom()) {
            isDraggingScroll = true;
            dragStartY = mouseY;
            dragStartScroll = scrollOffset;
            int thumbH = EditorLayout.thumbHeight(scrollbarTrackH(), maxScroll());
            double track = Math.max(1, scrollbarTrackH() - thumbH);
            scrollOffset = Math.max(0, Math.min(
                    (int) ((mouseY - contentTop() - thumbH / 2.0D) / track * maxScroll()), maxScroll()));
            return true;
        }
        if (tab == 1) {
            int startY = contentTop() - scrollOffset, columnWidth = kindColumnWidth();
            for (int n = 0; n < EchoMapDraft.KINDS.size(); n++) {
                int rx = x + 8 + (n / 10) * (columnWidth + 4), ry = startY + (n % 10) * 11;
                if (mouseX >= rx && mouseX < rx + columnWidth && mouseY >= ry && mouseY < ry + 10) {
                    kindIndex = n; return true;
                }
            }
            int fx = fieldsX();
            for (int n = 0; n < FIELDS.size(); n++) {
                int ry = startY + n * 11;
                if (mouseX >= fx && mouseX < x + w - 8 && mouseY >= ry && mouseY < ry + 10) {
                    fieldIndex = n; return true;
                }
            }
        } else if (tab == 2) {
            int startY = y + 60;
            for (int n = 0; n < EchoMapDraft.VOLUMES.size(); n++) {
                int ry = startY + n * 12;
                if (mouseX >= x + 8 && mouseX < x + w / 2 && mouseY >= ry && mouseY < ry + 11) {
                    volumeIndex = n; return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public boolean mouseReleased(double mouseX, double mouseY, int button) {
        isDraggingScroll = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (isDraggingScroll && maxScroll() > 0) {
            // 用真实 thumb 高度算比例：滑块才会跟着鼠标走
            double track = Math.max(1, scrollbarTrackH() - EditorLayout.thumbHeight(scrollbarTrackH(), maxScroll()));
            scrollOffset = Math.max(0, Math.min(
                    dragStartScroll + (int) ((mouseY - dragStartY) / track * maxScroll()), maxScroll()));
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override public void tick() {
        super.tick();
        if (rebuildPending) {
            rebuildPending = false;
            build();
        }
        if (EchoEditorClient.state() != null) return;
        // 没有快照时先拉一次，避免刚打开就是空界面。
        EchoEditorClient.send(EchoEditorActionPayload.Op.LIST, "", "", "");
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override public void onClose() { minecraft.setScreen(parent); }
}
