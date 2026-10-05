package org.agmas.noellesroles.role.qust.roles.bettor;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * 筹客客户端网络处理器
 */
public class BettorClientHandlers {

    public static void register() {
        // S2C: 开始滚动动画
        ClientPlayNetworking.registerGlobalReceiver(
            BettorPayload.StartRolling.TYPE,
            (payload, context) -> {
                context.client().execute(BettorHud::startRolling);
            }
        );

        // S2C: 轮盘结果
        ClientPlayNetworking.registerGlobalReceiver(
            BettorPayload.RouletteResult.TYPE,
            (payload, context) -> {
                context.client().execute(() -> {
                    BettorHud.showResult(payload.number(), payload.description());
                });
            }
        );
    }
}
