package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.state.EchoSkill;

import java.util.Locale;

/**
 * 右侧居中的五边形主动技能 HUD：图标 + 冷却 + 就绪呼吸。
 * 只用本人投影的冷却值，不预测、不参与任何权威判定。
 */
public final class EchoSkillHud {
    private EchoSkillHud() { }

    public static void initialize() {
        org.agmas.noellesroles.client.event.CommonHudRenderCallback.EVENT.register((g, dt) -> render(g));
    }

    private static void render(FakeGuiGraphics g) {
        var client = Minecraft.getInstance();
        if (client.player == null || client.level == null || client.options.hideGui) return;
        var data = EchoBagClient.data();
        if (data == null || data.matchId() == null || data.faction() == null) return;
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        int size = height < 240 ? 38 : 46;
        int radius = size / 2;
        boolean usable = data.life().standing();
        int cx = width - radius - 8, cy = height / 2;
        EchoSkill skill = data.skill();
        long remaining = data.skillRemaining();
        long total = Math.max(1, skill.cooldownTicks());
        boolean ready = remaining <= 0 && usable;
        float breathe = EchoAnim.breathe(1600);
        float alpha = usable ? 1F : 0.4F;
        int fillColor = EchoAnim.fade(0xD01A1008, alpha);
        int borderColor = EchoAnim.fade(ready ? EchoAnim.blend(0xFF8B6914, 0xFFFFE8C0, breathe) : 0xFF6B5232, alpha);
        fillPentagon(g, cx, cy, radius, fillColor);
        outlinePentagon(g, cx, cy, radius, borderColor);
        int iconSize = size >= 46 ? 24 : 20;
        int iconX = cx - iconSize / 2, iconY = cy - iconSize / 2;
        g.blit(ResourceLocation.parse(skill.icon()), iconX, iconY, iconSize, iconSize, 0F, 0F, 32, 32, 32, 32);
        if (remaining > 0) {
            float ratio = EchoAnim.clamp01(remaining / (float) total);
            g.fill(iconX, iconY, iconX + iconSize, iconY + Math.round(iconSize * ratio), 0x99101010);
            String label = remaining > 1200 ? String.format(Locale.ROOT, "%d", (remaining + 19) / 20) : String.format(Locale.ROOT, "%.1f", remaining / 20D);
            g.drawCenteredString(client.font, label, cx, cy - 4, EchoAnim.fade(0xFFFFF4DC, alpha));
        }
        if (!usable) g.drawCenteredString(client.font, "×", cx, cy - 4, 0xFFE06B65);
    }

    private static final double[] COS = new double[5];
    private static final double[] SIN = new double[5];
    static {
        for (int i = 0; i < 5; i++) {
            double angle = -Math.PI / 2 + i * Math.PI * 2 / 5;
            COS[i] = Math.cos(angle);
            SIN[i] = Math.sin(angle);
        }
    }

    private static void fillPentagon(FakeGuiGraphics g, int cx, int cy, int radius, int color) {
        double[] hits = new double[5];
        for (int y = cy - radius; y <= cy + radius; y++) {
            int n = 0;
            for (int i = 0; i < 5; i++) {
                double y1 = cy + SIN[i] * radius, y2 = cy + SIN[(i + 1) % 5] * radius;
                double x1 = cx + COS[i] * radius, x2 = cx + COS[(i + 1) % 5] * radius;
                if (y1 <= y && y2 > y || y2 <= y && y1 > y) hits[n++] = x1 + (y - y1) * (x2 - x1) / (y2 - y1);
            }
            if (n < 2) continue;
            int left = (int) Math.round(Math.min(hits[0], hits[1])), right = (int) Math.round(Math.max(hits[0], hits[1]));
            g.fill(left, y, right, y + 1, color);
        }
    }

    private static void outlinePentagon(FakeGuiGraphics g, int cx, int cy, int radius, int color) {
        for (int i = 0; i < 5; i++) {
            int x1 = (int) Math.round(cx + COS[i] * radius), y1 = (int) Math.round(cy + SIN[i] * radius);
            int x2 = (int) Math.round(cx + COS[(i + 1) % 5] * radius), y2 = (int) Math.round(cy + SIN[(i + 1) % 5] * radius);
            line(g, x1, y1, x2, y2, color);
        }
    }

    /** Bresenham 细线，避免依赖任何渲染管线状态。 */
    private static void line(FakeGuiGraphics g, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1), dy = Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1, sy = y1 < y2 ? 1 : -1, err = dx - dy;
        int x = x1, y = y1;
        while (true) {
            g.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) return;
            int e2 = err * 2;
            if (e2 > -dy) { err -= dy; x += sx; }
            if (e2 < dx) { err += dx; y += sy; }
        }
    }
}
