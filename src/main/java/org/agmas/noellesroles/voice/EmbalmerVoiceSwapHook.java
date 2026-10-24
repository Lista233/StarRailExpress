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

import de.maxhenkel.voicechat.api.Position;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatServerApi;
import de.maxhenkel.voicechat.api.events.MicrophonePacketEvent;
import de.maxhenkel.voicechat.api.packets.EntitySoundPacket;
import de.maxhenkel.voicechat.api.packets.MicrophonePacket;
import net.minecraft.server.level.ServerPlayer;
import org.agmas.noellesroles.game.roles.killer.embalmer.EmbalmerVoiceSwapManager;

/**
 * 嬉命人·语音互换的语音路由。
 *
 * <p>核心：不交换音频数据，只把「声源实体」换成对方。{@code channelId} 仍是说话者本人，
 * 所以语音 HUD 上显示的说话人不变；{@code entityUuid} 换成交换对象，
 * 于是声音从对方所在位置发出并随对方移动。
 *
 * <p>与参考实现的对应关系（{@code GreysVoicechatPlugin#onMicrophonePacket} 尾部）：
 * <ul>
 * <li>低语距离读服务端配置 {@code whisper_distance}，缺省退回语音距离的一半；</li>
 * <li>用 {@code getPlayersInRange} 以「交换对象所在位置」为圆心取收听范围内的玩家，
 * 逐个 {@code sendEntitySoundPacketTo}（不再自行遍历世界玩家 + 距离裁剪，
 * 也不再额外要求 {@code isInstalled()}/{@code isConnected()}，避免误伤正常连接）；</li>
 * <li>转发完成后 {@code cancel()} 原麦克风包，阻止默认从说话者位置广播。</li>
 * </ul>
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
        boolean whispering = packet.isWhispering();
        // 低语距离取服务端配置（与 Gexpress 一致）；没有配置时退回默认语音距离的一半
        float distance = (float) (whispering
                ? api.getServerConfig().getDouble("whisper_distance", api.getVoiceChatDistance() / 2.0D)
                : api.getVoiceChatDistance());

        // channelId 仍是说话者本人（语音 HUD 说话人不变）；entityUuid 换成交换对象（声源位置互换）
        EntitySoundPacket.Builder<?> builder = packet.entitySoundPacketBuilder();
        builder.channelId(speaker.getUUID())
                .entityUuid(body.getUUID())
                .distance(distance)
                .whispering(whispering);
        EntitySoundPacket sound = builder.build();

        // 以「声源实体（交换对象）」所在位置为圆心，向语音可达范围内的玩家逐个转发
        Position position = api.createPosition(body.getX(), body.getY(), body.getZ());
        double maxDistance = Math.max(distance, api.getBroadcastRange());
        for (de.maxhenkel.voicechat.api.ServerPlayer listener : api.getPlayersInRange(
                api.fromServerLevel(speaker.serverLevel()), position, maxDistance)) {
            if (listener.getUuid().equals(speaker.getUUID())) {
                continue;
            }
            VoicechatConnection connection = api.getConnectionOf(listener.getUuid());
            if (connection != null) {
                api.sendEntitySoundPacketTo(connection, sound);
            }
        }

        event.cancel();
        return true;
    }
}
