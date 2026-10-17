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

package org.agmas.noellesroles.client.renderer;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.UUID;

import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.BambooSpearEntity;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 竹刀渲染：整根竹杆<b>压在持有者的视线射线上</b>，朝向恒等于持有者的当前朝向。
 * <p>
 * <b>起点 = 眼睛沿视线前推一点（与服务端共用的 {@link BambooSpearEntity#poleBase}），
 * 方向 = 视线</b>。因为杆根就落在视线上，画出来的这条杆与枪械射线
 * （{@code ProjectileUtil#getHitResultOnViewVector}）是同一条轨道：
 * 第一人称杆身正压在准星上笔直往前长，第三人称也是从身前朝准星方向刺出去，
 * 不会再出现「杆被平移到视线一侧、近处偏得厉害」的错位。
 * 手臂姿态由 {@code HumanoidModelBambooSpearMixin} 抬到视线高度配合。
 * <p>
 * 眼睛与视线按下面四层取，顺序不能调：
 * <ol>
 * <li><b>本地持有者</b>：第一人称用相机视线（零帧延迟），第三人称用玩家自己的视线向量
 * （相机在前视角下是反的，不能直接拿来当视线）。</li>
 * <li><b>别的玩家在用</b>：取<b>他本人</b>在本客户端上的眼睛 / 视线。这样竹杆是贴着他长出来的，
 * 指的就是他自己准星的方向（也就是他那边真正伸出、真正结算伤害的那条射线）；
 * 用服务端快照的话，画面上的玩家模型和快照的眼睛位置会差出网络延迟那一段，看着像杆子飘在脸外面。</li>
 * <li><b>兜底一</b>：持有者不在这边的客户端里（超出追踪范围）时，退回服务端每 tick 同步的
 * 权威眼睛 / 视线，并在客户端按 partialTick 插值，否则会看到 20Hz 的跳动。</li>
 * <li><b>兜底二</b>：发射瞬间的实体朝向（同步还没到位的第一帧）。</li>
 * </ol>
 */
public class BambooSpearRenderer extends EntityRenderer<BambooSpearEntity> {

    private static final float RADIUS = 0.08F;
    private static final float TIP_LENGTH = 0.38F;
    private static final float MIN_LENGTH = 0.08F;

    public BambooSpearRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(BambooSpearEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer viewer = minecraft.player;
        if (viewer != null && viewer.hasEffect(ModEffects.TIME_STOP)
                && !TimeStopEffect.clientCanMovePlayers.contains(viewer.getUUID())) {
            return;
        }

        float length = entity.getInterpolatedLength(partialTick);
        UUID ownerUuid = entity.getOwnerUuid();
        Player owner = entity.getOwner();

        Vec3 eye = null;
        Vec3 view = null;
        if (viewer != null && ownerUuid != null && ownerUuid.equals(viewer.getUUID())) {
            eye = viewer.getEyePosition(partialTick);
            if (minecraft.options.getCameraType().isFirstPerson()) {
                Vector3f look = minecraft.gameRenderer.getMainCamera().getLookVector();
                view = new Vec3(look.x(), look.y(), look.z());
            } else {
                // 第三人称前视角的相机朝向是反的，只能用玩家本体的视线。
                view = viewer.getViewVector(partialTick);
            }
        } else if (owner != null) {
            // 别的玩家在用竹枪：竹杆必须从「他自己的眼睛 → 他自己的视线」这条射线长出来，
            // 也就是画面上这个玩家此时此刻准星所指的方向——两边看到的是同一根杆。
            eye = owner.getEyePosition(partialTick);
            view = owner.getViewVector(partialTick);
        }
        if (eye == null || view == null || view.lengthSqr() < 1.0e-6) {
            // 持有者不在这边的客户端里：退回服务端每 tick 同步的权威眼睛 / 视线（已按 partialTick 插值）。
            eye = entity.getInterpolatedEye(partialTick);
            view = entity.getInterpolatedDir(partialTick);
        }
        if (eye == null || view == null || view.lengthSqr() < 1.0e-6) {
            float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            eye = entity.getPosition(partialTick);
            view = Vec3.directionFromRotation(pitch, yaw);
        }
        view = view.normalize();

        // 起点 = 杆根（服务端结算用的是同一个 poleBase，都在视线上），方向 = 视线方向。
        Vec3 origin = BambooSpearEntity.poleBase(eye, view);
        // length 本身就是「从杆根沿视线量出去的杆长」，服务端也是这么算命中点的。
        float drawLength = Math.max(MIN_LENGTH, length);

        Vec3 entityPos = entity.getPosition(partialTick);
        poseStack.pushPose();
        poseStack.translate(origin.x - entityPos.x, origin.y - entityPos.y, origin.z - entityPos.z);
        BambooPoleGeometry.orient(poseStack, view);
        BambooPoleGeometry.render(poseStack, bufferSource, packedLight, 0.0F, drawLength, RADIUS, TIP_LENGTH);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(BambooSpearEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
