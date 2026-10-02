package org.agmas.noellesroles.role.qust.roles.super_doctor;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.agmas.noellesroles.role.qust.QUSTRoles;

/**
 * 超级医生悔改之枪 C2S 数据包。
 * <p>
 * 客户端使用悔改之枪时发送，携带目标实体 ID（-1 表示未命中）。
 */
public record SuperDoctorPayload(int targetId) implements CustomPacketPayload {

    public static final Type<SuperDoctorPayload> TYPE = new Type<>(QUSTRoles.id("super_doctor_gun"));
    public static final StreamCodec<ByteBuf, SuperDoctorPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.INT, SuperDoctorPayload::targetId, SuperDoctorPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
