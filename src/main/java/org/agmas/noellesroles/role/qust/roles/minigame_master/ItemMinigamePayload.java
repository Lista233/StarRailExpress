package org.agmas.noellesroles.role.qust.roles.minigame_master;

import io.wifi.starrailexpress.SRE;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jetbrains.annotations.NotNull;

/**
 * 小游戏达人网络包
 * <ul>
 *   <li>{@link OpenItemGame} S2C：通知客户端打开小游戏界面（challenge 标记是否为华容道挑战）</li>
 *   <li>{@link CompleteItemGame} C2S：客户端小游戏完成后通知服务端发放奖励或触发胜利</li>
 * </ul>
 */
public class ItemMinigamePayload {

    /** 服务端 → 客户端：打开小游戏 */
    public record OpenItemGame(String minigameId, boolean challenge) implements CustomPacketPayload {
        public static final Type<OpenItemGame> TYPE = new Type<>(SRE.id("qust_item_minigame_open"));
        public static final StreamCodec<FriendlyByteBuf, OpenItemGame> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, OpenItemGame::minigameId,
                ByteBufCodecs.BOOL, OpenItemGame::challenge,
                OpenItemGame::new);

        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** 客户端 → 服务端：小游戏完成，领取奖励 */
    public record CompleteItemGame(String minigameId, boolean challenge) implements CustomPacketPayload {
        public static final Type<CompleteItemGame> TYPE = new Type<>(SRE.id("qust_item_minigame_complete"));
        public static final StreamCodec<FriendlyByteBuf, CompleteItemGame> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CompleteItemGame::minigameId,
                ByteBufCodecs.BOOL, CompleteItemGame::challenge,
                CompleteItemGame::new);

        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
