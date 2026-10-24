package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.agmas.noellesroles.game.modes.echo.inventory.*;
import org.agmas.noellesroles.game.modes.echo.state.*;

import java.util.*;

/** LimitedInventoryScreen 内嵌页；没有独立背包、客户端可写库存或原版搬运操作。 */
public final class EchoInventoryPanel {
    private static final int GOLD = 0xFFD4AF37, TEXT = 0xFFFFF4DC, MUTED = 0xFF9E8B6E;
    private final LimitedInventoryScreen screen;
    private final Minecraft client = Minecraft.getInstance();
    private final List<UUID> selected = new ArrayList<>();
    private final int[] choiceScroll = new int[3];
    private EchoInventoryLayout layout;
    private UUID match, visibleOffer;
    private int tool = -1, detailScroll;
    private boolean rebuild, initialSelection = true;
    private String signature = "";

    public EchoInventoryPanel(LimitedInventoryScreen screen) { this.screen = screen; }
    private EchoRoleData data() { return EchoBagClient.data(); }
    private static Component tr(String key, Object... args) { return Component.translatable("echo.inventory." + key, args); }
    private boolean offered(EchoRoleData d) { return d.draft() != null && d.draft().state().equals("OFFERED"); }
    private boolean channeling(EchoRoleData d) { return d.draft() != null && d.draft().state().equals("CHANNELING"); }
    private boolean paused() { return client.level == null || io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(client.level).isTimeFrozen(); }
    private boolean writable(EchoRoleData d) {
        return d.phase().acceptsActions() && d.life().standing() && !d.controlled() && !paused()
                && !offered(d) && !channeling(d) && !EchoBagClient.waiting();
    }
    private EchoResourceInstance primary() {
        var d = data();
        return d == null || selected.isEmpty() ? null : d.items().stream().filter(i -> i.instanceId().equals(selected.getFirst())).findFirst().orElse(null);
    }
    private Optional<EchoCardDefinition> output(EchoRoleData d) {
        if (selected.size() != 2) return Optional.empty();
        var first = primary();
        var second = d.items().stream().filter(i -> i.instanceId().equals(selected.get(1))).findFirst().orElse(null);
        if (first == null || second == null) return Optional.empty();
        return EchoCardRegistry.craftOutput(first.instanceId(), first.definitionId(), second.instanceId(), second.definitionId(), d.faction());
    }

    /** 由原屏幕 init/reinit 调用，控件只归原屏幕持有。 */
    public void init() {
        layout = EchoInventoryLayout.of(screen.width, screen.height);
        var d = data();
        if (d == null || d.bag() == null) return;
        if (initialSelection) {
            int hotbar = screen.player.getInventory().selected;
            if (hotbar < 3) tool = hotbar;
            else if (hotbar < 9 && !d.bag().slots().get(hotbar - 3).items().isEmpty()) selected.add(d.bag().slots().get(hotbar - 3).items().getFirst().instanceId());
            initialSelection = false;
        }
        if (match == null) match = d.matchId();
        selected.removeIf(id -> d.items().stream().noneMatch(i -> i.instanceId().equals(id)));
        var nextOffer = offered(d) ? d.draft().offerId() : null;
        if (!Objects.equals(visibleOffer, nextOffer)) { Arrays.fill(choiceScroll, 0); visibleOffer = nextOffer; }
        int x = layout.x(), y = layout.y(), w = layout.width();
        int navW = (w - 26) / 3;
        boolean idle = !EchoBagClient.waiting() && !offered(d) && !channeling(d);
        button(Component.translatable(d.trade() == null ? "echo.trade.title" : "echo.trade.active"), x + 8, y + 26, navW, 18,
                () -> client.setScreen(new EchoTradeScreen(screen))).active = idle;
        var contract = button(Component.translatable("echo.contract.title"), x + 13 + navW, y + 26, navW, 18,
                () -> client.setScreen(new EchoContractScreen(screen)));
        contract.active = d.contract() != null && !channeling(d);
        var spec = button(Component.translatable("echo.spec.title"), x + 18 + 2 * navW, y + 26, navW, 18,
                () -> EchoSpecializationClient.open(client));
        spec.active = idle && EchoSpecializationClient.view() != null && !EchoSpecializationClient.view().locked();
        for (int n = 0; n < 9; n++) screen.addRoleWidget(new HotbarSlot(n));
        if (offered(d)) {
            for (int n = 0; n < Math.min(3, d.draft().candidates().size()); n++) {
                int choice = n;
                button(Component.translatable("echo.ui.choose", n + 1), layout.choiceX(n) + 4, layout.actionsY(), layout.choiceWidth() - 10, 18,
                        () -> EchoBagClient.send(EchoInventoryPayload.Operation.CHOOSE, d.draft().offerId(), null, choice)).active =
                        !EchoBagClient.waiting() && !paused() && d.life().potentiallyEscapable();
            }
        } else {
            var item = primary();
            boolean allowed = writable(d), unlocked = item != null && !d.bag().locks().containsKey(item.instanceId());
            boolean bound = item != null && d.draft() != null && d.draft().packId().equals(item.instanceId()) && d.draft().state().equals("BOUND");
            int actionW = (w - 25) / 4;
            var open = button(Component.translatable(channeling(d) ? "echo.ui.cancel_open" : "echo.ui.open"), x + 8, layout.actionsY(), actionW, 18, () -> {
                if (channeling(d)) EchoBagClient.send(EchoInventoryPayload.Operation.CANCEL_PACK, d.draft().offerId(), null, 0);
                else if (primary() != null) EchoBagClient.send(EchoInventoryPayload.Operation.BEGIN_PACK, primary().instanceId(), null, 0);
            });
            open.active = channeling(d) ? !EchoBagClient.waiting() : allowed && d.trade() == null && item != null && item.kind() == EchoResourceInstance.Kind.PACK && (unlocked || bound);
            var card = item == null || item.kind() == EchoResourceInstance.Kind.PACK ? null : EchoCardRegistry.require(item.definitionId());
            boolean self = card != null && (card.target() == EchoCardDefinition.Target.SELF || card.target() == EchoCardDefinition.Target.DIRECTION);
            var use = button(tr(self ? "use" : "hold"), x + 11 + actionW, layout.actionsY(), actionW, 18, () -> {
                if (self) EchoBagClient.send(EchoInventoryPayload.Operation.USE_CARD, item.instanceId(), null, 0);
                else { int slot = resourceSlot(item.instanceId(), d); if (slot >= 0) screen.player.getInventory().selected = slot + 3; screen.onClose(); }
            });
            use.active = allowed && d.trade() == null && unlocked && card != null && card.activation() != EchoCardDefinition.Activation.PASSIVE && card.usableBy(d.faction());
            use.setTooltipText(tr(self ? "use_help" : "hold_help"));
            button(Component.translatable("echo.ui.drop"), x + 14 + 2 * actionW, layout.actionsY(), actionW, 18, this::drop).active = allowed && unlocked;
            var craft = button(tr("craft"), x + 17 + 3 * actionW, layout.actionsY(), actionW, 18, this::craft);
            craft.active = allowed && output(d).isPresent() && selected.stream().noneMatch(d.bag().locks()::containsKey);
            if (!craft.active) craft.setTooltipText(tr("craft_help"));
        }
        signature = signature(d);
        rebuild = false;
    }
    private SreButton button(Component text, int x, int y, int width, int height, Runnable action) {
        var button = new SreButton(text, b -> action.run());
        button.setX(x); button.setY(y); button.setWidth(width); button.setHeight(height);
        button.refreshTooltip();
        return screen.addRoleWidget(button);
    }
    private int resourceSlot(UUID id, EchoRoleData d) {
        for (int n = 0; n < 6; n++) if (d.bag().slots().get(n).items().stream().anyMatch(i -> i.instanceId().equals(id))) return n;
        return -1;
    }
    private void select(int hotbar, boolean alternate) {
        var d = data();
        if (d == null || d.bag() == null || offered(d)) return;
        if (hotbar < 3) { tool = hotbar; selected.clear(); screen.player.getInventory().selected = hotbar; }
        else {
            var slot = d.bag().slots().get(hotbar - 3);
            if (slot.reservation() != null || EchoBagClient.waiting()) return;
            if (slot.items().isEmpty()) {
                if (selected.size() == 1 && writable(d)) EchoBagClient.send(EchoInventoryPayload.Operation.MOVE, selected.getFirst(), null, hotbar - 3);
                else { selected.clear(); tool = -1; }
            } else {
                var item = slot.items().get(alternate && slot.items().size() == 2 ? 1 : 0);
                if (!Screen.hasShiftDown()) selected.clear();
                if (!selected.remove(item.instanceId())) { if (selected.size() >= 2) selected.removeFirst(); selected.add(item.instanceId()); }
                tool = -1;
                screen.player.getInventory().selected = hotbar;
            }
        }
        detailScroll = 0; rebuild = true;
    }
    private void drop() {
        var item = primary(); if (item == null) return;
        // 关闭确认页不重复发送请求；只有明确确认才交给服务器。
        client.setScreen(new ConfirmScreen(yes -> {
            client.setScreen(screen);
            if (yes) EchoBagClient.send(EchoInventoryPayload.Operation.DROP, item.instanceId(), null, 0);
        }, Component.translatable("echo.ui.confirm_drop"), name(item)));
    }
    private void craft() {
        var d = data(); var result = d == null ? Optional.<EchoCardDefinition>empty() : output(d);
        if (result.isEmpty()) return;
        UUID a = selected.get(0), b = selected.get(1);
        client.setScreen(new ConfirmScreen(yes -> {
            client.setScreen(screen);
            if (yes) EchoBagClient.send(EchoInventoryPayload.Operation.CRAFT, a, b, 0);
        }, tr("craft"), Component.translatable("echo.ui.craft_preview", Component.translatable(result.get().nameKey()))));
    }
    private String signature(EchoRoleData d) {
        return d.bag().revision() + ":" + d.draft() + ":" + d.trade() + ":" + d.acknowledged() + ":" + EchoBagClient.waiting()
                + ":" + d.life() + ":" + d.controlled() + ":" + d.phase() + ":" + paused() + ":" + EchoSpecializationClient.view();
    }
    public void tick() {
        var d = data();
        if (!EchoHotbar.active() || d == null || match != null && !match.equals(d.matchId())) { screen.onClose(); return; }
        if (rebuild || d.bag() != null && !signature(d).equals(signature)) screen.reinit();
    }
    /** 数字键只选择快捷栏，不走原版 SWAP，从而不能绕过固定工具。 */
    public boolean keyPressed(int key, int scan) {
        for (int n = 0; n < 9; n++) if (client.options.keyHotbarSlots[n].matches(key, scan)) { select(n, false); return true; }
        return false;
    }
    public void close() { EchoBagClient.close(); }

    public void renderBackground(GuiGraphics g) {
        int x = layout.x(), y = layout.y(), w = layout.width(), h = layout.height();
        g.fillGradient(x, y, x + w, y + h, 0xF01A1008, 0xF00F1417);
        g.renderOutline(x, y, w, h, 0xFF8B6914);
        g.fill(x + 1, y + 1, x + w - 1, y + 2, 0x33FFE8C0);
        g.fill(x + 8, y + 21, x + w - 8, y + 22, 0x408B6914);
        var d = data();
        if (d == null || d.bag() == null) return;
        if (offered(d)) {
            for (int n = 0; n < 3; n++) {
                int cx = layout.choiceX(n), cw = layout.choiceWidth() - 2;
                g.fillGradient(cx, y + 49, cx + cw, layout.actionsY() - 4, 0xFF2B2013, 0xFF17110C);
                g.renderOutline(cx, y + 49, cw, layout.actionsY() - y - 53, 0xFF6B5232);
            }
        } else {
            g.fillGradient(x + 8, layout.contentY(), x + w - 8, layout.detailBottom(), 0xA0261D12, 0x90120D08);
            g.renderOutline(x + 8, layout.contentY(), w - 16, layout.detailBottom() - layout.contentY(), 0xFF5A4530);
        }
    }
    public void render(GuiGraphics g, int mouseX, int mouseY) {
        int x = layout.x(), y = layout.y(), w = layout.width();
        text(g, tr("title"), x + 8, y + 8, w / 2 - 14, 10, GOLD);
        var d = data();
        if (d == null || d.bag() == null) { text(g, Component.translatable("echo.ui.syncing"), x + 8, y + 52, w - 16, 20, MUTED); return; }
        text(g, tr("identity", Component.translatable("echo.faction." + d.faction().name().toLowerCase(Locale.ROOT)),
                Component.translatable("echo.phase." + d.phase().name().toLowerCase(Locale.ROOT))), x + w / 2, y + 8, w / 2 - 8, 10, TEXT);
        if (offered(d)) renderChoices(g, d);
        else {
            text(g, tr("cargo", d.cargo().size(), d.capacity(), d.blackCargoCount(), d.key() == null ? tr("no_key") : tr("has_key")), x + 8, y + 50, w - 16, 10, TEXT);
            renderDetail(g, d);
            Component hint = output(d).<Component>map(c -> Component.translatable("echo.ui.craft_preview", Component.translatable(c.nameKey())))
                    .orElse(tr(selected.size() == 2 ? "invalid_recipe" : "select_help"));
            if (d.draft() != null) hint = Component.translatable("echo.ui.draft_status", Component.translatable("echo.draft." + d.draft().state().toLowerCase(Locale.ROOT)),
                    Math.max(0, ((channeling(d) ? d.draft().deadline() : d.draft().readyAt()) - d.projectedTick() + 19) / 20));
            text(g, hint, x + 8, layout.hintY(), w - 16, 10, GOLD);
        }
        text(g, tr("tools"), layout.toolsX(), layout.labelY(), 3 * layout.slotStep() - 3, 10, GOLD);
        text(g, tr("resources"), layout.resourcesX(), layout.labelY(), 6 * layout.slotStep() - 3, 10, 0xFF5EB7D8);
        Component status = EchoBagClient.waiting() ? Component.translatable("echo.ui.waiting") : !d.requestError().isEmpty() ?
                Component.translatable("echo.error." + d.requestError().toLowerCase(Locale.ROOT)) : tr("close_help", client.options.keyInventory.getTranslatedKeyMessage());
        text(g, status, x + 8, y + layout.height() - 13, w - 16, 10, d.requestError().isEmpty() ? MUTED : 0xFFE06B65);
    }
    private void renderDetail(GuiGraphics g, EchoRoleData d) {
        int x = layout.x() + 16, y = layout.contentY() + 7, w = layout.width() - 32;
        var item = primary();
        if (item == null && tool < 0) { text(g, tr("empty_detail"), x, y, w, layout.detailBottom() - y - 4, MUTED); return; }
        Component title, body;
        if (tool >= 0) {
            var stack = screen.player.getInventory().getItem(tool);
            if (!stack.isEmpty()) g.renderItem(stack, x + 4, y + 5);
            title = toolName(tool);
            body = tr("tool_help." + tool);
        } else {
            drawResource(g, item, x, y, 28);
            title = name(item);
            body = detail(item);
            if (d.selectedCard() != null && d.selectedCard().equals(item.instanceId())) title = title.copy().append(" · ").append(tr("equipped"));
        }
        text(g, title, x + 36, y, w - 38, 20, GOLD);
        scrollText(g, body, x + 36, y + 22, w - 38, Math.max(10, layout.detailBottom() - y - 26), detailScroll, TEXT);
    }
    private void renderChoices(GuiGraphics g, EchoRoleData d) {
        for (int n = 0; n < Math.min(3, d.draft().candidates().size()); n++) {
            var card = EchoCardRegistry.require(d.draft().candidates().get(n));
            int x = layout.choiceX(n) + 4, y = layout.y(), w = layout.choiceWidth() - 10;
            icon(g, card, x, y + 53, 18);
            text(g, Component.translatable("echo.rarity." + card.rarity().name().toLowerCase(Locale.ROOT)), x + 23, y + 56, w - 23, 10, GOLD);
            text(g, Component.translatable(card.nameKey()), x, y + 76, w, 20, TEXT);
            var body = EchoCardText.description(card);
            long owned = d.items().stream().filter(i -> i.kind() != EchoResourceInstance.Kind.PACK).map(i -> EchoCardRegistry.require(i.definitionId()))
                    .filter(c -> c.recipeGroup().equals(card.recipeGroup())).count();
            body.append("\n").append(Component.translatable("echo.ui.series_owned", owned));
            if (!card.usableBy(d.faction())) body.append("\n").append(Component.translatable("echo.ui.wrong_faction"));
            scrollText(g, body, x, y + 99, w, Math.max(10, layout.actionsY() - y - 107), choiceScroll[n], TEXT);
        }
        text(g, Component.translatable("echo.ui.choice_timeout", Math.max(0, (d.draft().deadline() - d.projectedTick() + 19) / 20)),
                layout.x() + 8, layout.hintY(), layout.width() - 16, 10, GOLD);
    }
    private Component toolName(int slot) { return tr("tool." + slot); }
    private Component name(EchoResourceInstance item) {
        return item.kind() == EchoResourceInstance.Kind.PACK ? Component.translatable("echo.pack." + item.quality().name().toLowerCase(Locale.ROOT)) : Component.translatable(EchoCardRegistry.require(item.definitionId()).nameKey());
    }
    private Component detail(EchoResourceInstance item) {
        var d = data(); var text = name(item).copy();
        if (d.bag().locks().containsKey(item.instanceId())) text.append("\n").append(Component.translatable("echo.ui.locked"));
        if (item.kind() == EchoResourceInstance.Kind.PACK) return text.append("\n").append(Component.translatable("echo.ui.pack_help"));
        var card = EchoCardRegistry.require(item.definitionId());
        text.append("\n").append(EchoCardText.description(card));
        if (!card.usableBy(d.faction())) text.append("\n").append(Component.translatable("echo.ui.wrong_faction"));
        else if (card.activation() == EchoCardDefinition.Activation.PASSIVE) text.append("\n").append(Component.translatable("echo.ui.passive"));
        return text;
    }
    private void drawResource(GuiGraphics g, EchoResourceInstance item, int x, int y, int size) {
        if (item.kind() != EchoResourceInstance.Kind.PACK) icon(g, EchoCardRegistry.require(item.definitionId()), x, y, size);
        else {
            var stack = new ItemStack(switch (item.quality()) { case COMMON -> Items.BUNDLE; case RARE -> Items.ENDER_CHEST; case BLACK_MARKET -> Items.CHEST_MINECART; });
            g.pose().pushPose(); g.pose().translate(x, y, 0); g.pose().scale(size / 16F, size / 16F, 1); g.renderItem(stack, 0, 0); g.pose().popPose();
        }
    }
    private void icon(GuiGraphics g, EchoCardDefinition card, int x, int y, int size) {
        g.blit(ResourceLocation.parse(card.icon()), x, y, size, size, 0F, 0F, 64, 64, 64, 64);
    }
    private void text(GuiGraphics g, Component text, int x, int y, int width, int height, int color) {
        if (width <= 0 || height <= 0) return;
        g.enableScissor(x, y, x + width, y + height); int line = 0;
        for (var part : client.font.split(text, width)) { g.drawString(client.font, part, x, y + line++ * 10, color, false); }
        g.disableScissor();
    }
    private int maxScroll(Component body, int width, int height) { return Math.max(0, client.font.split(body, Math.max(1, width - 5)).size() * 10 - height); }
    private void scrollText(GuiGraphics g, Component body, int x, int y, int width, int height, int scroll, int color) {
        int max = maxScroll(body, width, height), offset = Math.min(scroll, max);
        g.enableScissor(x, y, x + width, y + height); int line = 0;
        for (var part : client.font.split(body, Math.max(1, width - 5))) g.drawString(client.font, part, x, y + line++ * 10 - offset, color, false);
        g.disableScissor();
        if (max > 0) {
            int thumb = Math.max(5, height * height / (height + max)), top = y + (height - thumb) * offset / max;
            g.fill(x + width - 2, y, x + width, y + height, 0xFF483722); g.fill(x + width - 2, top, x + width, top + thumb, GOLD);
        }
    }
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        var d = data(); if (d == null || d.bag() == null) return false;
        if (offered(d)) {
            for (int n = 0; n < 3; n++) if (mouseX >= layout.choiceX(n) && mouseX < layout.choiceX(n) + layout.choiceWidth()
                    && mouseY >= layout.y() + 49 && mouseY < layout.actionsY()) {
                choiceScroll[n] = Math.max(0, choiceScroll[n] - (int) (amount * 20)); return true;
            }
        } else if (mouseX >= layout.x() + 8 && mouseX < layout.x() + layout.width() - 8 && mouseY >= layout.contentY() && mouseY < layout.detailBottom()) {
            detailScroll = Math.max(0, detailScroll - (int) (amount * 20)); return true;
        }
        return false;
    }

    private final class HotbarSlot extends SreButton {
        private final int hotbar;
        HotbarSlot(int hotbar) {
            super(tr("slot", hotbar + 1), b -> select(hotbar, false));
            this.hotbar = hotbar;
            setX(layout.slotX(hotbar)); setY(layout.slotY()); setWidth(layout.slotSize()); setHeight(layout.slotSize());
            var d = data();
            if (hotbar < 3) setTooltipText(toolName(hotbar).copy().append("\n").append(tr("fixed_help")));
            else {
                var value = d.bag().slots().get(hotbar - 3);
                Component tip = value.reservation() != null ? Component.translatable("echo.ui.reserved") : value.items().isEmpty() ? tr("empty_slot") : detail(value.items().getFirst());
                if (value.items().size() == 2) tip = tip.copy().append("\n").append(tr("stack_help"));
                setTooltipText(tip);
            }
        }
        @Override public boolean mouseClicked(double mx, double my, int button) {
            if (button == 1 && isMouseOver(mx, my) && hotbar >= 3) { select(hotbar, true); playDownSound(client.getSoundManager()); return true; }
            return super.mouseClicked(mx, my, button);
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            var d = data(); if (d == null || d.bag() == null) return;
            int x = getX(), y = getY(), size = getWidth();
            var slot = hotbar >= 3 ? d.bag().slots().get(hotbar - 3) : null;
            boolean chosen = hotbar < 3 ? tool == hotbar : slot.items().stream().anyMatch(i -> selected.contains(i.instanceId()));
            int border = chosen ? GOLD : isHoveredOrFocused() ? TEXT : hotbar < 3 ? 0xFF8B6914 : 0xFF42616B;
            g.fillGradient(x, y, x + size, y + size, chosen ? 0xFF3A2A13 : 0xFF241A10, 0xFF100D09);
            g.renderOutline(x, y, size, size, border);
            int iconSize = Math.min(20, size - 8), ix = x + (size - iconSize) / 2, iy = y + (size - iconSize) / 2 + 2;
            if (hotbar < 3) {
                var stack = screen.player.getInventory().getItem(hotbar);
                if (!stack.isEmpty()) g.renderItem(stack, x + (size - 16) / 2, iy);
                else g.drawCenteredString(client.font, Component.literal("·"), x + size / 2, iy + 3, MUTED);
            } else if (slot.reservation() != null) g.drawCenteredString(client.font, Component.literal("…"), x + size / 2, iy + 3, GOLD);
            else if (!slot.items().isEmpty()) {
                drawResource(g, slot.items().getFirst(), ix, iy, iconSize);
                if (slot.items().size() > 1) g.drawString(client.font, "×" + slot.items().size(), x + size - 15, y + size - 9, TEXT, true);
                if (slot.items().stream().anyMatch(i -> d.bag().locks().containsKey(i.instanceId()))) g.fill(x + size - 5, y + 3, x + size - 2, y + 6, 0xFFE06B65);
            }
            g.drawString(client.font, String.valueOf(hotbar + 1), x + 2, y + 1, chosen ? GOLD : MUTED, false);
        }
    }
}
