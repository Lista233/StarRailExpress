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

package io.wifi.starrailexpress.network.packet;

import io.wifi.starrailexpress.SRE;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 门高亮结果：只发给执行指令的玩家，不写入全局任务点数据。
 */
public record DoorHighlightS2CPacket(boolean enabled, List<Entry> doors) implements CustomPacketPayload {
    public static final Type<DoorHighlightS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "door_highlight"));
    public static final StreamCodec<FriendlyByteBuf, DoorHighlightS2CPacket> CODEC = StreamCodec
            .ofMember(DoorHighlightS2CPacket::write, DoorHighlightS2CPacket::read);

    private static final int MAX_DOORS = 4096;

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.enabled);
        buf.writeVarInt(this.doors.size());
        for (Entry door : this.doors) {
            buf.writeBlockPos(door.pos());
            buf.writeBoolean(door.bound());
            buf.writeUtf(door.roomName(), 256);
        }
    }

    public static DoorHighlightS2CPacket read(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_DOORS) {
            throw new IllegalArgumentException("Invalid door highlight size: " + size);
        }
        List<Entry> doors = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            BlockPos pos = buf.readBlockPos();
            boolean bound = buf.readBoolean();
            String roomName = buf.readUtf(256);
            doors.add(new Entry(pos, bound, roomName));
        }
        return new DoorHighlightS2CPacket(enabled, doors);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /**
     * @param pos      门下半格坐标
     * @param bound    扫描时是否已绑定钥匙
     * @param roomName 去掉 locked/alarmed/reinforced 前缀后的房间名
     */
    public record Entry(BlockPos pos, boolean bound, String roomName) {
    }
}
