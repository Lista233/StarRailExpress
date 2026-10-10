package org.agmas.noellesroles.role.qust.roles.aimlabs;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Aimlabs 练枪方块网络包定义。
 */
public class AimlabsPayload {

    /** 服务端→客户端：开始会话 */
    public record StartSession(BlockPos blockPos, AimlabsBlockEntity.GameMode mode,
            int countdownSeconds, int targetHits,
            int areaWidth, int areaHeight,
            float sphereRadius, int maxSpheres, Direction facing) implements CustomPacketPayload {
        public static final Type<StartSession> TYPE = new Type<>(SRE.id("aimlabs_start"));

        public static final StreamCodec<FriendlyByteBuf, StartSession> CODEC =
                new StreamCodec<>() {
                    @Override
                    public StartSession decode(FriendlyByteBuf buf) {
                        return new StartSession(
                                BlockPos.STREAM_CODEC.decode(buf),
                                AimlabsBlockEntity.GameMode.valueOf(buf.readUtf(16)),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readVarInt(),
                                buf.readFloat(),
                                buf.readVarInt(),
                                Direction.from2DDataValue(buf.readVarInt()));
                    }

                    @Override
                    public void encode(FriendlyByteBuf buf, StartSession payload) {
                        BlockPos.STREAM_CODEC.encode(buf, payload.blockPos);
                        buf.writeUtf(payload.mode.name(), 16);
                        buf.writeVarInt(payload.countdownSeconds);
                        buf.writeVarInt(payload.targetHits);
                        buf.writeVarInt(payload.areaWidth);
                        buf.writeVarInt(payload.areaHeight);
                        buf.writeFloat(payload.sphereRadius);
                        buf.writeVarInt(payload.maxSpheres);
                        buf.writeVarInt(payload.facing.get2DDataValue());
                    }
                };

        @Override
        public Type<StartSession> type() { return TYPE; }
    }

    /** 服务端→客户端：会话状态更新 */
    public record SessionUpdate(int remainingSeconds, int score, int elapsedTicks) implements CustomPacketPayload {
        public static final Type<SessionUpdate> TYPE = new Type<>(SRE.id("aimlabs_update"));
        public static final StreamCodec<FriendlyByteBuf, SessionUpdate> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, SessionUpdate::remainingSeconds,
                ByteBufCodecs.INT, SessionUpdate::score,
                ByteBufCodecs.INT, SessionUpdate::elapsedTicks,
                SessionUpdate::new);

        @Override
        public Type<SessionUpdate> type() { return TYPE; }
    }

    /** 服务端→客户端：靶标被击中 */
    public record TargetHit(int entityId) implements CustomPacketPayload {
        public static final Type<TargetHit> TYPE = new Type<>(SRE.id("aimlabs_hit"));
        public static final StreamCodec<FriendlyByteBuf, TargetHit> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, TargetHit::entityId,
                TargetHit::new);

        @Override
        public Type<TargetHit> type() { return TYPE; }
    }

    /** 服务端→客户端：会话结束 */
    public record EndSession(int finalScore, int elapsedTicks) implements CustomPacketPayload {
        public static final Type<EndSession> TYPE = new Type<>(SRE.id("aimlabs_end"));
        public static final StreamCodec<FriendlyByteBuf, EndSession> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, EndSession::finalScore,
                ByteBufCodecs.INT, EndSession::elapsedTicks,
                EndSession::new);

        @Override
        public Type<EndSession> type() { return TYPE; }
    }

    /** 客户端→服务端：练习手枪射击通知 */
    public record PracticeShot(int targetEntityId) implements CustomPacketPayload {
        public static final Type<PracticeShot> TYPE = new Type<>(SRE.id("aimlabs_practice_shot"));
        public static final StreamCodec<FriendlyByteBuf, PracticeShot> CODEC = StreamCodec.composite(
                ByteBufCodecs.INT, PracticeShot::targetEntityId,
                PracticeShot::new);

        @Override
        public Type<PracticeShot> type() { return TYPE; }
    }

    /** 注册所有网络包接收器（服务端调用）。 */
    public static void register() {
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(PracticeShot.TYPE, PracticeShot.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(StartSession.TYPE, StartSession.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(SessionUpdate.TYPE, SessionUpdate.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(TargetHit.TYPE, TargetHit.CODEC);
        net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(EndSession.TYPE, EndSession.CODEC);

        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.registerGlobalReceiver(
                PracticeShot.TYPE, new PracticeShotReceiver());
    }

    /** 客户端注册接收器。 */
    public static void registerClient() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                StartSession.TYPE, (payload, context) -> context.client().execute(() -> {
                    AimlabsClientState.onStart(payload);
                }));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                SessionUpdate.TYPE, (payload, context) -> context.client().execute(() -> {
                    AimlabsClientState.onUpdate(payload);
                }));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                TargetHit.TYPE, (payload, context) -> context.client().execute(() -> {
                    AimlabsClientState.onHit(payload);
                }));
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
                EndSession.TYPE, (payload, context) -> context.client().execute(() -> {
                    AimlabsClientState.onEnd(payload);
                }));
    }

    /** 服务端处理练习手枪射击包。 */
    private static class PracticeShotReceiver implements net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.PlayPayloadHandler<PracticeShot> {
        @Override
        public void receive(PracticeShot payload, net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.Context context) {
            var player = context.player();
            var level = player.serverLevel();
            var entity = level.getEntity(payload.targetEntityId());
            if (entity instanceof AimlabsTargetEntity target) {
                target.onPracticeHit(player);
            }
        }
    }
}
