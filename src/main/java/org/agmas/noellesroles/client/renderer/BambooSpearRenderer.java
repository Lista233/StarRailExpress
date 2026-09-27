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
import org.joml.Vector3f;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.BambooSpearEntity;
import org.agmas.noellesroles.init.ModEffects;

/** 竹枪：从持有者手部沿视线伸出，长度在客户端插值，伸缩看起来连续。 */
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
        LocalPlayer viewer = Minecraft.getInstance().player;
        if (viewer != null && viewer.hasEffect(ModEffects.TIME_STOP)
                && !TimeStopEffect.clientCanMovePlayers.contains(viewer.getUUID())) {
            return;
        }

        float length = Math.max(MIN_LENGTH, entity.getInterpolatedLength(partialTick));
        Vec3 origin;
        Vec3 direction;

        UUID ownerUuid = entity.getOwnerUuid();
        boolean isLocalOwner = viewer != null && ownerUuid != null && ownerUuid.equals(viewer.getUUID());

        // 1) 优先用服务端每 tick 同步的权威眼睛/视线：命中判定就是用它结算的，
        //    且服务器实时持有持有者（含本地玩家）的当前视角，因此对谁都实时、且视觉与命中一致。
        //    注意：必须放在最前面，否则一旦 getOwner() 解析到本地玩家，就会退回到恒定的
        //    owner.getViewVector()，导致竹枪卡在固定方向（默认南）。
        Vec3 eye = entity.getEyePos();
        Vec3 view = entity.getLookDir();
        boolean resolved = eye != null && view != null;

        // 2) 本地玩家兜底：用相机（最实时，不受实体旋转同步影响）。
        if (!resolved && isLocalOwner) {
            Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
            eye = camera.getPosition();
            Vector3f look = camera.getLookVector();
            view = new Vec3(look.x(), look.y(), look.z());
            resolved = true;
        }

        // 3) 其他玩家兜底：用其客户端实时的实体旋转。
        if (!resolved) {
            Player owner = entity.getOwner();
            if (owner != null) {
                eye = owner.getEyePosition(partialTick);
                view = owner.getViewVector(partialTick);
                resolved = true;
            }
        }

        if (resolved) {
            origin = eye.add(0.0, -0.22, 0.0).add(view.scale(0.28));
            if (isLocalOwner && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                origin = origin.add(view.scale(0.45));
            }
            Vec3 target = eye.add(view.scale(BambooSpearEntity.MAX_LENGTH));
            direction = target.subtract(origin).normalize();
        } else {
            // 兜底：发射瞬间的实体静态旋转（不随视角更新）。
            float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            direction = entity.calculateViewVector(pitch, yaw);
            origin = entity.getPosition(partialTick);
        }
        // 视线方向（服务端同步/相机）已经是「正前方」命中方向，无需取反。

        Vec3 entityPos = entity.getPosition(partialTick);
        poseStack.pushPose();
        poseStack.translate(origin.x - entityPos.x, origin.y - entityPos.y, origin.z - entityPos.z);
        BambooPoleGeometry.orient(poseStack, direction);
        BambooPoleGeometry.render(poseStack, bufferSource, packedLight, 0.0F, length, RADIUS, TIP_LENGTH);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(BambooSpearEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
