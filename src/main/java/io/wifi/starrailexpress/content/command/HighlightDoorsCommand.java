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
import io.wifi.starrailexpress.content.block.SmallDoorBlock;
import io.wifi.starrailexpress.content.block_entity.SmallDoorBlockEntity;
import io.wifi.starrailexpress.index.TMMBlocks;
import io.wifi.starrailexpress.network.packet.DoorHighlightS2CPacket;
import io.wifi.starrailexpress.util.KeyNameUtils;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * /tmm:highlightDoors &lt;from&gt; &lt;to&gt; [room|all]
 * 只把扫描结果发给执行者：默认只找住宿用小木门，{@code all} 才包含玻璃门等其他门。
 *
 * <p>颜色按开门条件区分：需要钥匙/开锁器的门为红色，无需开锁器的门为绿色。
 */
public class HighlightDoorsCommand {
    private static final int MAX_VOLUME = 2_000_000;
    private static final int MAX_PACKET_DOORS = 1024;
    private static final int MAX_CHAT_LINES = 50;
    /** 需要钥匙或开锁器才能打开。 */
    private static final int BOUND_COLOR = 0xFF0000;
    /** 无需开锁器，空手即可打开。 */
    private static final int UNBOUND_COLOR = 0x00FF00;

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("tmm:highlightDoors")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> disable(context.getSource()))
                        .then(Commands.literal("off")
                                .executes(context -> disable(context.getSource())))
                        .then(Commands.argument("from", BlockPosArgument.blockPos())
                                .then(Commands.argument("to", BlockPosArgument.blockPos())
                                        .executes(context -> highlight(context.getSource(),
                                                BlockPosArgument.getBlockPos(context, "from"),
                                                BlockPosArgument.getBlockPos(context, "to"), false))
                                        .then(Commands.literal("room")
                                                .executes(context -> highlight(context.getSource(),
                                                        BlockPosArgument.getBlockPos(context, "from"),
                                                        BlockPosArgument.getBlockPos(context, "to"), false)))
                                        .then(Commands.literal("all")
                                                .executes(context -> highlight(context.getSource(),
                                                        BlockPosArgument.getBlockPos(context, "from"),
                                                        BlockPosArgument.getBlockPos(context, "to"), true))))));
    }

    private static int disable(CommandSourceStack source) {
        ServerPlayer player = source.getPlayer();
        if (player != null) {
            ServerPlayNetworking.send(player, new DoorHighlightS2CPacket(false, List.of()));
        }
        source.sendSuccess(() -> Component.translatable("commands.sre.highlightdoors.off")
                .withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static int highlight(CommandSourceStack source, BlockPos from, BlockPos to, boolean all) {
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("commands.sre.highlightdoors.player_only"));
            return 0;
        }

        BlockPos min = new BlockPos(
                Math.min(from.getX(), to.getX()),
                Math.min(from.getY(), to.getY()),
                Math.min(from.getZ(), to.getZ()));
        BlockPos max = new BlockPos(
                Math.max(from.getX(), to.getX()),
                Math.max(from.getY(), to.getY()),
                Math.max(from.getZ(), to.getZ()));

        long dx = (long) max.getX() - min.getX() + 1;
        long dy = (long) max.getY() - min.getY() + 1;
        long dz = (long) max.getZ() - min.getZ() + 1;
        long volume;
        try {
            volume = Math.multiplyExact(Math.multiplyExact(dx, dy), dz);
        } catch (ArithmeticException exception) {
            // 极端坐标下乘法溢出时按“过大”处理，避免绕过体积限制。
            volume = Long.MAX_VALUE;
        }
        if (volume > MAX_VOLUME) {
            source.sendFailure(Component.translatable("commands.sre.highlightdoors.too_large", volume, MAX_VOLUME));
            return 0;
        }

        ServerLevel level = source.getLevel();
        List<FoundDoor> found = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof SmallDoorBlock)) {
                continue;
            }
            if (!state.hasProperty(SmallDoorBlock.HALF)
                    || state.getValue(SmallDoorBlock.HALF) != DoubleBlockHalf.LOWER) {
                continue;
            }
            if (!all && !isRoomDoor(state)) {
                continue;
            }
            if (!(level.getBlockEntity(pos) instanceof SmallDoorBlockEntity door)) {
                continue;
            }

            String roomName = KeyNameUtils.baseKeyName(door.getKeyName());
            if (roomName.length() > 80) {
                // 防止自定义超长房间名撑爆网络包。
                roomName = roomName.substring(0, 80);
            }
            boolean bound = !roomName.isEmpty();
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            found.add(new FoundDoor(new DoorHighlightS2CPacket.Entry(pos.immutable(), bound, roomName), blockId));
        }

        found.sort(Comparator.comparingDouble(door -> door.entry().pos().distSqr(player.blockPosition())));

        if (found.isEmpty()) {
            ServerPlayNetworking.send(player, new DoorHighlightS2CPacket(false, List.of()));
            source.sendSuccess(() -> Component.translatable("commands.sre.highlightdoors.none")
                    .withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }

        List<DoorHighlightS2CPacket.Entry> packetDoors = new ArrayList<>();
        for (int i = 0; i < found.size() && i < MAX_PACKET_DOORS; i++) {
            packetDoors.add(found.get(i).entry());
        }
        ServerPlayNetworking.send(player, new DoorHighlightS2CPacket(true, packetDoors));

        sendReport(source, found, min, max, all);
        return found.size();
    }

    private static void sendReport(CommandSourceStack source, List<FoundDoor> doors,
            BlockPos min, BlockPos max, boolean all) {
        long bound = doors.stream().filter(door -> door.entry().bound()).count();
        Component mode = Component.translatable(all
                ? "commands.sre.highlightdoors.mode.all"
                : "commands.sre.highlightdoors.mode.room");
        Component header = Component.translatable("commands.sre.highlightdoors.header",
                formatPos(min), formatPos(max), mode,
                doors.size(), bound, doors.size() - bound)
                .withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> header, false);

        int shown = Math.min(doors.size(), MAX_CHAT_LINES);
        for (int i = 0; i < shown; i++) {
            Component line = buildDoorLine(doors.get(i));
            source.sendSuccess(() -> line, false);
        }
        if (doors.size() > shown) {
            int remaining = doors.size() - shown;
            source.sendSuccess(() -> Component.translatable("commands.sre.highlightdoors.more", remaining)
                    .withStyle(ChatFormatting.GRAY), false);
        }
    }

    private static Component buildDoorLine(FoundDoor door) {
        BlockPos pos = door.entry().pos();
        int color = door.entry().bound() ? BOUND_COLOR : UNBOUND_COLOR;
        String tpCommand = String.format(Locale.ROOT, "/tp @s %.1f %.1f %.1f",
                pos.getX() + 0.5D, pos.getY() + 2.0D, pos.getZ() + 0.5D);

        Component hover = Component.translatable("commands.sre.highlightdoors.tp_hover")
                .append("\n")
                .append(Component.translatable("commands.sre.highlightdoors.id_hover", door.blockId()))
                .append("\n")
                .append(statusText(door.entry()));

        Component coords = Component.literal("[" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + "]")
                .withStyle(style -> style.withColor(color).withUnderlined(true)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, tpCommand))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));

        return Component.empty()
                .append(coords)
                .append(Component.literal(" "))
                .append(statusText(door.entry()).withStyle(style -> style.withColor(color)));
    }

    private static MutableComponent statusText(DoorHighlightS2CPacket.Entry entry) {
        if (entry.bound()) {
            return Component.translatable("commands.sre.highlightdoors.status.bound", entry.roomName());
        }
        return Component.translatable("commands.sre.highlightdoors.status.unbound");
    }

    private static Component formatPos(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    /** 默认只找住宿用小木门；玻璃门等其他门需要显式加 {@code all}。 */
    private static boolean isRoomDoor(BlockState state) {
        return state.is(TMMBlocks.SMALL_WOOD_DOOR);
    }

    private record FoundDoor(DoorHighlightS2CPacket.Entry entry, String blockId) {
    }
}
