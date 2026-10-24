package org.agmas.noellesroles.game.modes.echo.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry;
import org.agmas.noellesroles.game.modes.echo.objective.EchoPublicPayload;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;

/** 公开目标与本人状态分区；旁观者不读取其他人的私有RoleData。 */
public final class EchoHud {
    private static final int TEXT = 0xFFFFF4DC, GOLD = 0xFFD4AF37, MUTED = 0xFF9E8B6E;
    private static final int GREEN = 0xFF72C17B, RED = 0xFFE06B65, BLUE = 0xFF5EB7D8;
    private static EchoPublicPayload view;
    private static int age;
    private EchoHud() { }
    static EchoPublicPayload view() { return view; }
    static int age() { return age; }
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoPublicPayload.ID,(p,c) -> c.client().execute(() -> { view=p; age=0; }));
        EchoObjectiveClient.initialize();
        EchoMachineClient.initialize();
        EchoRollClient.initialize();
        EchoScreenFx.initialize();
        EchoSkillHud.initialize();
        EchoAtmosphereClient.initialize();
        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntClient.initialize();
        EchoHuntHud.initialize();
        EchoHuntFxClient.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(view==null) return;
            if(client.level==null || client.player==null || !client.level.dimension().location().equals(view.dimension()) || ++age>100
                    || !(io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(client.level).getGameMode() instanceof EchoGameMode)
                    || view.state().phase()==EchoPhase.FINISHED || view.state().phase()==EchoPhase.SETTLING) view=null;
        });
        org.agmas.noellesroles.client.event.CommonHudRenderCallback.EVENT.register((g,dt) -> render(g));
    }

    /** 三个 HUD 共用公开会话门控，旧本人投影不能跨局或向旁观者泄漏。 */
    static boolean visible() {
        var client = Minecraft.getInstance();
        return client.player != null && client.level != null && !client.options.hideGui && view != null && age <= 100
                && client.level.dimension().location().equals(view.dimension())
                && io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(client.level).getGameMode() instanceof EchoGameMode
                && view.state().phase() != EchoPhase.FINISHED && view.state().phase() != EchoPhase.SETTLING;
    }

    static EchoRoleData ownData() {
        if (!visible() || Minecraft.getInstance().player.isSpectator()) return null;
        var data = EchoBagClient.data();
        return data != null && data.faction() != null && view.match().equals(data.matchId()) ? data : null;
    }

    private static void render(FakeGuiGraphics g) {
        if (!visible()) return;
        var client = Minecraft.getInstance();
        int width = client.getWindow().getGuiScaledWidth(), height = client.getWindow().getGuiScaledHeight();
        var s = view.state();
        var d = ownData();
        long tick = s.displayTick(age);
        int pw = Math.min(400, width - 16), x = (width - pw) / 2, y = 4;
        boolean operations = s.phase() == EchoPhase.OPERATIONS;
        int panelHeight = operations ? 91 : s.phase() == EchoPhase.EVACUATION ? 55 : 43;
        panel(g, x, y, pw, panelHeight);
        long seconds = (s.displayRemaining(age) + 19) / 20;
        row(g, tr("phase_time", tr("phase." + s.phase().name().toLowerCase(Locale.ROOT)),
                String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60)), x + 5, y + 5, pw - 88, GOLD);
        if (s.paused()) row(g, tr("paused"), x + pw - 78, y + 5, 73, RED);
        String goal = goal(s, d);
        row(g, tr("goal." + goal), x + 5, y + 18, pw - 10, TEXT);
        row(g, tr("hint." + goal), x + 5, y + 29, pw - 10, MUTED);
        if (operations) {
            Component gate = s.coreOpen() ? s.coreUntil() > tick
                    ? tr("core.closes", EchoPublicState.seconds(s.coreUntil(), tick)) : tr("core.confirm")
                    : tr("core.closed");
            row(g, gate, x + 5, y + 40, pw - 10, s.coreOpen() ? GREEN : MUTED);
            int count = s.beacons().size();
            for (int i = 0; i < count; i++) {
                int left = x + 5 + i * (pw - 10) / count;
                int right = x + 5 + (i + 1) * (pw - 10) / count - 4;
                var b = s.beacons().get(i);
                // 编号只按公开列表顺序，不把地图内部 id 当作玩家可读名称。
                row(g, tr("beacon.supply", tr("beacon.number." + (i + 1)), b.delivered(), b.required()),
                        left, y + 53, right - left, b.active() ? GREEN : TEXT);
                int progress = b.active() ? 1000 : Math.min(999, b.progress());
                int barWidth = Math.max(8, right - left - 31);
                g.fill(left, y + 67, left + barWidth, y + 71, 0xFF49341E);
                g.fill(left, y + 67, left + barWidth * progress / 1000, y + 71, b.active() ? GREEN : GOLD);
                row(g, tr("percent", progress / 10), left + barWidth + 3, y + 64, 28, b.active() ? GREEN : BLUE);
                boolean locked = b.lockedUntil() > tick, stopped = b.pausedUntil() > tick;
                Component status = b.active() ? tr("beacon.active")
                        : locked && stopped ? tr("beacon.blocked_paused", EchoPublicState.seconds(b.lockedUntil(), tick), EchoPublicState.seconds(b.pausedUntil(), tick))
                        : locked ? tr("beacon.blocked", EchoPublicState.seconds(b.lockedUntil(), tick))
                        : stopped ? tr("beacon.paused", EchoPublicState.seconds(b.pausedUntil(), tick))
                        : tr(b.charging() ? "beacon.charging" : "beacon.collect");
                row(g, status, left, y + 77, right - left, locked || stopped ? RED : b.active() ? GREEN : MUTED);
            }
        } else if (s.phase() == EchoPhase.EVACUATION) {
            Component gate = s.evacuationOpen() ? tr("evac.open", s.escaped(), s.quota())
                    : s.closedUntil() > tick ? tr("evac.overload", s.escaped(), s.quota(), EchoPublicState.seconds(s.closedUntil(), tick))
                    : tr("evac.confirm", s.escaped(), s.quota());
            row(g, gate, x + 5, y + 40, pw - 10, s.evacuationOpen() ? GREEN : RED);
        }
        if (d == null) return;

        // 320×240 时中段左侧为 y=99..158，右侧留给技能与狩猎图形。
        int infoX = 8, infoWidth = width - 128, infoY = y + panelHeight + 4;
        if (d.contract() != null && d.contractProgress() != null) {
            var p = d.contractProgress();
            Component progress = switch (d.contract()) {
                case SMUGGLER -> tr("contract.smuggler", p.primary());
                case SCAVENGER -> tr("contract.scavenger", p.primary());
                case INFORMATION_DEALER -> tr("contract.information_dealer", p.primary(), p.secondary());
                case KEY_DEALER -> tr("contract.key_dealer", p.primary());
            };
            row(g, tr("contract.line", progress, translated("echo.dashboard.contract.state." + p.status().toLowerCase(Locale.ROOT), "contract.state.active")),
                    infoX, infoY, infoWidth, GOLD);
            infoY += 11;
        }
        int n = 0;
        for (var signal : d.signals()) if (signal.target() == null && d.projectedTick() < signal.until() && n++ < 2) {
            String kind = switch (signal.kind()) {
                case "salvage" -> "salvage"; case "key_contract" -> "key_contract";
                case "cargo_pulse" -> "cargo"; case "key_pulse" -> "key";
                default -> "tracking";
            };
            row(g, tr("signal." + kind, tr("direction." + Math.floorMod(signal.sector(), 8))), infoX, infoY, infoWidth, GOLD);
            infoY += 11;
        }
        if (EchoCardClient.selectingSecond()) {
            row(g, tr("second_point"), infoX, infoY, infoWidth, GOLD);
            infoY += 11;
        } else if (!d.requestError().isEmpty()) {
            row(g, translated("echo.error." + d.requestError().toLowerCase(Locale.ROOT), "request_failed"), infoX, infoY, infoWidth, RED);
            infoY += 11;
        }
        infoY = EchoMachineClient.renderHud(g, infoX, infoY, infoWidth, height - 99);
        if (client.screen == null) {
            var action = d.actionView();
            int ay = Math.max(infoY, Math.min(height / 2 + 20, height - 99));
            if (action != null) {
                int progress = Math.min(999, action.progress(tick));
                Component name = translated("echo.dashboard.action." + action.kind().toLowerCase(Locale.ROOT), "action.unknown");
                row(g, tr("action.progress", name, progress / 10, EchoPublicState.seconds(action.startedAt() + action.duration(), tick)),
                        infoX, ay, infoWidth, TEXT);
                g.fill(infoX, ay + 12, infoX + infoWidth, ay + 15, 0xFF49341E);
                g.fill(infoX, ay + 12, infoX + infoWidth * progress / 1000, ay + 15, GOLD);
            } else if (!d.interactionError().isEmpty()) {
                row(g, translated("echo.error." + d.interactionError().toLowerCase(Locale.ROOT), "request_failed"), infoX, ay, infoWidth, RED);
            }
        }
        renderSupplies(g, d, s, tick, width, height);
    }

    private static String goal(EchoPublicState s, EchoRoleData d) {
        if (s.paused()) return "paused";
        if (d == null) return "public." + s.phase().name().toLowerCase(Locale.ROOT);
        if (!d.life().standing()) return "life." + d.life().name().toLowerCase(Locale.ROOT);
        if (s.phase() == EchoPhase.SAFE) return "safe." + d.faction().name().toLowerCase(Locale.ROOT);
        if (d.faction() == EchoFaction.BROKER) {
            var p = d.contractProgress();
            if (p != null && p.status().equals("SUCCEEDED")) return "broker.done";
            if (p != null && p.status().equals("ARMED")) return "broker.survive";
            return d.contract() == null ? "broker.wait" : "broker." + d.contract().name().toLowerCase(Locale.ROOT);
        }
        if (s.phase() == EchoPhase.EVACUATION) return d.faction() == EchoFaction.CARRIER
                ? s.evacuationOpen() ? "carrier.evacuate" : "carrier.wait_gate"
                : s.overloadUsed() ? "interceptor.guard" : "interceptor.overload";
        if (d.faction() == EchoFaction.INTERCEPTOR) return "interceptor.operations";
        if (!s.beacons().isEmpty() && s.beacons().stream().allMatch(b -> b.delivered() >= b.required()))
            return s.beacons().stream().allMatch(EchoPublicState.Beacon::active) ? "carrier.ready" : "carrier.escort";
        if (!d.cargo().isEmpty()) return "carrier.deliver";
        return d.key() != null && !s.coreOpen() ? "carrier.unlock" : "carrier.collect";
    }

    private static void renderSupplies(FakeGuiGraphics g, EchoRoleData d, EchoPublicState s, long tick, int width, int height) {
        // y>=height-40 留给快捷栏标签，y>=height-28 留给三工具＋六物资。
        int half = (width - 24) / 2, right = width / 2 + 4, top = height - 79;
        panel(g, 8, top, half, 37);
        panel(g, right, top, half, 37);
        row(g, tr("cargo", d.cargo().size(), d.capacity(), d.blackCargoCount()), 12, top + 4, half - 8, TEXT);
        Component key = d.key() == null ? tr("key.none") : d.keyPulseAt() > tick
                ? tr("key.pulse", EchoPublicState.seconds(d.keyPulseAt(), tick)) : tr("key.held");
        row(g, key, 12, top + 15, half - 8, GOLD);
        Component life = d.life() == EchoLifeState.DOWNED ? tr("life.bleeding", (d.bleedRemaining() + 19) / 20)
                : tr("life.label", tr("life." + d.life().name().toLowerCase(Locale.ROOT)));
        row(g, life, 12, top + 26, half - 8, d.life() == EchoLifeState.DOWNED || d.life() == EchoLifeState.WOUNDED ? RED : GREEN);
        Component equipped = tr("equipped.none");
        boolean forged = false;
        if (d.selectedCard() != null) {
            var item = d.items().stream().filter(i -> i.instanceId().equals(d.selectedCard())).findFirst().orElse(null);
            if (item != null) {
                var card = EchoCardRegistry.require(item.definitionId());
                equipped = tr("equipped", translated(card.nameKey(), "card"));
                forged = card.shortId().equals("forged_signal");
            }
        }
        row(g, equipped, right + 4, top + 4, half - 8, GOLD);
        Component basic = switch (d.faction()) {
            case CARRIER -> d.shoveRemaining() > 0 ? tr("shove.cooldown", (d.shoveRemaining() + 19) / 20) : tr("shove.ready");
            case INTERCEPTOR -> s.phase() == EchoPhase.EVACUATION ? tr(s.overloadUsed() ? "overload.used" : "overload.ready") : tr("intercept");
            case BROKER -> tr("trade");
        };
        row(g, basic, right + 4, top + 15, half - 8, TEXT);
        row(g, d.faction()==EchoFaction.CARRIER ? EchoRollClient.hud(d) : forged ? tr("forgery") : tr("skill", translated(d.skill().nameKey(), "skill.generic")), right + 4, top + 26, half - 8, MUTED);
    }

    static Component tr(String key, Object... args) { return Component.translatable("echo.dashboard." + key, args); }
    private static Component translated(String key, String fallback) {
        return I18n.exists(key) ? Component.translatable(key) : tr(fallback);
    }
    private static void panel(FakeGuiGraphics g, int x, int y, int width, int height) {
        g.fillGradient(x, y, x + width, y + height, 0xD81A1008, 0xD820140A);
        g.renderOutline(x, y, width, height, 0xFF8B6914);
        g.fill(x + 1, y + 1, x + width - 1, y + 2, 0x33FFE8C0);
    }
    static void row(FakeGuiGraphics g, Component text, int x, int y, int width, int color) {
        var font = Minecraft.getInstance().font;
        String line = text.getString();
        if (font.width(line) > width) line = font.plainSubstrByWidth(line, Math.max(0, width - font.width("…"))) + "…";
        g.enableScissor(x, y, x + width, y + 10);
        g.drawString(font, line, x, y, color, false);
        g.disableScissor();
    }
}
