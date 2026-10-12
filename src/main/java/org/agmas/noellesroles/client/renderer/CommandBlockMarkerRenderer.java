package org.agmas.noellesroles.client.renderer;

import org.agmas.noellesroles.content.entity.CommandBlockMarkerEntity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** 天上的命令方块：全亮方块模型，本地旋转，不走方块展示。 */
public class CommandBlockMarkerRenderer extends EntityRenderer<CommandBlockMarkerEntity> {
    private static final int FULL_BRIGHT = 0x00F000F0;
    private final BlockRenderDispatcher blocks;

    public CommandBlockMarkerRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(CommandBlockMarkerEntity entity, float yaw, float partialTick, PoseStack matrices,
            MultiBufferSource buffers, int light) {
        int stage = entity.getStage();
        float size = 8.0F - stage;
        float spin = (entity.tickCount + partialTick) * 1.72F;
        matrices.pushPose();
        matrices.translate(0.0F, 4.0F, 0.0F);
        matrices.mulPose(Axis.YP.rotationDegrees(spin));
        matrices.scale(size, size, size);
        matrices.translate(-0.5F, -0.5F, -0.5F);
        this.blocks.renderSingleBlock(stageBlock(stage), matrices, buffers, FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        matrices.popPose();
        super.render(entity, yaw, partialTick, matrices, buffers, light);
    }

    private static BlockState stageBlock(int stage) {
        return switch (stage) {
            case 1 -> Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
            case 2 -> Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
            case 3 -> Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
            default -> Blocks.COMMAND_BLOCK.defaultBlockState();
        };
    }

    @Override
    public ResourceLocation getTextureLocation(CommandBlockMarkerEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
