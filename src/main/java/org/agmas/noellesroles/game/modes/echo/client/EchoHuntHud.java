package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.game.modes.echo.combat.EchoHuntLoop;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import java.util.Locale;

/** 本人狩猎状态按需展开为两行，放在右侧常驻地图下方。 */
public final class EchoHuntHud {
    private EchoHuntHud() { }

    /** 环半径与线宽（像素）。 */
    private static final int THICK = 2;

    public static void initialize() {
        EchoCutterCooldownClient.initialize();
        org.agmas.noellesroles.client.event.CommonHudRenderCallback.EVENT.register((g, dt) -> render(g));
    }

    private static void render(FakeGuiGraphics g) {
        var client = Minecraft.getInstance();
        var data = EchoHud.ownData();
        if (data == null || data.faction() != EchoFaction.INTERCEPTOR || client.screen != null) return;
        if (!data.life().standing() || !EchoHud.view().state().phase().acceptsActions()) return;

        var rect = new EchoHudLayout(client.getWindow().getGuiScaledWidth(), client.getWindow().getGuiScaledHeight()).hunt();
        long cutterRemaining=org.agmas.noellesroles.game.modes.echo.content.EchoCutterItem.cutterRemaining(client.player);
        int focus = Math.max(0, Math.min(100, data.huntFocus()));
        boolean instinct = data.huntInstinct(), empowered = data.huntEmpowered() || focus >= EchoHuntLoop.FOCUS_MAX;
        EchoHuntLoop.Phase phase = phaseOf(data.huntPhase());
        if (focus == 0 && !instinct && !empowered && phase == EchoHuntLoop.Phase.STALK && cutterRemaining <= 0) return;
        int cx = rect.x() + 8, cy = rect.y() + 11;
        ring(g, cx, cy, 6, 0x88402E18, 0F, 1F);
        ring(g, cx, cy, 6, empowered ? 0xFFE06B65 : 0xFFD4AF37, 0F, focus / 100F);
        String label = switch (phase) {
            case CHARGE, WINDUP -> "hunt.charge";
            case LUNGE -> "hunt.lunge";
            case RECOVER -> "hunt.recover";
            case STALK -> "hunt.stalk";
        };
        var primary = cutterRemaining > 0 ? EchoHud.tr("hunter_balance.weapon_cooldown", String.format(Locale.ROOT, "%.1f", cutterRemaining / 20D)) : EchoHud.tr(label);
        EchoHud.row(g, primary, rect.x() + 19, rect.y() + 1, rect.width() - 19, 0xFFD4AF37);
        var secondary = instinct ? EchoFacilityMapClient.tr("hunt_instinct", focus) : EchoFacilityMapClient.tr("hunt_focus", focus);
        EchoHud.row(g, secondary, rect.x() + 19, rect.y() + 12, rect.width() - 19, 0xFFC8B898);
    }

    private static EchoHuntLoop.Phase phaseOf(String name) {
        if (name == null) return EchoHuntLoop.Phase.STALK;
        try { return EchoHuntLoop.Phase.valueOf(name); }
        catch (IllegalArgumentException invalid) { return EchoHuntLoop.Phase.STALK; }
    }

    /** 画一段圆弧（0~1 的比例）；用逐度填充，避免依赖任何额外的缓冲。 */
    private static void ring(FakeGuiGraphics g, int cx, int cy, int radius, int color, float from, float to) {
        from = Math.max(0F, Math.min(1F, from));
        to = Math.max(from, Math.min(1F, to));
        if (to <= from) return;
        int steps = Math.max(1, Math.round((to - from) * 96F));
        for (int i = 0; i <= steps; i++) {
            double angle = -Math.PI / 2 + (from + (to - from) * i / steps) * Math.PI * 2;
            int x = (int) Math.round(cx + Math.cos(angle) * radius);
            int y = (int) Math.round(cy + Math.sin(angle) * radius);
            g.fill(x - THICK / 2, y - THICK / 2, x + THICK / 2 + 1, y + THICK / 2 + 1, color);
        }
    }
}