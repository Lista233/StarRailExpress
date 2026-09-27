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

package org.agmas.noellesroles.mixin.client.roles.nature_spirit;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.content.entity.BambooSpearEntity;
import org.agmas.noellesroles.init.ModItems;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 第三人称竹刀姿态：竹刀伸出期间把持刀那条手臂沿视线方向前伸，
 * 这样外部视角看到的竹杆是从手里刺出去的，而不是从身体里穿出来。
 * <p>
 * 只在竹刀实体存在期间生效（{@link BambooSpearEntity#isClientActiveOwner}），
 * 单纯拿着竹刀走路仍是原版持物姿态。
 */
@Mixin(HumanoidModel.class)
public abstract class HumanoidModelBambooSpearMixin<T extends LivingEntity> {

    @Shadow
    @Final
    public ModelPart rightArm;

    @Shadow
    @Final
    public ModelPart leftArm;

    @Shadow
    @Final
    public ModelPart head;

    @Inject(method = "poseRightArm", at = @At("TAIL"))
    private void bambooSpear$poseRightArm(T entity, CallbackInfo ci) {
        bambooSpear$poseArm(entity, this.rightArm, HumanoidArm.RIGHT);
    }

    @Inject(method = "poseLeftArm", at = @At("TAIL"))
    private void bambooSpear$poseLeftArm(T entity, CallbackInfo ci) {
        bambooSpear$poseArm(entity, this.leftArm, HumanoidArm.LEFT);
    }

    @Unique
    private void bambooSpear$poseArm(T entity, ModelPart arm, HumanoidArm humanoidArm) {
        if (!(entity instanceof Player player)) {
            return;
        }
        ItemStack stack = entity.getMainArm() == humanoidArm ? entity.getMainHandItem() : entity.getOffhandItem();
        if (!stack.is(ModItems.BAMBOO_SPEAR)) {
            return;
        }
        if (!BambooSpearEntity.isClientActiveOwner(player)) {
            return;
        }
        // 手臂完全前伸并跟随俯仰角：渲染端的手部锚点就是按这个姿态算的，两边必须同步改。
        arm.xRot = (-(float) Math.PI / 2.0F) + this.head.xRot;
        arm.yRot = this.head.yRot;
        arm.zRot = 0.0F;
    }
}
