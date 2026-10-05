package org.agmas.noellesroles.role.qust.roles.wanderer;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.role.qust.QUSTRoles;

/**
 * 游荡者网络包
 */
public class WandererPayload {

    /** S2C: 通知客户端进入/退出灵魂出窍 */
    public record SoulOutState(boolean active) implements CustomPacketPayload {
        public static final Type<SoulOutState> TYPE = new Type<>(QUSTRoles.id("wanderer_qust_soul_out"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SoulOutState> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, SoulOutState::active, SoulOutState::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: 同步幽灵显隐状态 */
    public record GhostVisibility(boolean visible) implements CustomPacketPayload {
        public static final Type<GhostVisibility> TYPE = new Type<>(QUSTRoles.id("wanderer_qust_ghost_vis"));
        public static final StreamCodec<RegistryFriendlyByteBuf, GhostVisibility> CODEC =
                StreamCodec.composite(ByteBufCodecs.BOOL, GhostVisibility::visible, GhostVisibility::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: 通知客户端进入幽灵状态（死亡后） */
    public record EnterGhostState() implements CustomPacketPayload {
        public static final Type<EnterGhostState> TYPE = new Type<>(QUSTRoles.id("wanderer_qust_enter_ghost"));
        public static final StreamCodec<RegistryFriendlyByteBuf, EnterGhostState> CODEC =
                StreamCodec.unit(new EnterGhostState());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** C2S: 请求切换幽灵显隐 */
    public record ToggleGhostVisibility() implements CustomPacketPayload {
        public static final Type<ToggleGhostVisibility> TYPE = new Type<>(QUSTRoles.id("wanderer_qust_toggle_vis"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ToggleGhostVisibility> CODEC =
                StreamCodec.unit(new ToggleGhostVisibility());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** S2C: 通知客户端显示死亡进入幽灵状态的大字提示 */
    public record DeathNotification() implements CustomPacketPayload {
        public static final Type<DeathNotification> TYPE = new Type<>(QUSTRoles.id("wanderer_qust_death_notify"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DeathNotification> CODEC =
                StreamCodec.unit(new DeathNotification());
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
