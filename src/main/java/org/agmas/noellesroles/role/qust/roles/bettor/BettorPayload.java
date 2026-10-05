package org.agmas.noellesroles.role.qust.roles.bettor;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 筹客网络数据包
 */
public class BettorPayload {

    /**
     * S2C: 通知客户端开始滚动动画
     */
    public record StartRolling() implements CustomPacketPayload {

        public static final Type<StartRolling> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("qust", "bettor_start_rolling"));

        public static final StreamCodec<FriendlyByteBuf, StartRolling> CODEC =
            StreamCodec.unit(new StartRolling());

        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    /**
     * S2C: 轮盘结果（最终数字 + 效果描述）
     */
    public record RouletteResult(int number, String description) implements CustomPacketPayload {

        public static final Type<RouletteResult> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("qust", "bettor_roulette_result"));

        public static final StreamCodec<FriendlyByteBuf, RouletteResult> CODEC =
            StreamCodec.composite(
                net.minecraft.network.codec.ByteBufCodecs.VAR_INT,
                RouletteResult::number,
                net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8,
                RouletteResult::description,
                RouletteResult::new
            );

        @Override
        public @NotNull Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }
}
