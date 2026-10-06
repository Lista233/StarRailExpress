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

package org.agmas.noellesroles.content.block;

import com.google.common.collect.Maps;
import com.mojang.serialization.MapCodec;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import io.wifi.starrailexpress.content.block_entity.MinigameQuestBlockEntity;
import io.wifi.starrailexpress.content.minigame.QuestMinigames;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * 随机小游戏任务点镶板
 * 与 MinigameQuestPanelBlock 类似，但交互时从小游戏池随机抽取一个小游戏进行游玩。
 * 用于「游玩小游戏」场景任务。
 */
public class RandomMinigameQuestPanelBlock extends BaseEntityBlock
        implements TaskInstinctShowableInterface, SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final int TASK_INSTINCT_ID = 27;

    private static final VoxelShape UP_SHAPE = box(0.0, 15.9, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape DOWN_SHAPE = box(0.0, 0.0, 0.0, 16.0, 0.1, 16.0);
    private static final VoxelShape EAST_SHAPE = box(0.0, 0.0, 0.0, 0.1, 16.0, 16.0);
    private static final VoxelShape WEST_SHAPE = box(15.9, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SOUTH_SHAPE = box(0.0, 0.0, 0.0, 16.0, 16.0, 0.1);
    private static final VoxelShape NORTH_SHAPE = box(0.0, 0.0, 15.9, 16.0, 16.0, 16.0);
    private static final Map<Direction, VoxelShape> SHAPES = Util.make(Maps.newEnumMap(Direction.class), shapes -> {
        shapes.put(Direction.NORTH, NORTH_SHAPE);
        shapes.put(Direction.EAST, EAST_SHAPE);
        shapes.put(Direction.SOUTH, SOUTH_SHAPE);
        shapes.put(Direction.WEST, WEST_SHAPE);
        shapes.put(Direction.UP, DOWN_SHAPE);
        shapes.put(Direction.DOWN, UP_SHAPE);
    });

    public RandomMinigameQuestPanelBlock(Properties settings) {
        super(settings.noOcclusion().noCollission());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return SHAPES.getOrDefault(state.getValue(FACING), Shapes.block());
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.INVISIBLE;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter world, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter world, BlockPos pos) {
        return true;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player,
            BlockHitResult hit) {
        if (world.isClientSide)
            return InteractionResult.SUCCESS;

        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof MinigameQuestBlockEntity questBe) {
            if (player instanceof ServerPlayer sp) {
                // 检查冷却
                if (SREGameWorldComponent.KEY.get(sp.level()).isRunning() && isBlockOnCooldown(sp, pos)) {
                    sp.displayClientMessage(
                            net.minecraft.network.chat.Component.translatable("message.sre.minigame_cooldown"),
                            true);
                    return InteractionResult.SUCCESS;
                }
                // 从小游戏池随机选取一个小游戏
                String randomId = pickRandomMinigame(sp);
                if (randomId != null) {
                    startBlockCooldown(sp, pos);
                    net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(sp,
                            new io.wifi.starrailexpress.network.MinigameQuestPayload.OpenGame(pos, randomId));
                }
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Nullable
    private static String pickRandomMinigame(ServerPlayer sp) {
        var areas = io.wifi.starrailexpress.cca.AreasWorldComponent.KEY.get(sp.level());
        List<String> ids;
        if (areas != null && !areas.availableMinigameIds.isEmpty()) {
            ids = new java.util.ArrayList<>(areas.availableMinigameIds);
        } else {
            ids = QuestMinigames.getAll().stream().map(mg -> mg.id()).collect(java.util.stream.Collectors.toList());
        }
        if (ids.isEmpty())
            return null;
        return ids.get(sp.getRandom().nextInt(ids.size()));
    }

    private static boolean isBlockOnCooldown(ServerPlayer sp, BlockPos pos) {
        var mgComp = SREPlayerMinigameTaskComponent.KEY.get(sp);
        return mgComp != null && mgComp.isBlockUsed(pos);
    }

    private static void startBlockCooldown(ServerPlayer sp, BlockPos pos) {
        var mgComp = SREPlayerMinigameTaskComponent.KEY.get(sp);
        if (mgComp != null) {
            mgComp.startBlockCooldown(pos);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        MinigameQuestBlockEntity be = new MinigameQuestBlockEntity(pos, state);
        be.setRandom(true);
        return be;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state,
            BlockEntityType<T> type) {
        return createTickerHelper(type, TMMBlockEntities.MINIGAME_QUEST,
                (lvl, pos, s, be) -> {
                });
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getClickedFace();
        FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return this.defaultBlockState().setValue(FACING, facing)
                .setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader world, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        return Block.canSupportCenter(world, pos.relative(facing.getOpposite()), facing);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world,
            BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED))
            world.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(world));
        Direction facing = state.getValue(FACING);
        if (direction == facing.getOpposite() && !state.canSurvive(world, pos))
            return Blocks.AIR.defaultBlockState();
        return state;
    }

    // ══════════════════════════════════════════
    // 任务路标接口
    // ══════════════════════════════════════════

    @Override
    public int taskInstinctId() {
        return TASK_INSTINCT_ID;
    }

    @Override
    public boolean shouldRenderTaskInstinct(Level level, BlockState state, BlockPos pos, Player player) {
        if (!(level.getBlockEntity(pos) instanceof MinigameQuestBlockEntity questBe)) {
            return false;
        }
        if (questBe.isSabotageTrigger()) {
            return false;
        }
        if (!SREGameWorldComponent.KEY.get(level).isRunning()) {
            return false;
        }
        var mgComp = SREPlayerMinigameTaskComponent.KEY.get(player);
        return mgComp == null || !mgComp.isBlockUsed(pos);
    }

    @Override
    public Color taskInstinctRenderColor(BlockState state, BlockPos pos, Player player) {
        Level level = player.level();
        if (level != null) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MinigameQuestBlockEntity questBe) {
                int c = questBe.getMarkerColor();
                return new Color(c);
            }
        }
        return new Color(255, 215, 0); // 金色
    }
}
