package org.agmas.noellesroles.role.qust.roles.aimlabs;

import io.wifi.starrailexpress.api.hit.HitType;
import io.wifi.starrailexpress.api.hit.SREHitManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jetbrains.annotations.NotNull;

/**
 * Aimlabs 练习手枪：仅在练枪会话期间使用，只能命中靶标实体，无音效无CD。
 * <p>
 * 逻辑参考 {@code RevolverItem}，但：
 * <ul>
 *   <li>仅对 {@link AimlabsTargetEntity} 有效</li>
 *   <li>射击时不播放音效</li>
 *   <li>无内置 CD</li>
 *   <li>不消耗耐久</li>
 * </ul>
 */
public class PracticeRevolverItem extends Item {

    public PracticeRevolverItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (world.isClientSide) {
            // 客户端射线检测：只命中靶标实体
            HitResult collision = getPracticeTarget(user);
            if (collision instanceof EntityHitResult entityHitResult) {
                Entity target = entityHitResult.getEntity();
                if (target instanceof AimlabsTargetEntity) {
                    // 发送练习手枪射击包到服务端
                    ClientPlayNetworking.send(new AimlabsPayload.PracticeShot(target.getId()));
                }
            }
            // 轻微后坐力反馈
            user.setXRot(user.getXRot() - 2.0F);
        }
        // 服务端不做任何处理（命中由客户端包触发）
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 练习手枪专用射线检测：只命中 AimlabsTargetEntity。
     */
    public static HitResult getPracticeTarget(Player user) {
        return SREHitManager.getTarget(user, HitType.GUN, 50f);
    }
}
