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
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.UUID;

import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.BambooSpearEntity;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 竹刀渲染：整根竹杆<b>从持有者手里长出来</b>，杆尖正好落在服务端结算出的命中点上。
 * <p>
 * 与「矛」的渲染思路一致——视觉锚点取手部而不是眼睛，所以第一人称看着像从手上刺出去，
 * 第三人称（其他玩家 / 自己按 F5）也是从那只手伸出去的，而不是从脑门飘出来。手臂姿态由
 * {@code HumanoidModelBambooSpearMixin} 抬起来配合。
 * <p>
 * 方向与长度的来源分三层，顺序不能调：
 * <ol>
 * <li><b>本地持有者</b>：第一人称用相机视线（零帧延迟），第三人称用玩家自己的视线向量
 * （相机在前视角下是反的，不能直接拿来当视线）。</li>
 * <li><b>其他玩家</b>：服务端每 tick 同步的权威眼睛 / 视线，并在客户端按 partialTick 插值，
 * 否则会看到 20Hz 的跳动。命中判定就是用这组数据结算的，所以视觉与伤害一致。</li>
 * <li><b>兜底</b>：发射瞬间的实体朝向（同步还没到位的第一帧）。</li>
 * </ol>
 */
public class BambooSpearRenderer extends EntityRenderer<BambooSpearEntity> {

    private static final float RADIUS = 0.08F;
    private static final float TIP_LENGTH = 0.38F;
    private static final float MIN_LENGTH = 0.08F;

    /**
     * 手部锚点相对眼睛的偏移。数值对着人形模型量出来的：手臂枢轴在 y≈1.375、x≈±0.3125，
     * 手臂前伸时掌心落在身体中心前方约 0.75 格，这里取略小的 0.6 让竹杆看着是「握住」而不是浮在指尖外。
     */
    private static final double HAND_DROP = -0.25;
    private static final double HAND_SIDE = 0.32;
    private static final double HAND_FORWARD = 0.60;
    /** 第一人称额外前推，免得杆根被近裁剪面切掉。 */
    private static final double FIRST_PERSON_PUSH = 0.35;
    /** 杆长短于这个值时不按「锚点→命中点」求方向，否则短杆期方向会大幅歪向侧面。 */
    private static final double DIRECTION_MIN_SPAN = 0.35;

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

        float length = Math.max(MIN_LENGTH, entity.getInterpolatedLength(partialTick));
        UUID ownerUuid = entity.getOwnerUuid();
        boolean isLocalOwner = viewer != null && ownerUuid != null && ownerUuid.equals(viewer.getUUID());
        Player owner = entity.getOwner();

        Vec3 eye = null;
        Vec3 view = null;
        if (isLocalOwner) {
            eye = viewer.getEyePosition(partialTick);
            if (minecraft.options.getCameraType().isFirstPerson()) {
                Vector3f look = minecraft.gameRenderer.getMainCamera().getLookVector();
                view = new Vec3(look.x(), look.y(), look.z());
            } else {
                // 第三人称前视角的相机朝向是反的，只能用玩家本体的视线。
                view = viewer.getViewVector(partialTick);
            }
        } else {
            eye = entity.getInterpolatedEye(partialTick);
            view = entity.getInterpolatedDir(partialTick);
            if ((eye == null || view == null) && owner != null) {
                eye = owner.getEyePosition(partialTick);
                view = owner.getViewVector(partialTick);
            }
        }
        if (eye == null || view == null || view.lengthSqr() < 1.0e-6) {
            float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            eye = entity.getPosition(partialTick);
            view = Vec3.directionFromRotation(pitch, yaw);
        }
        view = view.normalize();

        Vec3 origin = handAnchor(eye, view, owner, isLocalOwner && minecraft.options.getCameraType().isFirstPerson());
        // 杆尖必须落在命中点上（服务端是从眼睛沿视线结算的），所以方向取「手 → 命中点」。
        Vec3 tip = eye.add(view.scale(length));
        Vec3 span = tip.subtract(origin);
        double spanLength = span.length();
        Vec3 direction;
        float drawLength;
        if (spanLength < DIRECTION_MIN_SPAN) {
            direction = view;
            drawLength = MIN_LENGTH;
        } else {
            direction = span.scale(1.0 / spanLength);
            drawLength = (float) spanLength;
        }

        Vec3 entityPos = entity.getPosition(partialTick);
        poseStack.pushPose();
        poseStack.translate(origin.x - entityPos.x, origin.y - entityPos.y, origin.z - entityPos.z);
        BambooPoleGeometry.orient(poseStack, direction);
        BambooPoleGeometry.render(poseStack, bufferSource, packedLight, 0.0F, drawLength, RADIUS, TIP_LENGTH);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /**
     * 由眼睛位置推出持刀那只手的世界坐标。
     * <p>
     * 侧向偏移用的是<b>水平</b>朝向的右向量而不是完整视线，这样抬头低头时手不会绕着身体转——
     * 人形模型的肩膀本来也只跟身体朝向走。前向偏移反过来要用完整视线，因为手臂姿态
     * （{@code HumanoidModelBambooSpearMixin}）是跟着俯仰角一起抬起来的。
     */
    private static Vec3 handAnchor(Vec3 eye, Vec3 view, @Nullable Player owner, boolean firstPerson) {
        Vec3 flat = new Vec3(view.x, 0.0, view.z);
        flat = flat.lengthSqr() < 1.0e-6 ? new Vec3(0.0, 0.0, 1.0) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0.0, flat.x);
        int side = owner != null && owner.getMainArm() == HumanoidArm.LEFT ? -1 : 1;
        Vec3 anchor = eye.add(0.0, HAND_DROP, 0.0)
                .add(right.scale(side * HAND_SIDE))
                .add(view.scale(HAND_FORWARD));
        return firstPerson ? anchor.add(view.scale(FIRST_PERSON_PUSH)) : anchor;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(BambooSpearEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
