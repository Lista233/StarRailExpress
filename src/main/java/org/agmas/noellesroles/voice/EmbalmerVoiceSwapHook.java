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

package org.agmas.noellesroles.voice;

import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerVoiceSwapManager;

/**
 * 嬉命人·语音互换的语音路由
 *
 * <p>核心：不交换音频数据，只把「声源实体」换成对方。{@code channelId} 仍是说话者本人，
 * 所以语音 HUD 上显示的说话人不变；{@code entityUuid} 换成交换对象，
 * 于是声音从对方所在位置发出并随对方移动。
 */
public final class EmbalmerVoiceSwapHook {

    private EmbalmerVoiceSwapHook() {
    }

    /**
     * @return true 表示本次麦克风包已被接管（已 {@code cancel()} 并手工转发），调用方应直接 return
     */
    public static boolean handle(MicrophonePacketEvent event, VoicechatServerApi api, ServerPlayer speaker) {
        if (event.isCancelled()) {
            return false;
        }
        ServerPlayer body = EmbalmerVoiceSwapManager.voiceBody(speaker);
        if (body == null || body.getUUID().equals(speaker.getUUID())) {
            return false;
        }

        MicrophonePacket packet = event.getPacket();
        float distance = (float) (packet.isWhispering()
                ? api.getVoiceChatDistance() * 0.5D
                : api.getVoiceChatDistance());

        EntitySoundPacket.Builder<?> builder = packet.entitySoundPacketBuilder();
        builder.channelId(speaker.getUUID())
                .entityUuid(body.getUUID())
                .distance(distance)
                .whispering(packet.isWhispering());
        EntitySoundPacket sound = builder.build();

        double maxDistance = Math.max(distance, api.getBroadcastRange());
        for (ServerPlayer listener : speaker.serverLevel().players()) {
            if (listener.getUUID().equals(speaker.getUUID())) {
                continue;
            }
            if (listener.distanceTo(body) > maxDistance) {
                continue;
            }
            VoicechatConnection connection = api.getConnectionOf(listener.getUUID());
            if (connection != null && connection.isInstalled() && connection.isConnected()) {
                api.sendEntitySoundPacketTo(connection, sound);
            }
        }

        event.cancel();
        return true;
    }
}
