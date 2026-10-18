package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.game.command_block.CommandBlockThrownEntity;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;

/**
 * 命令方块投掷物。石头和火球沿用默认投掷物渲染；
 * 石枪模型本身沿 +X 横放，投出后要把枪尖转到飞行方向，不能继续横着飞。
 */
public class CommandBlockThrownRenderer extends EntityRenderer<CommandBlockThrownEntity> {
    /** 模型枪身沿 +X，转到箭矢局部 +Y（飞行方向）需要绕 Z 再转 90°。 */
    private static final float SPEAR_ALIGN_DEGREES = 90.0F;
    private static final float SPEAR_SCALE = 1.15F;

    private final ItemRenderer itemRenderer;
    private final ThrownItemRenderer<CommandBlockThrownEntity> thrownItemRenderer;

    public CommandBlockThrownRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.itemRenderer = context.getItemRenderer();
        this.thrownItemRenderer = new ThrownItemRenderer<>(context);
        this.shadowRadius = 0.0F;
    }

    @Override
    public void render(CommandBlockThrownEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(ModEffects.TIME_STOP)
                && !TimeStopEffect.clientCanMovePlayers.contains(player.getUUID())) {
            return;
        }
        if (entity.kind() != CommandBlockThrownEntity.SPEAR) {
            this.thrownItemRenderer.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
            return;
        }
        ItemStack stack = entity.getItem();
        if (stack.isEmpty()) {
            stack = ModItems.STONE_SPEAR.getDefaultInstance();
        }
        float yaw = entity.renderYaw(partialTick);
        float pitch = entity.renderPitch(partialTick);
        poseStack.pushPose();
        // 箭矢约定：绕 Y、再绕 Z 后，局部 +Y 指向飞行方向。模型枪身沿 +X，再绕 Z 90° 把枪尖转过去。
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw - 90.0F));
        poseStack.mulPose(Axis.ZP.rotationDegrees(pitch + SPEAR_ALIGN_DEGREES));
        poseStack.scale(SPEAR_SCALE, SPEAR_SCALE, SPEAR_SCALE);
        this.itemRenderer.renderStatic(stack, ItemDisplayContext.FIXED, packedLight, OverlayTexture.NO_OVERLAY,
                poseStack, bufferSource, entity.level(), entity.getId());
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(CommandBlockThrownEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
