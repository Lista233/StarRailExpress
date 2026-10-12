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

package org.agmas.noellesroles.client.hud.roles;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.agmas.noellesroles.client.event.RoleHudRenderCallback;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.agmas.noellesroles.role.qust.QUSTConfig;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.roles.minigame_master.MinigameMasterPlayerComponent;

public final class MinigameMasterHud {
    private MinigameMasterHud() {
    }

    /** 上一次渲染时记录的连击进度（用于检测连击完成） */
    private static int lastProgress = -1;
    /** 连击完成通知剩余显示 tick */
    private static int comboNotifyTicks = 0;
    /** 连击完成奖励金额（缓存用于渲染） */
    private static int lastComboReward = 0;
    /** 连击完成通知持续时间（3秒 = 60 tick） */
    private static final int COMBO_NOTIFY_DURATION = 60;

    /** 上一次记录的金币余额（用于检测金币增加） */
    private static int lastBalance = -1;
    /** 单次完成金币通知剩余显示 tick */
    private static int coinNotifyTicks = 0;
    /** 单次金币增加量 */
    private static int lastCoinGain = 0;
    /** 单次完成通知持续时间（2秒 = 40 tick） */
    private static final int COIN_NOTIFY_DURATION = 40;

    public static void register() {
        RoleHudRenderCallback.EVENT.register(QUSTRoles.MINIGAME_MASTER_ID, (context, tickCounter) -> {
            Minecraft client = Minecraft.getInstance();
            if (client.player == null) {
                return;
            }
            MinigameMasterPlayerComponent comp = QUSTComponentKeys.Keys.MINIGAME_MASTER
                    .maybeGet(client.player).orElse(null);
            if (comp == null) {
                return;
            }
            QUSTConfig cfg = QUSTConfig.instance();
            int comboCount = cfg.minigameMasterTicketComboCount;
            if (comboCount <= 0) {
                return;
            }
            int progress = comp.ticketMinigameCompleted % comboCount;
            int comboReward = cfg.minigameMasterTicketComboCoinReward;

            // 检测金币增加（通过余额差值）
            int currentBalance = io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(client.player).balance;
            if (lastBalance >= 0 && currentBalance > lastBalance) {
                lastCoinGain = currentBalance - lastBalance;
                coinNotifyTicks = COIN_NOTIFY_DURATION;
            }
            lastBalance = currentBalance;

            // 检测连击完成：进度从非零变为零（且之前已初始化）
            if (lastProgress >= 0 && progress == 0 && lastProgress != 0) {
                comboNotifyTicks = COMBO_NOTIFY_DURATION;
                lastComboReward = comboReward;
            }
            lastProgress = progress;

            // ── 渲染 ──
            int x = context.guiWidth() - 10;
            int y = context.guiHeight() - 20;

            // 连击进度（右下角金色常驻）
            Component text = Component.translatable("hud.noellesroles.minigame_master.combo",
                    progress, comboCount);
            context.drawString(client.font, text, x - client.font.width(text), y, 0xFFFFD700);

            int notifyY = y;

            // 单次完成金币通知（绿色渐隐）
            if (coinNotifyTicks > 0) {
                coinNotifyTicks--;
                int alpha = Math.min(255, coinNotifyTicks * 255 / COIN_NOTIFY_DURATION);
                int color = (alpha << 24) | 0x55FF55;
                Component coinText = Component.translatable(
                        "hud.noellesroles.minigame_master.coin_notify", lastCoinGain);
                notifyY -= 13;
                context.drawString(client.font, coinText,
                        x - client.font.width(coinText), notifyY, color);
            }

            // 连击完成通知（金色加粗渐隐，显示在进度上方）
            if (comboNotifyTicks > 0) {
                comboNotifyTicks--;
                int alpha = Math.min(255, comboNotifyTicks * 255 / COMBO_NOTIFY_DURATION);
                int color = (alpha << 24) | 0xFFD700;
                Component notifyText = Component.translatable(
                        "hud.noellesroles.minigame_master.combo_notify", lastComboReward)
                        .withStyle(ChatFormatting.BOLD);
                notifyY -= 13;
                context.drawString(client.font, notifyText,
                        x - client.font.width(notifyText), notifyY, color);
            }
        });
    }
}
