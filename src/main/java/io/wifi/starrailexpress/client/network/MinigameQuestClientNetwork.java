/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.client.network;

import io.wifi.starrailexpress.client.gui.screen.MinigameQuestConfigScreen;
import io.wifi.starrailexpress.client.gui.screen.MinigameScreenFactory;
import io.wifi.starrailexpress.network.MinigameQuestPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.agmas.noellesroles.role.qust.roles.minigame_master.ItemMinigamePayload;
import org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderPayload;
import org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderScreen;
import org.agmas.noellesroles.role.qust.roles.wanderer.WandererClientHandlers;
import org.agmas.noellesroles.role.qust.roles.wanderer.WandererHud;
import org.agmas.noellesroles.role.qust.roles.wanderer.WandererPayload;

/**
 * 小游戏任务点方块 — 客户端网络处理
 */
public class MinigameQuestClientNetwork {

    public static void register() {
        // 创造模式：打开配置界面
        ClientPlayNetworking.registerGlobalReceiver(MinigameQuestPayload.OpenConfig.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> client.setScreen(new MinigameQuestConfigScreen(
                            payload.pos(),
                            payload.data().getString("MinigameId"),
                            payload.data().getInt("MarkerColor"),
                            payload.data().getBoolean("IsTaskMarker"),
                            payload.data().getBoolean("IsSabotageTrigger"),
                            payload.data().getInt("SabotageDuration"),
                            payload.data().getInt("SabotageCooldown"))));
                });

        // 冒险模式：打开小游戏界面
        ClientPlayNetworking.registerGlobalReceiver(MinigameQuestPayload.OpenGame.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> {
                        // onSuccess → 发送完成通知到服务端
                        Runnable onSuccess = () -> ClientPlayNetworking.send(
                                new MinigameQuestPayload.CompleteGame(payload.pos()));
                        Screen screen = MinigameScreenFactory.create(
                                payload.minigameId(), payload.pos(), onSuccess);
                        if (screen != null) {
                            client.setScreen(screen);
                        }
                    });
                });

        // 小游戏达人：打开小游戏界面（复用 MinigameScreenFactory）
        ClientPlayNetworking.registerGlobalReceiver(ItemMinigamePayload.OpenItemGame.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> {
                        // onSuccess → 发送完成通知到服务端（携带小游戏 ID 和挑战标记）
                        String mgId = payload.minigameId();
                        boolean isChallenge = payload.challenge();
                        Runnable onSuccess = () -> ClientPlayNetworking.send(
                                new ItemMinigamePayload.CompleteItemGame(mgId, isChallenge));
                        Screen screen = MinigameScreenFactory.create(
                                payload.minigameId(), net.minecraft.core.BlockPos.ZERO, onSuccess);
                        if (screen != null) {
                            client.setScreen(screen);
                        }
                    });
                });

        // 超级记录员：打开标记界面
        ClientPlayNetworking.registerGlobalReceiver(SuperRecorderPayload.OpenMarkScreen.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> client.setScreen(
                            new SuperRecorderScreen(payload.mode())));
                });

        // 游荡者：灵魂出窍状态同步
        ClientPlayNetworking.registerGlobalReceiver(WandererPayload.SoulOutState.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> {
                        if (payload.active()) {
                            WandererClientHandlers.enterSoulOut();
                        } else {
                            WandererClientHandlers.exitSoulOut();
                        }
                    });
                });

        // 游荡者：幽灵显隐同步
        ClientPlayNetworking.registerGlobalReceiver(WandererPayload.GhostVisibility.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> {
                        var comp = org.agmas.noellesroles.role.qust.QUSTComponentKeys.Keys.WANDERER
                                .maybeGet(client.player).orElse(null);
                        if (comp != null) {
                            comp.setGhostVisible(payload.visible());
                        }
                    });
                });

        // 游荡者：进入幽灵状态
        ClientPlayNetworking.registerGlobalReceiver(WandererPayload.EnterGhostState.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> {
                        var comp = org.agmas.noellesroles.role.qust.QUSTComponentKeys.Keys.WANDERER
                                .maybeGet(client.player).orElse(null);
                        if (comp != null) {
                            comp.setGhostState(true);
                        }
                    });
                });

        // 游荡者：死亡通知覆盖层
        ClientPlayNetworking.registerGlobalReceiver(WandererPayload.DeathNotification.TYPE,
                (payload, context) -> {
                    Minecraft client = context.client();
                    client.execute(() -> WandererHud.showDeathNotification());
                });
    }
}
