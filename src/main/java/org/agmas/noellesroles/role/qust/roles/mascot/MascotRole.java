package org.agmas.noellesroles.role.qust.roles.mascot;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREPlayerTaskComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * 吉祥物角色 - 乘客阵营
 * <ul>
 *   <li>好人阵营 (isInnocent = true)</li>
 *   <li>不能使用杀手能力 (canUseKiller = false)</li>
 *   <li>真实心情 (MoodType.REAL)</li>
 *   <li>1.25倍标准冲刺时间</li>
 *   <li>可以看见金币</li>
 *   <li>完成任务后给周围玩家恢复心情和金币</li>
 *   <li>90秒后开始周期性发光，发光时被刀杀/棍杀则杀手直接胜利</li>
 *   <li>完成指定数量任务后乘客阵营直接胜利</li>
 * </ul>
 */
public class MascotRole extends NormalRole {
    public MascotRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                      MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
        refreshableTasksInit();
    }

    @Override
    public void onFinishQuest(Player player, String quest) {
        if (player.level().isClientSide())
            return;
        QUSTComponentKeys.Keys.MASCOT.get(player).callOnFinishQuest(player, quest);
    }

    @Override
    public @Nullable List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();
        shop.add(new MascotShopEntry(350));
        return shop;
    }

    public void refreshableTasksInit() {
        super.addUnrefreshableTasks(
                SREPlayerTaskComponent.Task.CHAIR,
                SREPlayerTaskComponent.Task.EAT,
                SREPlayerTaskComponent.Task.DRINK,
                SREPlayerTaskComponent.Task.BE_ALONE,
                SREPlayerTaskComponent.Task.MEDITATE
        );
    }

    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason, boolean forceDeath) {
        if (!org.agmas.noellesroles.utils.RoleUtils.isPlayerTheJob(victim,
                org.agmas.noellesroles.role.qust.QUSTRoles.MASCOT)) {
            return;
        }
        QUSTComponentKeys.Keys.MASCOT.get(victim).callOnDeath(victim, spawnBody, killer, deathReason, forceDeath);
    }
}
