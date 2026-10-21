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

package io.wifi.starrailexpress.client.door;

import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.network.packet.DoorHighlightS2CPacket;
import io.wifi.starrailexpress.util.KeyNameUtils;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;

import java.awt.Color;
import java.util.List;

/**
 * 指令门高亮的客户端状态：只保存服务端发给自己的那批门，不写入地图任务点。
 */
public final class DoorHighlightClient {
    /** 需要钥匙或开锁器才能打开。 */
    private static final Color BOUND_COLOR = new Color(0xFF0000);
    /** 无需开锁器，空手即可打开。 */
    private static final Color UNBOUND_COLOR = new Color(0x00FF00);
    /** 未加载区块时无法读方块形状，用普通门 1x2 的外框兜底。 */
    private static final AABB DOOR_FALLBACK = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 2.0D, 1.0D);

    private static boolean enabled;
    private static List<DoorHighlightS2CPacket.Entry> doors = List.of();
    private static ClientLevel lastLevel;

    private DoorHighlightClient() {
    }

    public static void receive(DoorHighlightS2CPacket packet) {
        enabled = packet.enabled();
        doors = packet.enabled() ? List.copyOf(packet.doors()) : List.of();
        lastLevel = Minecraft.getInstance().level;
    }

    public static void clear() {
        enabled = false;
        doors = List.of();
        lastLevel = null;
    }

    public static void render(WorldRenderContext context) {
        if (!enabled) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        ClientLevel level = client.level;
        if (level == null) {
            clear();
            return;
        }
        if (lastLevel == null) {
            lastLevel = level;
        } else if (lastLevel != level) {
            // 切维度/重连后不残留上一张地图的门。
            clear();
            return;
        }

        for (DoorHighlightS2CPacket.Entry door : doors) {
            BlockPos pos = door.pos();
            boolean bound = door.bound();

            // 已加载区块以方块实体为准，这样门被绑定/解锁后颜色会立即变化。
            if (level.isLoaded(pos)) {
                if (!(level.getBlockState(pos).getBlock() instanceof SmallDoorBlock)) {
                    continue;
                }
                BlockEntity blockEntity = level.getBlockEntity(pos);
                if (blockEntity instanceof SmallDoorBlockEntity doorEntity) {
                    bound = KeyNameUtils.isBound(doorEntity.getKeyName());
                }
            }

            TaskBlockOverlayRenderer.renderBlockOverlay(context, pos, DOOR_FALLBACK,
                    bound ? BOUND_COLOR : UNBOUND_COLOR, 1.0F, true);
        }
    }
}
