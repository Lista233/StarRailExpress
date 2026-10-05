package org.agmas.noellesroles.role.qust.roles.hacker;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * 黑客客户端网络处理器
 */
public class HackerClientHandlers {

    public static void register() {
        // S2C: 显示被标记玩家信息（黑客看到）
        ClientPlayNetworking.registerGlobalReceiver(
            HackerPayload.ShowMarkedInfo.TYPE,
            (payload, context) -> {
                context.client().execute(() -> {
                    HackerHud.showMarkedInfo(payload.playerName(), payload.uuid(), payload.ip());
                });
            }
        );

        // S2C: 显示"您已被标记"消息（被标记玩家看到）
        ClientPlayNetworking.registerGlobalReceiver(
            HackerPayload.ShowBeenMarked.TYPE,
            (payload, context) -> {
                context.client().execute(() -> {
                    showBeenMarkedScreen(payload.playerName(), payload.uuid(), payload.ip());
                });
            }
        );

        // S2C: 发送确认消息
        ClientPlayNetworking.registerGlobalReceiver(
            HackerPayload.SendConfirm.TYPE,
            (payload, context) -> {
                context.client().execute(() -> {
                    HackerHud.showSendConfirm(payload.count());
                });
            }
        );
    }

    /**
     * 显示"您已被标记"的屏幕信息
     */
    private static void showBeenMarkedScreen(String playerName, java.util.UUID uuid, String ip) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 掩码IP
        String maskedIP = maskIP(ip);

        // 查询属地（使用IPLocator）
        String location = org.agmas.noellesroles.role.qust.util.IPLocator.locate(ip);

        // 使用Title API显示大标题
        Component title = Component.literal("§4§l您已被标记");
        mc.gui.setTitle(title);

        // 子标题显示信息摘要
        Component subtitle = Component.literal("§c您的信息已被黑客获取");
        mc.gui.setSubtitle(subtitle);

        mc.gui.setTimes(10, 70, 20); // 淡入、停留、淡出时间

        // 同时在聊天框显示详细信息
        mc.player.displayClientMessage(Component.literal("§k||||||||||||||||||||||||||||||||"), false);
        mc.player.displayClientMessage(Component.literal("§4§l 您已被标记 "), false);
        mc.player.displayClientMessage(Component.literal("§k||||||||||||||||||||||||||||||||"), false);
        mc.player.displayClientMessage(Component.literal(""), false);
        mc.player.displayClientMessage(Component.literal("§c§l玩家昵称：§f" + playerName), false);
        mc.player.displayClientMessage(Component.literal("§c§lUUID：§f" + uuid.toString()), false);
        mc.player.displayClientMessage(Component.literal("§c§lIP 地址：§f" + maskedIP), false);
        mc.player.displayClientMessage(Component.literal("§c§lIP 属地：§f" + location), false);
        mc.player.displayClientMessage(Component.literal(""), false);
    }

    /**
     * 掩码IP地址
     */
    private static String maskIP(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + ".XX.XX";
        }
        return "192.168.XX.XX";
    }
}
