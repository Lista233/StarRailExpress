package org.agmas.noellesroles.game.modes.echo.map.manor;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachineBlock;
import org.agmas.noellesroles.game.modes.echo.content.EchoMachines;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Kind;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Layout;
import org.jetbrains.annotations.Nullable;

/**
 * 庄园场景的方块映射：把 {@link EchoManorLayout.Kind} 翻成实际 {@link BlockState}。
 *
 * <p>只使用整格方块，且刻意避开所有方块实体（箱子/熔炉/信标/讲台/床/旗帜…）：
 * 模板整块复制只搬方块状态，带方块实体的方块复制后容易留下空壳或残留旧 NBT，
 * 是「鬼畜冲突」的常见来源。这里也不需要邻居更新，所有装饰都是静止方块。
 *
 * <p>同一坐标的纹理变化由坐标哈希决定，因此每次生成结果完全一致，便于比对与复现。
 */
public final class EchoManorScene {
    private EchoManorScene() { }

    private static int hash(int x, int y, int z) {
        return Math.floorMod(x * 31 + y * 17 + z * 57, 100);
    }

    /** 仅供显式生成命令使用；根据布局点位区分核心信标和撤离面板，不迁移已有地图。 */
    public static BlockState state(Layout layout, int x, int y, int z) {
        EchoMachines.Kind machine = machineKind(layout, x, y, z);
        if (machine != null) {
            return EchoMachines.block(machine).defaultBlockState()
                    .setValue(EchoMachineBlock.FACING, machineFacing(layout, x, y, z));
        }
        return sceneryState(layout.at(x, y, z), x, y, z);
    }

    /** 点位到机器种类的纯映射，不触发机器注册，便于离线校验布局。 */
    @Nullable
    public static EchoMachines.Kind machineKind(Layout layout, int x, int y, int z) {
        return switch (layout.at(x, y, z)) {
            case MARKER_CRYSTAL -> EchoMachines.Kind.CRYSTAL;
            case MARKER_BEACON -> layout.beacons().stream().anyMatch(beacon ->
                    beacon.x() == x && beacon.z() == z && beacon.region().equals("core"))
                    ? EchoMachines.Kind.CORE_BEACON : EchoMachines.Kind.BEACON;
            case MARKER_ALTAR -> EchoMachines.Kind.ALTAR;
            case MARKER_CRATE -> EchoMachines.Kind.SUPPLY;
            case MARKER_BLACK_CRATE -> EchoMachines.Kind.BLACK_MARKET;
            case MARKER_BLACK_NODE -> EchoMachines.Kind.BLACK_NODE;
            case MARKER_BLACK_DROPOFF -> EchoMachines.Kind.BLACK_DROPOFF;
            case MARKER_BROKER -> EchoMachines.Kind.BROKER_EXIT;
            case MARKER_FREE_EXIT -> EchoMachines.Kind.FREE_EXIT;
            case PANEL -> x == layout.evacControl().x() && z == layout.evacControl().z()
                    ? EchoMachines.Kind.EVACUATION_CONSOLE : EchoMachines.Kind.CORE_CONSOLE;
            default -> null;
        };
    }

    /** 墙上面板朝门外走廊；独立机器优先朝相邻脚部、头部均畅通的站立格。 */
    public static Direction machineFacing(Layout layout, int x, int y, int z) {
        if (layout.at(x, y, z) == Kind.PANEL) {
            // 撤离面板朝西；核心面板一律朝门外（背离金库内部），用门体所在侧判断。
            if (x == layout.evacControl().x() && z == layout.evacControl().z()) return Direction.WEST;
            return x < layout.coreArea().minX() ? Direction.WEST : Direction.EAST;
        }
        for (Direction direction : new Direction[]{Direction.SOUTH, Direction.NORTH, Direction.WEST, Direction.EAST}) {
            int nx = x + direction.getStepX(), nz = z + direction.getStepZ();
            if (layout.solid(nx, EchoManorLayout.GROUND_Y, nz)
                    && !layout.solid(nx, EchoManorLayout.FEET_Y, nz)
                    && !layout.solid(nx, EchoManorLayout.FEET_Y + 1, nz)) return direction;
        }
        return Direction.NORTH;
    }

    /** 建筑方块保持原映射与完整碰撞，门体仍为铁块。 */
    private static BlockState sceneryState(Kind kind, int x, int y, int z) {
        int h = hash(x, y, z);
        return switch (kind) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case STONE -> (h < 70 ? Blocks.STONE : h < 85 ? Blocks.ANDESITE : Blocks.DEEPSLATE).defaultBlockState();
            case DIRT -> (h < 80 ? Blocks.DIRT : Blocks.COARSE_DIRT).defaultBlockState();
            case GRASS -> (h < 88 ? Blocks.GRASS_BLOCK : Blocks.MOSS_BLOCK).defaultBlockState();
            case PATH -> (h < 78 ? Blocks.STONE_BRICKS : h < 90 ? Blocks.MOSSY_STONE_BRICKS
                    : Blocks.CHISELED_STONE_BRICKS).defaultBlockState();
            case FLOOR -> (x % 4 == 0 || z % 4 == 0 ? Blocks.STRIPPED_SPRUCE_LOG : Blocks.SPRUCE_PLANKS).defaultBlockState();
            case WALL -> (h < 85 ? Blocks.STONE_BRICKS : Blocks.CRACKED_STONE_BRICKS).defaultBlockState();
            case GATE -> Blocks.IRON_BLOCK.defaultBlockState();
            case PILLAR -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
            case GLASS -> Blocks.GLASS.defaultBlockState();
            case ROOF -> Blocks.DARK_OAK_PLANKS.defaultBlockState();
            case ROOF_DARK -> Blocks.DEEPSLATE_TILES.defaultBlockState();
            case LAMP -> Blocks.SEA_LANTERN.defaultBlockState();
            case LOG -> Blocks.OAK_LOG.defaultBlockState();
            case LEAVES -> Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
            case CONTAINER -> (h < 34 ? Blocks.RED_CONCRETE : h < 67 ? Blocks.BLUE_CONCRETE
                    : Blocks.ORANGE_CONCRETE).defaultBlockState();
            case TABLE -> Blocks.OAK_PLANKS.defaultBlockState();
            case COUNTER -> Blocks.SMOOTH_STONE.defaultBlockState();
            case BOOKSHELF -> Blocks.BOOKSHELF.defaultBlockState();
            case CRATE -> Blocks.CRAFTING_TABLE.defaultBlockState();
            case DARKSTONE -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            // 假山/台地/硬地：全部整格方块，无方块实体、无重力、无流体。
            case ROCK -> (h < 55 ? Blocks.STONE : h < 80 ? Blocks.ANDESITE : Blocks.TUFF).defaultBlockState();
            case MOSS -> (h < 60 ? Blocks.MOSS_BLOCK : Blocks.MOSSY_COBBLESTONE).defaultBlockState();
            case CONCRETE -> (h < 70 ? Blocks.SMOOTH_STONE : h < 88 ? Blocks.POLISHED_ANDESITE
                    : Blocks.STONE_BRICKS).defaultBlockState();
            case PLATE -> (h < 80 ? Blocks.POLISHED_ANDESITE : Blocks.POLISHED_DEEPSLATE).defaultBlockState();
            case TERRACE -> (h < 85 ? Blocks.SMOOTH_STONE : Blocks.POLISHED_ANDESITE).defaultBlockState();
            case TERRACE_WALL -> (h < 75 ? Blocks.STONE_BRICKS : Blocks.MOSSY_STONE_BRICKS).defaultBlockState();
            case MARKER_CRYSTAL, MARKER_BEACON, MARKER_ALTAR, MARKER_CRATE, MARKER_BLACK_CRATE,
                    MARKER_BLACK_NODE, MARKER_BLACK_DROPOFF, MARKER_BROKER, MARKER_FREE_EXIT, PANEL ->
                    throw new IllegalArgumentException("机器点位必须通过布局映射: " + kind);
        };
    }

    /** 生成时统一使用的写块标记：不触发邻居更新，避免流体/重力/红石级联。 */
    public static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
}
