package org.agmas.noellesroles.role.qust.roles.aimlabs;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Aimlabs 靶标实体渲染器：绘制一个半透明天蓝色球体。
 * <p>
 * 使用原版标靶方块贴图（minecraft:target）作为球体贴图。
 */
public class AimlabsTargetRenderer extends EntityRenderer<AimlabsTargetEntity> {

    private static final ResourceLocation TEXTURE = ResourceLocation.withDefaultNamespace("block/target_side");

    public AimlabsTargetRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public ResourceLocation getTextureLocation(AimlabsTargetEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(AimlabsTargetEntity entity, float yaw, float tickDelta, PoseStack poseStack,
            MultiBufferSource bufferSource, int light) {
        float radius = entity.getSphereRadius();
        if (radius <= 0) return;

        poseStack.pushPose();
        // 不旋转，保持静态

        VertexConsumer consumer = bufferSource.getBuffer(RenderType.entityTranslucent(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();

        // 绘制球体（经纬线方式）
        int slices = 16;
        int stacks = 12;

        for (int i = 0; i < stacks; i++) {
            float v0 = (float) i / stacks;
            float v1 = (float) (i + 1) / stacks;
            float theta0 = v0 * Mth.PI;
            float theta1 = v1 * Mth.PI;

            for (int j = 0; j < slices; j++) {
                float u0 = (float) j / slices;
                float u1 = (float) (j + 1) / slices;
                float phi0 = u0 * Mth.TWO_PI;
                float phi1 = u1 * Mth.TWO_PI;

                // 4个顶点
                float x0 = radius * Mth.sin(theta0) * Mth.cos(phi0);
                float y0 = radius * Mth.cos(theta0);
                float z0 = radius * Mth.sin(theta0) * Mth.sin(phi0);

                float x1 = radius * Mth.sin(theta0) * Mth.cos(phi1);
                float y1 = radius * Mth.cos(theta0);
                float z1 = radius * Mth.sin(theta0) * Mth.sin(phi1);

                float x2 = radius * Mth.sin(theta1) * Mth.cos(phi1);
                float y2 = radius * Mth.cos(theta1);
                float z2 = radius * Mth.sin(theta1) * Mth.sin(phi1);

                float x3 = radius * Mth.sin(theta1) * Mth.cos(phi0);
                float y3 = radius * Mth.cos(theta1);
                float z3 = radius * Mth.sin(theta1) * Mth.sin(phi0);

                // UV
                float uv0 = u0;
                float uv1 = v0;
                float uv2 = u1;
                float uv3 = v1;

                // 法线（简化为顶点方向归一化）
                float len0 = Mth.sqrt(x0 * x0 + y0 * y0 + z0 * z0);
                float len1 = Mth.sqrt(x1 * x1 + y1 * y1 + z1 * z1);
                float len2 = Mth.sqrt(x2 * x2 + y2 * y2 + z2 * z2);
                float len3 = Mth.sqrt(x3 * x3 + y3 * y3 + z3 * z3);

                // 颜色：天蓝色
                int r = 135;
                int g = 206;
                int b = 235;
                int a = 200;

                // 三角形1
                addVertex(consumer, matrix, x0, y0, z0, uv0, uv1, light, r, g, b, a,
                        len0 > 0 ? x0 / len0 : 0, len0 > 0 ? y0 / len0 : 1, len0 > 0 ? z0 / len0 : 0);
                addVertex(consumer, matrix, x1, y1, z1, uv2, uv1, light, r, g, b, a,
                        len1 > 0 ? x1 / len1 : 0, len1 > 0 ? y1 / len1 : 1, len1 > 0 ? z1 / len1 : 0);
                addVertex(consumer, matrix, x2, y2, z2, uv2, uv3, light, r, g, b, a,
                        len2 > 0 ? x2 / len2 : 0, len2 > 0 ? y2 / len2 : 1, len2 > 0 ? z2 / len2 : 0);

                // 三角形2
                addVertex(consumer, matrix, x0, y0, z0, uv0, uv1, light, r, g, b, a,
                        len0 > 0 ? x0 / len0 : 0, len0 > 0 ? y0 / len0 : 1, len0 > 0 ? z0 / len0 : 0);
                addVertex(consumer, matrix, x2, y2, z2, uv2, uv3, light, r, g, b, a,
                        len2 > 0 ? x2 / len2 : 0, len2 > 0 ? y2 / len2 : 1, len2 > 0 ? z2 / len2 : 0);
                addVertex(consumer, matrix, x3, y3, z3, uv0, uv3, light, r, g, b, a,
                        len3 > 0 ? x3 / len3 : 0, len3 > 0 ? y3 / len3 : 1, len3 > 0 ? z3 / len3 : 0);
            }
        }

        poseStack.popPose();
    }

    private static void addVertex(VertexConsumer consumer, Matrix4f matrix,
            float x, float y, float z, float u, float v, int light,
            int r, int g, int b, int a, float nx, float ny, float nz) {
        consumer.addVertex(matrix, x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(light)
                .setNormal(nx, ny, nz);
    }
}
