package org.agmas.noellesroles.game.modes.echo.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;

/**
 * 回声托运站只有三格携带：0 阵营工具、1 卡牌终端、2 专长信物。
 * 只替换表现，槽位内容与选中值仍由服务器 enforceTool 决定。
 */
public final class EchoHotbar {
    private static final int SLOT = 20;
    private static final int GAP = 2;
    private EchoHotbar() { }

    /** 只有本模式的在局玩家才接管快捷栏。 */
    public static boolean active() {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.player.isSpectator()) return false;
        if (!(io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(client.level).getGameMode() instanceof EchoGameMode)) return false;
        var data = EchoBagClient.data();
        return data != null && data.matchId() != null && data.faction() != null;
    }

    /** 返回 true 表示已接管渲染，调用方应取消原版快捷栏。 */
    public static boolean render(GuiGraphics graphics) {
        if (!active()) return false;
        var client = Minecraft.getInstance();
        var player = client.player;
        if (player == null) return false;
        if (client.options.hideGui) return false;
        var inventory = player.getInventory();
        int width = client.getWindow().getGuiScaledWidth();
        int height = client.getWindow().getGuiScaledHeight();
        int total = SLOT * 3 + GAP * 2;
        int left = (width - total) / 2;
        int top = height - SLOT - 3;
        int selected = Math.max(0, Math.min(2, inventory.selected));
        graphics.fillGradient(left - 3, top - 3, left + total + 3, top + SLOT + 3, 0xC0181009, 0xD81A1008);
        graphics.renderOutline(left - 3, top - 3, total + 6, SLOT + 6, 0xFF8B6914);
        for (int slot = 0; slot < 3; slot++) {
            int x = left + slot * (SLOT + GAP);
            boolean chosen = slot == selected;
            graphics.fillGradient(x, top, x + SLOT, top + SLOT, chosen ? 0xE0342410 : 0xC020160C, chosen ? 0xE01C1207 : 0xB0140D06);
            graphics.renderOutline(x, top, SLOT, SLOT, chosen ? 0xFFFFF4DC : 0xFF6B5232);
            if (chosen) graphics.fill(x + 1, top + 1, x + SLOT - 1, top + 2, 0x66FFE8C0);
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, x + 2, top + 2);
                graphics.renderItemDecorations(client.font, stack, x + 2, top + 2);
            }
        }
        return true;
    }
}
