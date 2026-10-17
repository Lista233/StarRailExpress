package org.agmas.noellesroles.mixin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.agmas.noellesroles.game.command_block.CommandBlockDirector;
import org.agmas.noellesroles.game.command_block.CommandBlockRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import io.wifi.starrailexpress.api.TMMRoles;
import io.wifi.starrailexpress.game.modes.SREMurderGameMode;
import io.wifi.starrailexpress.game.utils.RoleInstance;

/**
 * 轮抽和志愿会用 Math.max(1, 杀手数) 保底抽一个杀手。
 * 命令方块在场时把杀手和绑定职业换成平民，池子人数不变。
 */
@Mixin(SREMurderGameMode.class)
public class CommandBlockRolePoolMixin {
    @Inject(method = "getAllRoles(IIIILorg/agmas/harpymodloader/modded_murder/RoleAssignmentPool;Lorg/agmas/harpymodloader/modded_murder/RoleAssignmentPool;Lorg/agmas/harpymodloader/modded_murder/RoleAssignmentPool;Lorg/agmas/harpymodloader/modded_murder/RoleAssignmentPool;ZLjava/util/List;I)Ljava/util/List;",
            at = @At("RETURN"), cancellable = true)
    private static void replaceExcludedRoles(CallbackInfoReturnable<List<RoleInstance>> cir) {
        if (!CommandBlockDirector.occupiesKillerSlots()) {
            return;
        }
        List<RoleInstance> original = cir.getReturnValue();
        if (original == null || original.isEmpty()) {
            return;
        }
        List<RoleInstance> replaced = new ArrayList<>(original.size());
        boolean changed = false;
        for (RoleInstance instance : original) {
            if (instance != null && CommandBlockRules.isExcluded(instance.role())) {
                replaced.add(new RoleInstance(UUID.randomUUID(), TMMRoles.CIVILIAN));
                changed = true;
            } else {
                replaced.add(instance);
            }
        }
        if (changed) {
            cir.setReturnValue(replaced);
        }
    }
}
