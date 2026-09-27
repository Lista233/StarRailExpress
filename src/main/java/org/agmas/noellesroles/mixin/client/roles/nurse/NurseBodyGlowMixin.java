package org.agmas.noellesroles.mixin.client.roles.nurse;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.NurseRoleData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 护士·尸体透视。
 *
 * <p>因虚拟血量归零（{@code dream_axe}）死亡的玩家尸体，自生成起 30 秒内
 * 会被护士看到发光轮廓（服务端在 {@code NurseRole.onBodySpawn} 里把尸体
 * 登记进护士的 {@link NurseRoleData} 并同步）。
 *
 * <p>实现方式与里世界同界描边（{@code BackworldOutlineGlowMixin}）一致：
 * 客户端拦截 {@code Entity#isCurrentlyGlowing()}，让原版实体描边后处理自动接管；
 * 描边颜色通过 {@code Entity#getTeamColor()} 指定为护士粉。
 */
@Mixin(Entity.class)
public abstract class NurseBodyGlowMixin {

    /** 护士尸体透视的描边颜色（护士粉）。 */
    private static final int NR$NURSE_BODY_GLOW_COLOR = 0xFF8AB3;

    @Inject(method = "isCurrentlyGlowing", at = @At("HEAD"), cancellable = true)
    private void nr$nurseBodyGlow(CallbackInfoReturnable<Boolean> cir) {
        if (nr$shouldGlow((Entity) (Object) this)) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getTeamColor", at = @At("HEAD"), cancellable = true)
    private void nr$nurseBodyGlowColor(CallbackInfoReturnable<Integer> cir) {
        if (nr$shouldGlow((Entity) (Object) this)) {
            cir.setReturnValue(NR$NURSE_BODY_GLOW_COLOR);
        }
    }

    private static boolean nr$shouldGlow(Entity self) {
        if (!(self instanceof PlayerBodyEntity body)) {
            return false;
        }
        if (!self.level().isClientSide()) {
            return false;
        }
        Minecraft client = Minecraft.getInstance();
        Player viewer = client.player;
        if (viewer == null || viewer == self || client.level == null) {
            return false;
        }
        // 只有护士本人能看到
        if (viewer.hasEffect(ModEffects.SAFE_TIME)) {
            return false;
        }
        if (SREClient.gameComponent == null
                || !SREClient.gameComponent.isRole(viewer, ModRoles.NURSE)) {
            return false;
        }
        NurseRoleData data = io.wifi.starrailexpress.api.data.RoleData
                .getNullable(NurseRoleData.class, viewer);
        if (data == null) {
            return false;
        }
        return data.isBodyGlowing(body.getUUID(), client.level.getGameTime());
    }
}
