package org.agmas.noellesroles.role.qust.roles.super_doctor;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.qust.QUSTConfig;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 超级医生（Super Doctor / 医生_花艺）
 * <p>
 * 平民阵营，默认概率。拥有与医生一样的机制以及商店，但不与毒师绑定生成。
 * 按 G 可以恢复周围人少量 san 值。
 * 可在商店购买"救赎之枪"（350 金币），一次性特殊枪械。
 */
public class SuperDoctorRole extends NormalRole {

    public SuperDoctorRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                           MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    // ── 商店：与医生相同 + 救赎之枪 ──

    @Override
    public @Nullable List<ShopEntry> getShopEntries(@Nullable Player player) {
        List<ShopEntry> entries = new ArrayList<>();
        // 与医生商店相同
        entries.add(new ShopEntry(ModItems.ANTIDOTE_REAGENT.getDefaultInstance(), 50, ShopEntry.Type.TOOL));
        entries.add(new ShopEntry(ModItems.ANTIDOTE.getDefaultInstance(), 75, ShopEntry.Type.TOOL));
        entries.add(new ShopEntry(ModItems.createPillStack(false), 75, ShopEntry.Type.TOOL));
        entries.add(new ShopEntry(ModItems.PURIFY_BOMB.getDefaultInstance(), 225, ShopEntry.Type.TOOL));
        // 救赎之枪 - 配置价格
        entries.add(createRepentanceGunEntry());
        return entries;
    }

    /**
     * 创建救赎之枪商店条目
     */
    public static ShopEntry createRepentanceGunEntry() {
        ItemStack gun = ModItems.REPENTANCE_GUN.getDefaultInstance();
        return new ShopEntry(gun, QUSTConfig.instance().superDoctorRepentanceGunPrice, ShopEntry.Type.WEAPON) {
            @Override
            public boolean onBuy(Player player) {
                return RoleUtils.insertStackInFreeSlot(player, gun.copy());
            }
        };
    }
}
