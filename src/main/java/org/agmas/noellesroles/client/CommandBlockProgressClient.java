package org.agmas.noellesroles.client;

import java.util.HashSet;
import java.util.Set;

import org.agmas.noellesroles.client.event.CommonHudRenderCallback;
import org.agmas.noellesroles.content.entity.CommandBlockMarkerEntity;
import org.agmas.noellesroles.game.command_block.CommandBlockCracks;
import org.agmas.noellesroles.game.command_block.CommandBlockRules;
import org.agmas.noellesroles.packet.CommandBlockProgressS2CPacket;

import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.client.SREClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;

/** 时间下方的破坏条，以及只给杀手看的裂缝发光。 */
public final class CommandBlockProgressClient {
    private static int progress;
    private static int goal;
    private static final Set<Integer> GLOWING = new HashSet<>();

    private CommandBlockProgressClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(CommandBlockProgressS2CPacket.ID,
                (packet, context) -> context.client().execute(() -> {
                    progress = Math.max(0, packet.progress());
                    goal = Math.max(0, packet.goal());
                }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
        ClientTickEvents.END_CLIENT_TICK.register(CommandBlockProgressClient::tickGlow);
        CommonHudRenderCallback.EVENT.register((graphics, delta) -> render(graphics));
    }

    public static boolean isShowing() {
        return goal > 0;
    }

    private static void clear() {
        progress = 0;
        goal = 0;
        GLOWING.clear();
    }

    private static void render(io.wifi.utils.client.betterrender.FakeGuiGraphics graphics) {
        if (goal <= 0) {
            return;
        }
        int width = 84;
        int x = graphics.guiWidth() / 2 - width / 2;
        int y = 30;
        float fill = Math.min(1.0F, progress / (float) goal);
        graphics.fill(x - 1, y - 1, x + width + 1, y + 5, 0xAA120018);
        graphics.fill(x, y, x + (int) (width * fill), y + 4, 0xFFB44DFF);
        Component text = Component.translatable("hud.noellesroles.command_block.progress", progress, goal);
        graphics.drawCenteredString(Minecraft.getInstance().font, text, graphics.guiWidth() / 2, y + 6, 0xFFE7D4FF);
    }

    private static void tickGlow(Minecraft client) {
        if (client.level == null || client.player == null) {
            GLOWING.clear();
            return;
        }
        for (Entity entity : client.level.entitiesForRendering()) {
            if (entity instanceof CommandBlockMarkerEntity) {
                CommandBlockSkyLight.seeMarker();
                break;
            }
        }
        boolean killer = isKiller(client);
        if (!killer) {
            clearGlow(client);
            return;
        }
        Set<Integer> seen = new HashSet<>();
        var box = client.player.getBoundingBox().inflate(128.0D);
        for (Entity entity : client.level.getEntities(client.player, box, CommandBlockCracks::isVisibleCrack)) {
            entity.setGlowingTag(true);
            seen.add(entity.getId());
        }
        GLOWING.removeIf(id -> {
            if (seen.contains(id)) {
                return false;
            }
            Entity entity = client.level.getEntity(id);
            if (entity != null) {
                entity.setGlowingTag(false);
            }
            return true;
        });
        GLOWING.addAll(seen);
    }

    private static void clearGlow(Minecraft client) {
        if (client.level == null) {
            GLOWING.clear();
            return;
        }
        for (int id : GLOWING) {
            Entity entity = client.level.getEntity(id);
            if (entity != null) {
                entity.setGlowingTag(false);
            }
        }
        GLOWING.clear();
    }

    private static boolean isKiller(Minecraft client) {
        if (SREClient.gameComponent == null || client.player == null) {
            return false;
        }
        SRERole role = SREClient.gameComponent.getRole(client.player);
        return role != null && (role.isKiller() || CommandBlockRules.isPoolRole(role));
    }
}
