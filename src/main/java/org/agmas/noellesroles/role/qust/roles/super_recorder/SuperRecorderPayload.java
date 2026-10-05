package org.agmas.noellesroles.role.qust.roles.super_recorder;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * 超级记录员网络包
 * <ul>
 *   <li>{@link MarkPlayer} C2S — 客户端标记请求（选玩家 + 选角色）</li>
 *   <li>{@link OpenMarkScreen} S2C — 服务端通知客户端打开标记界面</li>
 * </ul>
 */
public class SuperRecorderPayload {

    /**
     *
     * C2S：客户端请求标记某玩家为某角色
     */
    public record MarkPlayer(UUID targetUuid, String roleId) implements CustomPacketPayload {
        public static final Type<MarkPlayer> TYPE = new Type<>(SRE.id("qust_super_recorder_mark"));
        public static final StreamCodec<FriendlyByteBuf, MarkPlayer> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), MarkPlayer::targetUuid,
                ByteBufCodecs.STRING_UTF8, MarkPlayer::roleId,
                MarkPlayer::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * S2C：通知客户端打开标记界面（记录员笔记 / 真相之书）
     * @param mode 0 = 记录员笔记（标记所有玩家），1 = 真相之书（仅标记未记录玩家）
     */
    public record OpenMarkScreen(int mode) implements CustomPacketPayload {
        public static final Type<OpenMarkScreen> TYPE = new Type<>(SRE.id("qust_super_recorder_open_screen"));
        public static final StreamCodec<FriendlyByteBuf, OpenMarkScreen> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, OpenMarkScreen::mode,
                OpenMarkScreen::new);

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * S2C：通知客户端打开真相之书显示界面（左键触发）
     */
    public record OpenTruthBook() implements CustomPacketPayload {
        public static final Type<OpenTruthBook> TYPE = new Type<>(SRE.id("qust_super_recorder_open_truth_book"));
        public static final StreamCodec<FriendlyByteBuf, OpenTruthBook> CODEC = StreamCodec.unit(new OpenTruthBook());

        @Override
        public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
