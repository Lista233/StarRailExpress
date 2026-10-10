package org.agmas.noellesroles.role.qust.roles.aimlabs;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

/**
 * Aimlabs 计次模式方块：默认以 COUNT 模式运行。
 * <p>
 * 击中指定次数靶标后自动结束，显示用时。
 */
public class AimlabsCountBlock extends AimlabsBlock {

    public static final MapCodec<AimlabsCountBlock> CODEC = simpleCodec(AimlabsCountBlock::new);

    @Override
    protected MapCodec<? extends net.minecraft.world.level.block.BaseEntityBlock> codec() {
        return CODEC;
    }

    public AimlabsCountBlock(Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        AimlabsBlockEntity be = new AimlabsBlockEntity(pos, state);
        be.mode = AimlabsBlockEntity.GameMode.COUNT;
        return be;
    }
}
