package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.game.modes.echo.combat.EchoHuntLoop;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoRoleData;
import java.util.Locale;

/**
 * 追捕者的狩猎 HUD：锁定环 + 相位 + 扑击/本能提示。
 *
 * <p>位置在五边形技能图标左侧，避免与右侧居中的主动技能冲突；只在本人是追捕者、
 * 且处于可行动阶段时显示。所有数值来自 {@link EchoRoleData} 的本人投影，
 * 与服务器相位一致（20 Hz 刷新，由项目的 HUD 节流决定，不做额外插值）。
 *
 * <p>视觉语言：
 * <ul>
 * <li>环：外圈表示锁定值（0~100），颜色随锁定从暖灰→琥珀→赤红；</li>
 * <li>内点：蓄力中实心、后摇中空心、可起手时呼吸；</li>
 * <li>文字：相位名（潜伏/蓄力/突进/后摇），扑击就绪额外一行。</li>
 * </ul>
 */
public final class EchoHuntHud {
    private EchoHuntHud() { }

    /** 环半径与线宽（像素）。 */
    private static final int RADIUS = 11, THICK = 2;

    public static void initialize() {
        EchoCutterCooldownClient.initialize();
        org.agmas.noellesroles.client.event.CommonHudRenderCallback.EVENT.register((g, dt) -> render(g));
    }

    private static void render(FakeGuiGraphics g) {
        var client = Minecraft.getInstance();
        var data = EchoHud.ownData();
        if (data == null || data.faction() != EchoFaction.INTERCEPTOR) return;
        if (!data.life().standing() || !EchoHud.view().state().phase().acceptsActions()) return;

        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        int cx = width - 78, cy = height / 2 + 8;
        boolean paused = EchoHud.view().state().paused();
        long cutterRemaining=org.agmas.noellesroles.game.modes.echo.content.EchoCutterItem.cutterRemaining(client.player);

        int focus = Math.max(0, Math.min(100, data.huntFocus()));
        boolean instinct = data.huntInstinct();
        boolean empowered = data.huntEmpowered() || focus >= (int) EchoHuntLoop.FOCUS_MAX;
        EchoHuntLoop.Phase phase = phaseOf(data.huntPhase());

        // 底盘：让环在亮场景里也读得出来。
        g.fill(cx - RADIUS - 2, cy - RADIUS - 2, cx + RADIUS + 2, cy + RADIUS + 2, 0x66000000);
        ring(g, cx, cy, RADIUS, 0x88402E18, 0F, 1F);
        int ringColor = focus >= 100 ? 0xFFE05A4A : focus >= 50 ? 0xFFE0AD5B : 0xFFB39A6E;
        ring(g, cx, cy, RADIUS, ringColor, 0F, focus / 100F);

        // 相位内点：蓄力实心、后摇空心、潜伏呼吸。
        int dotColor = switch (phase) {
            case CHARGE -> 0xFFFFE8C0;
            case LUNGE -> 0xFFFF8A3D;
            case RECOVER -> 0xFF8C7A5E;
            case STALK -> EchoAnim.blend(0xFF6B5232, 0xFFD4AF37, paused ? 0F : EchoAnim.breathe(1400));
        };
        int dot = phase == EchoHuntLoop.Phase.RECOVER || cutterRemaining>0 ? 0 : 3;
        if (dot > 0) g.fill(cx - dot, cy - dot, cx + dot, cy + dot, dotColor);
        else ring(g, cx, cy, 3, dotColor, 0F, 1F);

        // 相位文字：在环下沿，用最短的可读词。
        String label = switch (phase) {
            case STALK -> "hunt.stalk";
            case CHARGE -> "hunt.charge";
            case LUNGE -> "hunt.lunge";
            case RECOVER -> "hunt.recover";
        };
        EchoHud.row(g, EchoHud.tr(label), cx - 35, cy + RADIUS + 2, 70, 0xFFD4AF37);

        // 状态合为一行，不能向上压住公共信标，也不能向下挤入快捷栏。
        if (instinct) {
            long remain = Math.max(0, data.huntInstinctUntil() - data.projectedTick());
            EchoHud.row(g, EchoHud.tr(empowered ? "hunt.instinct_empowered" : "hunt.instinct",
                    String.format(Locale.ROOT, "%.1f", remain / 20D)), cx - 35, cy - RADIUS - 13, 70, 0xFF72C17B);
        } else if (empowered) {
            EchoHud.row(g, EchoHud.tr("hunt.empowered"), cx - 35, cy - RADIUS - 13, 70,
                    EchoAnim.blend(0xFFE06B65, 0xFFFFF4DC, paused ? 0F : EchoAnim.breathe(700)));
        }
        // 职业分支仍明确显示给本人，短名不包含内部枚举。
        var hunterClass = data.hunterClass();
        if (hunterClass != null) {
            EchoHud.row(g, EchoHud.tr(hunterClass == org.agmas.noellesroles.game.modes.echo.hunter.EchoHunterClass.WEAVER
                    ? "hunt.weaver" : "hunt.rend"), cx - 35, cy + RADIUS + 12, 70, 0xFFFFF4DC);
        }
        if(cutterRemaining>0) {
            EchoHud.row(g,EchoHud.tr("hunter_balance.weapon_cooldown",String.format(Locale.ROOT,"%.1f",cutterRemaining/20D)),
                    cx-43,cy+RADIUS+22,86,0xFFE06B65);
        }
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