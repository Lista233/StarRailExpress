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
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.effects.TimeStopEffect;
import org.agmas.noellesroles.content.entity.ThrownBambooEntity;
import org.agmas.noellesroles.init.ModEffects;

/**
 * 投掷竹子（竹枪）：沿飞行方向绘制带竹节和尖头的 3D 竹杆。
 * <p>
 * 竹杆从实体原点向前 {@link ThrownBambooEntity#TIP_REACH}、向后 {@link ThrownBambooEntity#TAIL_REACH}，
 * 与实体里挂人 / 扫掠判定用的是同一组常量，所以「看到被戳到」就是「真的被戳到」。
 * 钉墙瞬间加一段阻尼震颤，让「扎进墙里」这件事在画面上看得出来。
 */
public class ThrownBambooRenderer extends EntityRenderer<ThrownBambooEntity> {

    private static final float RADIUS = 0.085F;
    private static final float TIP_LENGTH = 0.4F;
    /** 入墙震颤：持续时间（tick）、初始摆幅（弧度）、摆动频率。 */
    private static final float QUIVER_TICKS = 7.0F;
    private static final float QUIVER_AMPLITUDE = 0.16F;
    private static final float QUIVER_SPEED = 2.2F;

    public ThrownBambooRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.shadowRadius = 0.0f;
    }

    @Override
    public void render(ThrownBambooEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource bufferSource, int packedLight) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(ModEffects.TIME_STOP)
                && !TimeStopEffect.clientCanMovePlayers.contains(player.getUUID())) {
            return;
        }

        Vec3 direction = Vec3.directionFromRotation(0.0F, entity.getRenderYaw(partialTick));
        poseStack.pushPose();
        BambooPoleGeometry.orient(poseStack, direction);
        float quiver = quiverAngle(entity, partialTick);
        if (quiver != 0.0F) {
            // 绕杆尖（钉在墙里的那一端）摆动，杆尾晃得最凶——真被钉住的杆子就是这么抖的。
            poseStack.translate(0.0F, ThrownBambooEntity.TIP_REACH, 0.0F);
            poseStack.mulPose(Axis.XP.rotation(quiver));
            poseStack.translate(0.0F, -ThrownBambooEntity.TIP_REACH, 0.0F);
        }
        BambooPoleGeometry.render(poseStack, bufferSource, packedLight, -ThrownBambooEntity.TAIL_REACH,
                ThrownBambooEntity.TIP_REACH, RADIUS, TIP_LENGTH);
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** 钉墙后的阻尼摆动角；未钉墙或已摆停返回 0。 */
    private static float quiverAngle(ThrownBambooEntity entity, float partialTick) {
        int pinTicks = entity.getClientPinTicks();
        if (pinTicks < 0) {
            return 0.0F;
        }
        float age = pinTicks + partialTick;
        if (age >= QUIVER_TICKS) {
            return 0.0F;
        }
        float decay = 1.0F - age / QUIVER_TICKS;
        return Mth.sin(age * QUIVER_SPEED) * QUIVER_AMPLITUDE * decay * decay;
    }

    @SuppressWarnings("deprecation")
    @Override
    public ResourceLocation getTextureLocation(ThrownBambooEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
