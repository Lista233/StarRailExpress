package org.agmas.noellesroles.game.roles.killer.siege;

import java.util.List;

import org.agmas.noellesroles.init.ModItems;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole.MoodType;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.resources.ResourceLocation;

/**
 * 攻城手：杀手阵营职业。
 *
 * <p>
 * 本职业的规则全部集中在这个类里，方便后续调试与修改：
 * <ul>
 * <li>商店：{@link #getShopEntries()}（普通杀手商店 + 破墙弹 / 粘液弹）</li>
 * <li>价格：{@link #WALL_BREAK_GRENADE_PRICE}、{@link #SLIME_GRENADE_PRICE}</li>
 * </ul>
 * 刷新条件（仅攻城手地图、30% 概率、不可被其它职业随机）在
 * {@code ModRoles#SIEGE} 的注册处配置。
 *
 * <p>
 * 本职业没有每人状态与冷却，因此不建 RoleData。
 */
public class SiegeRole extends NormalRole {

    /** 破墙弹的购买价格（金币） */
    public static final int WALL_BREAK_GRENADE_PRICE = 200;

    /** 粘液弹的购买价格（金币） */
    public static final int SLIME_GRENADE_PRICE = 140;

    public SiegeRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    // ==================== 商店 ====================

    /**
     * 专属商店：普通杀手商店（刀具 / 左轮 / 手雷等）的基础上追加破墙弹与粘液弹。
     * 双端都会调用，因此不要在这里碰服务端专用的东西。
     */
    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = ShopContent.getDefaultKnifeEntries();
        shop.add(new ShopEntry(ModItems.WALL_BREAK_GRENADE.getDefaultInstance(),
                WALL_BREAK_GRENADE_PRICE, ShopEntry.Type.WEAPON));
        shop.add(new ShopEntry(ModItems.SLIME_GRENADE.getDefaultInstance(),
                SLIME_GRENADE_PRICE, ShopEntry.Type.WEAPON));
        return shop;
    }
}
