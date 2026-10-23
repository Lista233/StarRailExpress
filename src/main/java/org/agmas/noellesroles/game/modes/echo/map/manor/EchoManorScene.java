package org.agmas.noellesroles.game.modes.echo.map.manor;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import org.agmas.noellesroles.game.modes.echo.map.manor.EchoManorLayout.Kind;

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

    /** 布局种类 → 方块状态；同一坐标永远得到同一个状态。 */
    public static BlockState state(Kind kind, int x, int y, int z) {
        int h = hash(x, y, z);
        return switch (kind) {
            case AIR -> Blocks.AIR.defaultBlockState();
            case STONE -> (h < 70 ? Blocks.STONE : h < 85 ? Blocks.ANDESITE : Blocks.DEEPSLATE).defaultBlockState();
            case DIRT -> (h < 80 ? Blocks.DIRT : Blocks.COARSE_DIRT).defaultBlockState();
            case GRASS -> Blocks.GRASS_BLOCK.defaultBlockState();
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
            case ANVIL -> Blocks.ANVIL.defaultBlockState();
            case SMITHING -> Blocks.SMITHING_TABLE.defaultBlockState();
            case CRATE -> Blocks.CRAFTING_TABLE.defaultBlockState();
            case DARKSTONE -> Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            case MARKER_CRYSTAL -> Blocks.AMETHYST_BLOCK.defaultBlockState();
            case MARKER_BEACON -> Blocks.LODESTONE.defaultBlockState();
            case MARKER_ALTAR -> Blocks.CHISELED_QUARTZ_BLOCK.defaultBlockState();
            case MARKER_CRATE -> Blocks.CRAFTING_TABLE.defaultBlockState();
            case MARKER_BLACK_CRATE -> Blocks.SMITHING_TABLE.defaultBlockState();
            case MARKER_BLACK_NODE -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
            case MARKER_BLACK_DROPOFF -> Blocks.ANCIENT_DEBRIS.defaultBlockState();
            case MARKER_BROKER -> Blocks.GOLD_BLOCK.defaultBlockState();
            case MARKER_FREE_EXIT -> Blocks.REDSTONE_LAMP.defaultBlockState();
            case PANEL -> Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
        };
    }

    /** 生成时统一使用的写块标记：不触发邻居更新，避免流体/重力/红石级联。 */
    public static final int WRITE_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
}
