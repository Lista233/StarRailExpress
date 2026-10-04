package org.agmas.noellesroles.role.qust.roles.american_police;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.component.ModComponents;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.qust.QUSTConfig;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 美国警察角色（亦_无悔） - 义警阵营
 * <ul>
 *   <li>义警阵营 (isInnocent = true)</li>
 *   <li>不能使用杀手能力 (canUseKiller = false)</li>
 *   <li>真实心情 (MoodType.REAL)</li>
 *   <li>标准冲刺体力</li>
 *   <li>初始没有枪，完成3个任务后获得左轮手枪</li>
 *   <li>特殊技能：标记一名玩家，将其皮肤变为 black_man</li>
 *   <li>击杀被标记的玩家没有小脑惩罚</li>
 *   <li>击杀杀手或中立角色增加一次技能使用次数</li>
 * </ul>
 */
public class AmericanPoliceRole extends NormalRole {

    public AmericanPoliceRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                              MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public void onFinishQuest(Player player, String quest) {
        if (player.level().isClientSide())
            return;
        // 先走父类通用任务奖励逻辑：完成 setTaskReward 配置的任务数后发放左轮手枪。
        // （之前缺少该调用，导致“做完 N 个任务获得枪”的奖励从未触发。）
        super.onFinishQuest(player, quest);
        QUSTComponentKeys.Keys.AMERICAN_POLICE.get(player).callOnFinishQuest(player, quest);
    }

    // ==================== 商店：可购买手铐（参考义警/警卫） ====================

    @Override
    public @Nullable List<ShopEntry> getShopEntries(@Nullable Player player) {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(new ShopEntry(ModItems.HANDCUFFS.getDefaultInstance(),
                QUSTConfig.instance().americanPoliceHandcuffPrice, ShopEntry.Type.TOOL));
        return entries;
    }
}
