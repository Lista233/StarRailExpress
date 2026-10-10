package org.agmas.noellesroles.role.qust.roles.aimlabs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.agmas.noellesroles.init.ModBlocks;

/**
 * Aimlabs 方块实体：存储每个方块的独立配置。
 */
public class AimlabsBlockEntity extends BlockEntity {

    /** 游戏模式。 */
    public enum GameMode {
        /** 计时模式：限定时间内尽量多得分。 */
        TIMED,
        /** 计次模式：计算击中指定次数所需时间。 */
        COUNT
    }

    // ── 可配置参数（带默认值） ──
    public GameMode mode = GameMode.TIMED;
    public int countdownSeconds = 30;
    public int targetHits = 15; // 计次模式目标击中次数
    public int areaWidth = 6;
    public int areaHeight = 4;
    public float sphereRadius = 0.375f; // 0.75格直径 → 0.375半径
    public int maxSpheres = 4;
    public int heightOffset = 1; // 最低一行方块上方1格
    /** 靶标展示面朝向（玩家交互时的水平朝向，SOUTH/NORTH/EAST/WEST）。 */
    public Direction facing = Direction.SOUTH;
    /** 上一次红石信号状态（用于检测脉冲边沿）。 */
    public boolean lastPowered = false;

    public AimlabsBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.AIMLABS_BLOCK_ENTITY, pos, state);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Mode", mode.name());
        tag.putInt("Countdown", countdownSeconds);
        tag.putInt("TargetHits", targetHits);
        tag.putInt("AreaWidth", areaWidth);
        tag.putInt("AreaHeight", areaHeight);
        tag.putFloat("SphereRadius", sphereRadius);
        tag.putInt("MaxSpheres", maxSpheres);
        tag.putInt("HeightOffset", heightOffset);
        tag.putInt("Facing", facing.get2DDataValue());
        tag.putBoolean("LastPowered", lastPowered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Mode")) {
            try { mode = GameMode.valueOf(tag.getString("Mode")); } catch (Exception ignored) {}
        }
        if (tag.contains("Countdown")) countdownSeconds = tag.getInt("Countdown");
        if (tag.contains("TargetHits")) targetHits = tag.getInt("TargetHits");
        if (tag.contains("AreaWidth")) areaWidth = tag.getInt("AreaWidth");
        if (tag.contains("AreaHeight")) areaHeight = tag.getInt("AreaHeight");
        if (tag.contains("SphereRadius")) sphereRadius = tag.getFloat("SphereRadius");
        if (tag.contains("MaxSpheres")) maxSpheres = tag.getInt("MaxSpheres");
        if (tag.contains("HeightOffset")) heightOffset = tag.getInt("HeightOffset");
        if (tag.contains("Facing")) facing = Direction.from2DDataValue(tag.getInt("Facing"));
        if (tag.contains("LastPowered")) lastPowered = tag.getBoolean("LastPowered");
    }
}
