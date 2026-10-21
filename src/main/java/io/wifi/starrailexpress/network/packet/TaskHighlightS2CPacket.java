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
 * 指令任务点高亮结果：只发给执行指令的玩家，不写入全局任务点数据。
 */
public record TaskHighlightS2CPacket(boolean enabled, List<Entry> points) implements CustomPacketPayload {
    public static final Type<TaskHighlightS2CPacket> ID = new Type<>(
            ResourceLocation.fromNamespaceAndPath(SRE.MOD_ID, "task_highlight"));
    public static final StreamCodec<FriendlyByteBuf, TaskHighlightS2CPacket> CODEC = StreamCodec
            .ofMember(TaskHighlightS2CPacket::write, TaskHighlightS2CPacket::read);

    /** 单包最多携带的任务点数，服务端指令分页/截断时也按这个上限来。 */
    public static final int MAX_POINTS = 4096;

    public void write(FriendlyByteBuf buf) {
        buf.writeBoolean(this.enabled);
        buf.writeVarInt(this.points.size());
        for (Entry point : this.points) {
            buf.writeBlockPos(point.pos());
            buf.writeVarInt(point.type());
            buf.writeBoolean(point.empty());
        }
    }

    public static TaskHighlightS2CPacket read(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_POINTS) {
            throw new IllegalArgumentException("Invalid task highlight size: " + size);
        }
        List<Entry> points = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            BlockPos pos = buf.readBlockPos();
            int type = buf.readVarInt();
            boolean empty = buf.readBoolean();
            points.add(new Entry(pos, type, empty));
        }
        return new TaskHighlightS2CPacket(enabled, points);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }

    /**
     * @param pos   任务点方块坐标
     * @param type  任务点类型，与 {@code MapScanner} 写入 {@code GameUtils.taskBlocks} 的值一致
     * @param empty 是否为空容器（未放物品的食物盘/饮料盘、未放书的讲台）
     */
    public record Entry(BlockPos pos, int type, boolean empty) {
    }
}
