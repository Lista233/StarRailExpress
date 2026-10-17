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
 * 竹刀渲染：整根竹杆<b>从持有者手里长出来</b>，朝向恒等于持有者的当前朝向。
 * <p>
 * 视觉锚点取手部而不是眼睛，所以第一人称看着像从手上刺出去，第三人称（其他玩家 / 自己按 F5）
 * 也是从那只手伸出去的，而不是从脑门飘出来。手臂姿态由 {@code HumanoidModelBambooSpearMixin} 抬起来配合。
 * <p>
 * <b>为什么方向直接用视线方向，而不是「手 → 命中点」：</b>
 * 握手点相对眼睛有约 0.3 格的侧向 / 向下偏移。如果用「手 → 命中点」求方向，那么杆越短这个偏移占比越大：
 * 刚右键（杆长≈0）时命中点几乎落在眼睛上，也就是在手的<b>后方</b>，竹杆就会朝身后长出来，
 * 再随着伸长慢慢甩到正前方——这就是之前那个「伸出动画」错乱的根因。
 * 现在改成：<b>起点 = 手（与服务端共用的 {@link BambooSpearEntity#handAnchor}），方向 = 视线</b>，
 * 杆的朝向恒等于准星方向，伸长过程就是一条笔直往前长的竹杆。
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

        // 起点 = 握手点（服务端结算用的是同一个 handAnchor），方向 = 视线方向。
        Vec3 origin = BambooSpearEntity.handAnchor(eye, view, owner);
        // length 本身就是「从握手点沿视线量出去的杆长」，服务端也是这么算命中点的。
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
