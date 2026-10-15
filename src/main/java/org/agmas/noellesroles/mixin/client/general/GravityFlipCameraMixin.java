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

package org.agmas.noellesroles.mixin.client.general;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.agmas.noellesroles.content.effects.GravityFlipEffect;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.joml.Matrix4f;

/**
 * 重力颠倒的视角层：把投影矩阵绕 Z 轴旋转 180°，
 * 整个视野（世界 + 手持物）随之一同倒转——天在下、地在上。
 *
 * <p>{@code getProjectionMatrix} 每帧都会新建矩阵，原地旋转不会跨帧累积；
 * 只影响世界渲染，HUD / GUI 走各自的投影，不受牵连。
 */
@Mixin(GameRenderer.class)
public abstract class GravityFlipCameraMixin {

    @Inject(method = "getProjectionMatrix", at = @At("RETURN"))
    private void noellesroles$upsideDownProjection(CallbackInfoReturnable<Matrix4f> cir) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && GravityFlipEffect.isFlipped(client.player)) {
            cir.getReturnValue().rotateZ((float) Math.PI);
        }
    }
}
