package org.agmas.noellesroles.role.qust.roles.super_doctor.content;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.hit.HitType;
import io.wifi.starrailexpress.api.hit.SREHitManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.agmas.noellesroles.role.qust.roles.super_doctor.SuperDoctorPayload;
import org.jetbrains.annotations.NotNull;

/**
 * 救赎之枪 — 超级医生专属一次性枪械。
 * <p>
 * 射中杀手 → 杀手变为医生职业；射中中立 → 无效果；
 * 射中平民/义警 → 不致死，但超级医生受小脑惩罚。
 * 仅限使用一次，命中后消失。空枪 CD 与普通枪一致。
 */
public class RepentanceGunItem extends SkinableItem {

    public RepentanceGunItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (world.isClientSide) {
            SRERole role = null;
            SREGameWorldComponent gameComponent = SREClient.gameComponent;
            if (gameComponent != null) {
                role = gameComponent.getRole(user);
                if (role != null && !role.onUseGun(user)) {
                    return InteractionResultHolder.fail(stack);
                }
            }

            // 消耗物品（一次性）
            stack.hurtAndBreak(1, user,
                    hand.equals(InteractionHand.MAIN_HAND) ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);

            // 发送救赎之枪专用数据包
            HitResult collision = getGunTarget(user);
            if (collision instanceof EntityHitResult entityHitResult) {
                Entity target = entityHitResult.getEntity();
                ClientPlayNetworking.send(new SuperDoctorPayload(target.getId()));
            } else {
                ClientPlayNetworking.send(new SuperDoctorPayload(-1));
            }

            user.setXRot(user.getXRot() - 4.0F);
            spawnHandParticle();
            // 空枪 CD 与普通枪一致
            user.getCooldowns().addCooldown(TMMItems.REVOLVER, 5 * 20);
        }
        return InteractionResultHolder.consume(stack);
    }

    public static void spawnHandParticle() {
        HandParticle handParticle = new HandParticle()
                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
                .setPos(0.1F, 0.275F, -0.2F).setMaxAge(3.0F).setSize(0.5F).setVelocity(0.0F, 0.0F, 0.0F)
                .setLight(15, 15).setAlpha(new float[]{1.0F, 0.1F}).setRenderLayer(TMMRenderLayers::additive);
        SREClient.handParticleManager.spawn(handParticle);
    }

    public static HitResult getGunTarget(Player user) {
        return SREHitManager.getTarget(user, HitType.GUN, 15f);
    }

    @Override
    public String getItemSkinType() {
        return "revolver";
    }
}
