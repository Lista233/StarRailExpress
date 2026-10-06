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

import com.mojang.serialization.MapCodec;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMinigameTaskComponent;
import io.wifi.starrailexpress.content.block.api.TaskInstinctShowableInterface;
import io.wifi.starrailexpress.content.block_entity.MinigameQuestBlockEntity;
import io.wifi.starrailexpress.content.minigame.QuestMinigames;
import io.wifi.starrailexpress.index.TMMBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.util.List;

/**
 * 随机小游戏任务点方块
 * 与 MinigameQuestBlock 类似，但交互时从小游戏池随机抽取一个小游戏进行游玩。
 * 用于「游玩小游戏」场景任务。
 */
public class RandomMinigameQuestBlock extends BaseEntityBlock
        implements TaskInstinctShowableInterface, SimpleWaterloggedBlock {

    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final int TASK_INSTINCT_ID = 26;

    public RandomMinigameQuestBlock(Properties settings) {
        super(settings.noOcclusion().noCollission());
        this.registerDefaultState(this.stateDefinition.any().setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return null;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
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

    /** 从小游戏池中随机选取一个小游戏 ID。优先从地图可用池选取，兜底从全注册表选取。 */
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

    /** 检查该方块对本玩家是否在复用冷却中。 */
    private static boolean isBlockOnCooldown(ServerPlayer sp, BlockPos pos) {
        var mgComp = SREPlayerMinigameTaskComponent.KEY.get(sp);
        return mgComp != null && mgComp.isBlockUsed(pos);
    }

    /** 开始复用冷却。 */
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
                    /* 无需tick */ });
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(WATERLOGGED);
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        FluidState fluidState = ctx.getLevel().getFluidState(ctx.getClickedPos());
        return this.defaultBlockState().setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
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
        // 随机小游戏方块：游戏运行中 + 该点未冷却 → 显示金色透视
        if (!(level.getBlockEntity(pos) instanceof MinigameQuestBlockEntity questBe)) {
            return false;
        }
        if (questBe.isSabotageTrigger()) {
            return false; // 随机方块不支持破坏任务触发
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
