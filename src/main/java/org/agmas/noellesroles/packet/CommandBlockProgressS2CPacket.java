package org.agmas.noellesroles.packet;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.Noellesroles;

/** 命令方块破坏进度。goal 为 0 时客户端把条藏起来。 */
public record CommandBlockProgressS2CPacket(int progress, int goal) implements CustomPacketPayload {
    public static final Type<CommandBlockProgressS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(Noellesroles.MOD_ID, "command_block_progress"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CommandBlockProgressS2CPacket> CODEC = StreamCodec
            .ofMember(CommandBlockProgressS2CPacket::write, CommandBlockProgressS2CPacket::read);

    private void write(FriendlyByteBuf buf) {
        buf.writeVarInt(Math.max(0, progress));
        buf.writeVarInt(Math.max(0, goal));
    }

    private static CommandBlockProgressS2CPacket read(FriendlyByteBuf buf) {
        return new CommandBlockProgressS2CPacket(buf.readVarInt(), buf.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}
