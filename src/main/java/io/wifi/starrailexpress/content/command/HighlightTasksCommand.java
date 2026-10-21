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

package io.wifi.starrailexpress.content.command;

import com.mojang.brigadier.CommandDispatcher;
import io.wifi.starrailexpress.content.block.FoodPlatterBlock;
import io.wifi.starrailexpress.content.block_entity.PlateTrayBlockEntity;
import io.wifi.starrailexpress.content.task.TaskPointColors;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.network.packet.TaskHighlightS2CPacket;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.agmas.noellesroles.content.block.VendingMachinesBlock;
import org.agmas.noellesroles.init.ModBlocks;
import org.agmas.noellesroles.utils.MapScanner;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * /tmm:highlightTasks 系列指令：
 * <ul>
 * <li>{@code <from> <to>} — 只高亮区域内装好的食物盘、饮料盘、自动售货机与讲台；</li>
 * <li>{@code all [<from> <to>]} — 直接显示服务端已扫描出的全部任务点（可限定区域），游戏未开始也能用；</li>
 * <li>{@code off} 或不带参数 — 关闭。</li>
 * </ul>
 *
 * <p>颜色取自 {@link TaskPointColors}，与任务透视完全同源。
 */
public class HighlightTasksCommand {
    private static final int MAX_VOLUME = 2_000_000;
    private static final int MAX_PACKET_POINTS = 1024;
    private static final int MAX_CHAT_LINES = 50;

    /** 与 {@code MapScanner} 写入 {@code GameUtils.taskBlocks} 的类型值保持一致。 */
    private static final int TYPE_FOOD = 1;
    private static final int TYPE_DRINK = 2;
    private static final int TYPE_LECTERN = 6;
    private static final int TYPE_VENDING = 11;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:highlightTasks")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> disable(context.getSource()))
                        .then(Commands.literal("off")
                                .executes(context -> disable(context.getSource())))
                        .then(Commands.literal("all")
                                .executes(context -> showAll(context.getSource(), null))
                                .then(Commands.argument("from", BlockPosArgument.blockPos())
                                        .then(Commands.argument("to", BlockPosArgument.blockPos())
                                                .executes(context -> showAll(context.getSource(),
                                                        Bounds.of(
                                                                BlockPosArgument.getBlockPos(context, "from"),
                                                                BlockPosArgument.getBlockPos(context, "to")))))))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(context -> highlightArea(context.getSource(),
                                                BlockPosArgument.getBlockPos(context, "from"),
                                                BlockPosArgument.getBlockPos(context, "to"))))));
    }

    private static int disable(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            ServerPlayNetworking.send(player, new TaskHighlightS2CPacket(false, List.of()));
        }
        source.sendSuccess(() -> Component.translatable("commands.sre.highlighttasks.off")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    // ───────────────────────── 区域扫描 ─────────────────────────

    private static int highlightArea(CommandSourceStack source, BlockPos from, BlockPos to) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.sre.highlighttasks.player_only"));
            return 0;
        }

        Bounds bounds = Bounds.of(from, to);
        if (bounds.volume() > MAX_VOLUME) {
            source.sendFailure(Component.translatable("commands.sre.highlighttasks.too_large", bounds.volume(),
                    MAX_VOLUME));
            return 0;
        }
        BlockPos min = bounds.min();
        BlockPos max = bounds.max();

        ServerLevel level = source.getLevel();
        List<FoundPoint> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            int type = classify(state);
            if (type == 0) {
                continue;
            }
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            boolean empty = isEmptyContainer(level, pos, state, type);
            found.add(new FoundPoint(new TaskHighlightS2CPacket.Entry(pos.immutable(), type, empty), blockId));
        }

        found.sort(Comparator.comparingDouble(point -> point.entry().pos().distSqr(player.blockPosition())));

        if (found.isEmpty()) {
            ServerPlayNetworking.send(player, new TaskHighlightS2CPacket(false, List.of()));
            source.sendSuccess(() -> Component.translatable("commands.sre.highlighttasks.none")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        List<TaskHighlightS2CPacket.Entry> packetPoints = new ArrayList<>();
        for (int i = 0; i < found.size() && i < MAX_PACKET_POINTS; i++) {
            packetPoints.add(found.get(i).entry());
        }
        ServerPlayNetworking.send(player, new TaskHighlightS2CPacket(true, packetPoints));

        sendAreaReport(source, found, min, max);
        return found.size();
    }

    /**
     * 只认本指令负责的三类任务点。
     *
     * @return {@code GameUtils.taskBlocks} 中的类型值；不是这三类则返回 {@code 0}
     */
    private static int classify(BlockState state) {
        if (state.is(ModBlocks.VENDING_MACHINES_BLOCK)) {
            // 双格方块：上下半格都会被遍历到，只登记下半格避免重复高亮。
            return state.getValue(VendingMachinesBlock.HALF) == DoubleBlockHalf.LOWER ? TYPE_VENDING : 0;
        }
        if (state.getBlock() instanceof FoodPlatterBlock) {
            // DrinkTrayBlock 继承自 FoodPlatterBlock，必须先判饮料盘。
            return state.is(TMMBlocks.DRINK_TRAY) ? TYPE_DRINK : TYPE_FOOD;
        }
        if (state.getBlock() instanceof LecternBlock) {
            // 地图制作阶段书可能还没放上去，这里不看 HAS_BOOK：所有讲台都高亮。
            return TYPE_LECTERN;
        }
        return 0;
    }

    /**
     * 容器是否为空：食物盘 / 饮料盘看方块实体里是否存有物品，讲台看是否放了书。
     */
    private static boolean isEmptyContainer(ServerLevel level, BlockPos pos, BlockState state, int type) {
        return switch (type) {
            case TYPE_FOOD, TYPE_DRINK ->
                    level.getBlockEntity(pos) instanceof PlateTrayBlockEntity plate && plate.getStoredItems().isEmpty();
            case TYPE_LECTERN -> !state.getValue(LecternBlock.HAS_BOOK);
            default -> false;
        };
    }

    private static void sendAreaReport(CommandSourceStack source, List<FoundPoint> points,
            BlockPos min, BlockPos max) {
        int food = 0;
        int drink = 0;
        int vending = 0;
        int lectern = 0;
        int empty = 0;
        for (FoundPoint point : points) {
            switch (point.entry().type()) {
                case TYPE_FOOD -> food++;
                case TYPE_DRINK -> drink++;
                case TYPE_VENDING -> vending++;
                case TYPE_LECTERN -> lectern++;
                default -> {
                }
            }
            if (point.entry().empty()) {
                empty++;
            }
        }

        Component header = Component.translatable("commands.sre.highlighttasks.header",
                formatPos(min), formatPos(max), points.size(), food, drink, vending, lectern)
                .withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> header, false);

        int emptyCount = empty;
        if (emptyCount > 0) {
            Component emptyLine = Component.translatable("commands.sre.highlighttasks.empty_hint", emptyCount)
                    .withStyle(ChatFormatting.LIGHT_PURPLE);
            source.sendSuccess(() -> emptyLine, false);
        }

        int shown = Math.min(points.size(), MAX_CHAT_LINES);
        for (int i = 0; i < shown; i++) {
            Component line = buildLine(points.get(i));
            source.sendSuccess(() -> line, false);
        }
        if (points.size() > shown) {
            int remaining = points.size() - shown;
            source.sendSuccess(() -> Component.translatable("commands.sre.highlighttasks.more", remaining)
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    // ───────────────────────── 全部任务点 ─────────────────────────

    /**
     * 显示全部任务点。
     *
     * @param bounds 只显示该区域内的任务点；{@code null} 表示整张地图
     */
    private static int showAll(CommandSourceStack source, Bounds bounds) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.sre.highlighttasks.player_only"));
            return 0;
        }

        if (bounds != null && bounds.volume() > MAX_VOLUME) {
            source.sendFailure(Component.translatable("commands.sre.highlighttasks.too_large", bounds.volume(),
                    MAX_VOLUME));
            return 0;
        }

        // 地图还没扫描过（例如刚切图、还没开过游戏）时先补扫一次，否则没有数据可显示。
        if (GameUtils.taskBlocks.isEmpty()) {
            MapScanner.scanAllTaskBlocks(source.getLevel());
        }
        if (GameUtils.taskBlocks.isEmpty()) {
            ServerPlayNetworking.send(player, new TaskHighlightS2CPacket(false, List.of()));
            source.sendSuccess(() -> Component.translatable("commands.sre.highlighttasks.all.empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        List<Map.Entry<BlockPos, Integer>> all = new ArrayList<>();
        for (Map.Entry<BlockPos, Integer> entry : GameUtils.taskBlocks.entrySet()) {
            if (bounds != null && !bounds.contains(entry.getKey())) {
                continue;
            }
            all.add(entry);
        }
        if (all.isEmpty()) {
            ServerPlayNetworking.send(player, new TaskHighlightS2CPacket(false, List.of()));
            source.sendSuccess(() -> Component.translatable("commands.sre.highlighttasks.all.area_empty")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        all.sort(Comparator.comparingDouble(point -> point.getKey().distSqr(player.blockPosition())));

        int total = all.size();
        int shown = Math.min(total, TaskHighlightS2CPacket.MAX_POINTS);
        List<TaskHighlightS2CPacket.Entry> packetPoints = new ArrayList<>(shown);
        int food = 0;
        int drink = 0;
        int vending = 0;
        for (int i = 0; i < total; i++) {
            Map.Entry<BlockPos, Integer> point = all.get(i);
            int type = point.getValue();
            switch (type) {
                case TYPE_FOOD -> food++;
                case TYPE_DRINK -> drink++;
                case TYPE_VENDING -> vending++;
                default -> {
                }
            }
            if (i < shown) {
                packetPoints.add(new TaskHighlightS2CPacket.Entry(point.getKey(), type, false));
            }
        }
        ServerPlayNetworking.send(player, new TaskHighlightS2CPacket(true, packetPoints));

        int other = total - food - drink - vending;
        // lambda 只能捕获 effectively final 变量，统计值在循环里自增过，这里另存一份。
        int totalCount = total;
        int foodCount = food;
        int drinkCount = drink;
        int vendingCount = vending;
        int otherCount = other;
        source.sendSuccess(() -> Component.translatable("commands.sre.highlighttasks.all.header",
                totalCount, foodCount, drinkCount, vendingCount, otherCount).withStyle(ChatFormatting.GOLD), false);
        if (bounds != null) {
            source.sendSuccess(() -> Component.translatable("commands.sre.highlighttasks.all.area",
                    formatPos(bounds.min()), formatPos(bounds.max())).withStyle(ChatFormatting.GRAY), false);
        }
        if (total > shown) {
            source.sendSuccess(() -> Component.translatable("commands.sre.highlighttasks.all.truncated", shown)
                    .withStyle(ChatFormatting.GRAY), false);
        }
        return shown;
    }

    // ───────────────────────── 聊天输出 ─────────────────────────

    private static Component buildLine(FoundPoint point) {
        BlockPos pos = point.entry().pos();
        int type = point.entry().type();
        int rgb = point.entry().empty() ? TaskPointColors.EMPTY_CONTAINER : TaskPointColors.rgb(type);
        int color = rgb == TaskPointColors.UNDEFINED ? 0xFFFFFF : rgb;
        // 自动售货机占两格，传送到 y+2 才不会卡在方块里。
        int height = type == TYPE_VENDING ? 2 : 1;
        String tpCommand = String.format(Locale.ROOT, "/tp @s %.1f %.1f %.1f",
                pos.getX() + 0.5D, pos.getY() + height, pos.getZ() + 0.5D);

        Component hover = Component.translatable("commands.sre.highlighttasks.tp_hover")
                .append("\n")
                .append(Component.translatable("commands.sre.highlighttasks.id_hover", point.blockId()));

        Component coords = Component.literal("[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")
                .withStyle(style -> style.withColor(color).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));

        MutableComponent kind = kindText(type).withStyle(style -> style.withColor(color));
        if (point.entry().empty()) {
            // 空容器额外标注，避免只靠颜色区分。
            kind.append(Component.translatable("commands.sre.highlighttasks.empty")
                    .withStyle(style -> style.withColor(color)));
        }

        return Component.empty()
                .append(coords)
                .append(Component.literal(" "))
                .append(kind);
    }

    private static MutableComponent kindText(int type) {
        String key = switch (type) {
            case TYPE_DRINK -> "commands.sre.highlighttasks.kind.drink";
            case TYPE_LECTERN -> "commands.sre.highlighttasks.kind.lectern";
            case TYPE_VENDING -> "commands.sre.highlighttasks.kind.vending";
            default -> "commands.sre.highlighttasks.kind.food";
        };
        return Component.translatable(key);
    }

    private static Component formatPos(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    /** 两个角点规范化后的闭区间区域。 */
    private record Bounds(BlockPos min, BlockPos max) {
        static Bounds of(BlockPos from, BlockPos to) {
            return new Bounds(
                    new BlockPos(Math.min(from.getX(), to.getX()),
                            Math.min(from.getY(), to.getY()),
                            Math.min(from.getZ(), to.getZ())),
                    new BlockPos(Math.max(from.getX(), to.getX()),
                            Math.max(from.getY(), to.getY()),
                            Math.max(from.getZ(), to.getZ())));
        }

        /** 区域方块数；极端坐标下乘法溢出按“过大”处理，避免绕过体积限制。 */
        long volume() {
            long dx = (long) max.getX() - min.getX() + 1;
            long dy = (long) max.getY() - min.getY() + 1;
            long dz = (long) max.getZ() - min.getZ() + 1;
            try {
                return Math.multiplyExact(Math.multiplyExact(dx, dy), dz);
            } catch (ArithmeticException exception) {
                return Long.MAX_VALUE;
            }
        }

        boolean contains(BlockPos pos) {
            return pos.getX() >= min.getX() && pos.getX() <= max.getX()
                    && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ();
        }
    }

    private record FoundPoint(TaskHighlightS2CPacket.Entry entry, String blockId) {
    }
}
