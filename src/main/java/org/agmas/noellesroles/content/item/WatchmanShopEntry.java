package org.agmas.noellesroles.content.item;

import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

/**
 * 更夫道具（锣 / 梆）的动态商店条目。
 *
 * <p>
 * 基础价格 {@code basePrice}，每购买一次价格上涨 {@code step} 金币（写入玩家的
 * {@link DynamicShopComponent}），实际扣费价由 {@code DynamicShopComponent#effectivePrice} 结算，
 * 商店 UI 也会显示同样的价格。
 *
 * <p>
 * 道具冷却的递增逻辑见
 * {@link org.agmas.noellesroles.role_data.innocence.WatchmanRoleData#itemCooldownTicks}。
 */
public class WatchmanShopEntry extends ShopEntry {

    private final int basePrice;
    private final int step;

    public WatchmanShopEntry(Item item, int basePrice, int step, Type type) {
        super(item.getDefaultInstance(), basePrice, type);
        this.basePrice = basePrice;
        this.step = step;
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        boolean success = super.onBuy(player);
        if (!success) {
            return false;
        }
        DynamicShopComponent dynamicShop = DynamicShopComponent.KEY.get(player);
        // 价格键按物品栈区分（自定义列车物品用其自定义 id），涨价只影响这一件商品
        String itemKey = DynamicShopComponent.keyOf(this.stack());
        // 已购买次数（含本次）
        int count = dynamicShop.recordPurchase(itemKey);
        // 下一次购买的价格：basePrice + step * 已购买次数
        int nextPrice = basePrice + step * count;
        dynamicShop.setMultiplier(itemKey, nextPrice / (double) basePrice);
        return true;
    }
}
