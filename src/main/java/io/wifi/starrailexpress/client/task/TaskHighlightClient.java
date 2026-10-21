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

package io.wifi.starrailexpress.client.task;

import io.wifi.starrailexpress.content.block.FoodPlatterBlock;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import io.wifi.starrailexpress.content.task.TaskPointColors;
import io.wifi.starrailexpress.network.packet.TaskHighlightS2CPacket;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;

import java.awt.Color;
import java.util.List;

/**
 * 指令任务点高亮的客户端状态：只保存服务端发给自己的那批点，不写入地图任务点。
 *
 * <p>与任务透视不同，这里不检查游戏是否开始、本能开关或死亡惩罚，
 * 因此地图制作阶段（游戏未开始）也能直接看到全部任务点。
 * 颜色取自 {@link TaskBlockOverlayRenderer#taskTypeColor(int)}，与任务透视完全一致。
 */
public final class TaskHighlightClient {
    /** 类型表未收录时的兜底颜色。 */
    private static final Color DEFAULT_COLOR = Color.WHITE;
    /** 空容器（未放物品的食物盘/饮料盘、未放书的讲台）的专用标记色。 */
    private static final Color EMPTY_COLOR = new Color(TaskPointColors.EMPTY_CONTAINER);
    /** 未加载区块时无法读方块形状，用整格外框兜底。 */
    private static final AABB POINT_FALLBACK = new AABB(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D);

    private static boolean enabled;
    private static List<TaskHighlightS2CPacket.Entry> points = List.of();
    private static ClientLevel lastLevel;

    private TaskHighlightClient() {
    }

    public static void receive(TaskHighlightS2CPacket packet) {
        enabled = packet.enabled();
        points = packet.enabled() ? List.copyOf(packet.points()) : List.of();
        lastLevel = Minecraft.getInstance().level;
    }

    public static void clear() {
        enabled = false;
        points = List.of();
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
            // 切维度/重连后不残留上一张地图的任务点。
            clear();
            return;
        }

        LocalPlayer player = client.player;
        for (TaskHighlightS2CPacket.Entry point : points) {
            BlockPos pos = point.pos();
            Color color = null;
            float alpha = 1.0F;
            boolean empty = point.empty();

            // 已加载区块以方块为准：方块被拆掉后不再画，自定义任务点也能拿到自己的颜色，
            // 容器是否为空也按当前状态实时刷新。
            if (level.isLoaded(pos)) {
                BlockState state = level.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                if (state.getBlock() instanceof LecternBlock) {
                    empty = !state.getValue(LecternBlock.HAS_BOOK);
                } else if (state.getBlock() instanceof FoodPlatterBlock
                        && level.getBlockEntity(pos) instanceof PlateTrayBlockEntity plate) {
                    empty = plate.getStoredItems().isEmpty();
                }
                if (empty) {
                    // 空容器优先用专用色，不再被动态颜色覆盖。
                    color = EMPTY_COLOR;
                } else if (player != null && state.getBlock() instanceof TaskInstinctShowableInterface showable) {
                    color = showable.taskInstinctRenderColor(state, pos, player);
                    if (color != null) {
                        alpha = color.getAlpha() / 255f;
                    }
                }
            } else if (empty) {
                // 未加载区块无法实时判定，用服务端扫描时的快照。
                color = EMPTY_COLOR;
            }

            if (color == null) {
                color = TaskBlockOverlayRenderer.taskTypeColor(point.type());
            }
            if (color == null) {
                color = DEFAULT_COLOR;
            }

            TaskBlockOverlayRenderer.renderBlockOverlay(context, pos, POINT_FALLBACK, color, alpha, true);
        }
    }
}
