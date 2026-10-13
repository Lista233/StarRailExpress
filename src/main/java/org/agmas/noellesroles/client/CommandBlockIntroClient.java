package org.agmas.noellesroles.client;

import org.agmas.noellesroles.content.entity.CommandBlockMarkerEntity;

import io.wifi.starrailexpress.client.gui.MapRuleIntroHud;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

/** 命令方块开场镜头期间，不要再叠地图进场的规则卡。 */
public final class CommandBlockIntroClient {
    private static int pendingId = -1;
    private static int pendingTicks;
    private static int pendingWait;
    private static int activeTicks;
    private static boolean holdRules;

    private CommandBlockIntroClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(CommandBlockIntroClient::tick);
    }

    public static void watch(int entityId, int durationTicks) {
        pendingId = entityId;
        pendingTicks = Math.max(1, durationTicks);
        pendingWait = 40;
        holdRules = true;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        var entity = minecraft.level.getEntity(entityId);
        if (entity instanceof CommandBlockMarkerEntity) {
            arm();
        } else if (entity != null) {
            releaseHold();
        }
    }

    public static boolean isActive() {
        return activeTicks > 0 || holdRules;
    }

    private static void tick(Minecraft minecraft) {
        if (activeTicks > 0 && --activeTicks == 0) {
            holdRules = false;
        }
        if (pendingWait <= 0 || minecraft.level == null) {
            return;
        }
        pendingWait--;
        var entity = minecraft.level.getEntity(pendingId);
        if (entity instanceof CommandBlockMarkerEntity) {
            arm();
            return;
        }
        if (entity != null || pendingWait == 0) {
            releaseHold();
        }
    }

    private static void arm() {
        activeTicks = pendingTicks + 8;
        pendingWait = 0;
        holdRules = true;
        MapRuleIntroHud.clear();
    }

    private static void releaseHold() {
        holdRules = false;
        pendingWait = 0;
    }
}
