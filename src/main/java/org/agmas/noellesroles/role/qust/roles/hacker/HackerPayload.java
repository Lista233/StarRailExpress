package org.agmas.noellesroles.role.qust.roles.hacker;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * 黑客网络数据包
 */
public class HackerPayload {

    /**
     * S2C: 显示被标记玩家的信息（发给黑客）
     */
    public record ShowMarkedInfo(String playerName, UUID uuid, String ip, String roleName) implements CustomPacketPayload {

        public static final Type<ShowMarkedInfo> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("qust", "hacker_show_marked"));

        public static final StreamCodec<FriendlyByteBuf, ShowMarkedInfo> CODEC =
            StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                ShowMarkedInfo::playerName,
                net.minecraft.network.codec.ByteBufCodecs.fromCodec(net.minecraft.core.UUIDUtil.CODEC),
                ShowMarkedInfo::uuid,
                net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                ShowMarkedInfo::ip,
                net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                ShowMarkedInfo::roleName,
                ShowMarkedInfo::new
            );

        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * S2C: 显示"您已被标记"消息（发给被标记玩家）
     */
    public record ShowBeenMarked(String playerName, UUID uuid, String ip) implements CustomPacketPayload {

        public static final Type<ShowBeenMarked> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("qust", "hacker_show_been_marked"));

        public static final StreamCodec<FriendlyByteBuf, ShowBeenMarked> CODEC =
            StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                ShowBeenMarked::playerName,
                net.minecraft.network.codec.ByteBufCodecs.fromCodec(net.minecraft.core.UUIDUtil.CODEC),
                ShowBeenMarked::uuid,
                net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                ShowBeenMarked::ip,
                ShowBeenMarked::new
            );

        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * S2C: 发送确认消息（发给黑客）
     */
    public record SendConfirm(int count) implements CustomPacketPayload {

        public static final Type<SendConfirm> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("qust", "hacker_send_confirm"));

        public static final StreamCodec<FriendlyByteBuf, SendConfirm> CODEC =
            StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                SendConfirm::count,
                SendConfirm::new
            );

        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
