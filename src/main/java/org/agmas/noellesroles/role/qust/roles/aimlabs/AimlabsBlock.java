package org.agmas.noellesroles.role.qust.roles.aimlabs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.BlockBehaviour;
import com.mojang.serialization.MapCodec;
import org.agmas.noellesroles.init.ModBlocks;
import org.jetbrains.annotations.Nullable;

/**
 * Aimlabs 练枪方块：玩家右键交互或红石信号触发练枪训练。
 * <p>
 * 支持两种模式：计时模式（TIMED）和计次模式（COUNT）。
 * 练习中再次右键或红石脉冲可提前结束。
 */
public class AimlabsBlock extends BaseEntityBlock {

    public static final MapCodec<AimlabsBlock> CODEC = simpleCodec(AimlabsBlock::new);

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public AimlabsBlock(Properties properties) {
        super(properties.noOcclusion().noCollission().strength(-1.0F, 3600000.0F));
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
        if (!(level instanceof ServerLevel serverLevel)) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AimlabsBlockEntity aimBe)) return InteractionResult.SUCCESS;

        // 如果该玩家已有活跃会话，右键提前结束
        AimlabsSession existingSession = AimlabsSession.getPlayerSession(sp);
        if (existingSession != null) {
            existingSession.end();
            sp.displayClientMessage(Component.literal("\u00a7e\u7ec3\u4e60\u5df2\u63d0\u524d\u7ed3\u675f\uff01"), true);
            return InteractionResult.SUCCESS;
        }

        // 捕获玩家水平朝向
        Direction playerFacing = sp.getDirection();
        aimBe.facing = playerFacing;
        aimBe.setChanged();

        startSession(sp, pos, serverLevel, aimBe, playerFacing);
        return InteractionResult.SUCCESS;
    }

    /** 启动会话的公共方法（供指令/红石调用）。 */
    public static void startSession(ServerPlayer sp, BlockPos pos, ServerLevel serverLevel,
            AimlabsBlockEntity aimBe, Direction facing) {
        // 检测游戏是否正在进行中，使用不同的默认尺寸
        boolean gameRunning = isGameRunning(serverLevel);
        int areaWidth = gameRunning ? AimlabsCommand.GAME_AREA_WIDTH : aimBe.areaWidth;
        int areaHeight = gameRunning ? AimlabsCommand.GAME_AREA_HEIGHT : aimBe.areaHeight;

        AimlabsSession session = new AimlabsSession(
                sp, pos, serverLevel,
                aimBe.mode,
                aimBe.countdownSeconds,
                aimBe.targetHits,
                areaWidth,
                areaHeight,
                aimBe.sphereRadius,
                aimBe.maxSpheres,
                aimBe.heightOffset,
                facing);
        session.start();
        AimlabsSession.giveOrRemovePracticeRevolver(sp, true);

        String modeText = (aimBe.mode == AimlabsBlockEntity.GameMode.COUNT)
                ? "\u00a7a\u8ba1\u6b21\u7ec3\u4e60\u5f00\u59cb\uff01\u51fb\u4e2d " + aimBe.targetHits + " \u6b21\u5373\u53ef\u5b8c\u6210\uff01"
                : "\u00a7a\u8ba1\u65f6\u7ec3\u4e60\u5f00\u59cb\uff01\u5c04\u51fb\u5706\u7403\u5f97\u5206\uff01";
        sp.displayClientMessage(Component.literal(modeText), true);
    }

    /** 检测 SRE 游戏对局是否正在进行中。 */
    private static boolean isGameRunning(ServerLevel level) {
        var gameWorld = io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level);
        return gameWorld != null && gameWorld.isRunning();
    }

    /** 红石信号变化：脉冲触发/关闭会话。 */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
            BlockPos neighborPos, boolean movedByPiston) {
        if (level.isClientSide) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof AimlabsBlockEntity aimBe)) return;

        boolean powered = level.hasNeighborSignal(pos);
        if (powered == aimBe.lastPowered) return; // 无变化
        aimBe.lastPowered = powered;

        if (powered) {
            // 上升沿：检查是否有活跃会话
            var sessions = AimlabsSession.getSessionsByBlock(pos);
            if (!sessions.isEmpty()) {
                // 有会话 → 结束所有
                for (AimlabsSession s : sessions) {
                    s.end();
                }
            } else {
                // 无会话 → 找最近玩家启动
                ServerPlayer nearest = (ServerPlayer) serverLevel.getNearestPlayer(
                        pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        16.0, p -> !p.isSpectator());
                if (nearest != null && !AimlabsSession.hasPlayerSession(nearest)) {
                    startSession(nearest, pos, serverLevel, aimBe, aimBe.facing);
                }
            }
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide) {
            for (AimlabsSession session : AimlabsSession.getSessionsByBlock(pos)) {
                session.end();
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AimlabsBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, pos, s, be) -> {
            if (be instanceof AimlabsBlockEntity) {
                AimlabsSession.tickAllForBlock(pos);
            }
        };
    }
}
