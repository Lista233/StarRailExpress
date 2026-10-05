package org.agmas.noellesroles.role.qust.roles.bettor;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import org.agmas.noellesroles.client.screen.DevilRouletteScreen;

/**
 * 筹客客户端网络处理器
 */
public class BettorClientHandlers {

    /** 当前打开的恶魔轮盘界面引用，用于更新状态 */
    private static DevilRouletteScreen currentScreen = null;

    public static void register() {
        // S2C: 开始滚动动画 → 打开恶魔轮盘界面
        ClientPlayNetworking.registerGlobalReceiver(
            BettorPayload.StartRolling.TYPE,
            (payload, context) -> {
                context.client().execute(() -> {
                    DevilRouletteScreen screen = new DevilRouletteScreen();
                    screen.startRolling();
                    currentScreen = screen;
                    context.client().setScreen(screen);
                });
            }
        );

        // S2C: 轮盘结果 → 更新界面显示结果
        ClientPlayNetworking.registerGlobalReceiver(
            BettorPayload.RouletteResult.TYPE,
            (payload, context) -> {
                context.client().execute(() -> {
                    Minecraft client = context.client();
                    // 如果当前界面是恶魔轮盘，直接更新
                    if (client.screen instanceof DevilRouletteScreen rouletteScreen) {
                        rouletteScreen.showResult(payload.number(), payload.description());
                    } else {
                        // 否则新开界面显示结果
                        DevilRouletteScreen screen = new DevilRouletteScreen();
                        screen.showResult(payload.number(), payload.description());
                        currentScreen = screen;
                        client.setScreen(screen);
                    }
                });
            }
        );
    }
}
