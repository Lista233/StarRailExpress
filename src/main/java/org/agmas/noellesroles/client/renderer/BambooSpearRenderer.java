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

        Player owner = entity.getOwner();
        float length = Math.max(MIN_LENGTH, entity.getInterpolatedLength(partialTick));
        Vec3 origin;
        Vec3 direction;
        // 优先用「持有者实时的眼睛位置 + 视线向量」绘制：这是客户端每帧最新的朝向，
        // 因此转身时枪身会实时跟随，且每次重新释放都按当时朝向计算，不会卡在旧方向。
        // 仅当持有者查不到（如已离线）时，才回退到服务端同步下来的眼睛/视线数据。
        Vec3 eye;
        Vec3 view;
        if (owner != null) {
            eye = owner.getEyePosition(partialTick);
            view = owner.getViewVector(partialTick);
        } else {
            eye = entity.getEyePos();
            view = entity.getLookDir();
        }
        if (eye != null && view != null) {
            origin = eye.add(0.0, -0.22, 0.0).add(view.scale(0.28));
            if (owner == viewer && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                origin = origin.add(view.scale(0.45));
            }
            Vec3 target = eye.add(view.scale(BambooSpearEntity.MAX_LENGTH));
            direction = target.subtract(origin).normalize();
        } else {
            float yaw = Mth.lerp(partialTick, entity.yRotO, entity.getYRot());
            float pitch = Mth.lerp(partialTick, entity.xRotO, entity.getXRot());
            direction = entity.calculateViewVector(pitch, yaw);
            origin = entity.getPosition(partialTick);
        }

        // 仅翻转「视觉」方向（客户端绘制）：把枪身从当前朝后的方向调为正前方。
        // 不影响服务端同步下来的视线方向，因此命中判定仍按正前方结算，不会打偏。
        direction = direction.scale(-1.0);

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
