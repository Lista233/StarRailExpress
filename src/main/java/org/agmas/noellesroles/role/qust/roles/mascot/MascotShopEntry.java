package org.agmas.noellesroles.role.qust.roles.mascot;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.cca.DynamicShopComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

/**
 * 吉祥物商店条目：防御药剂，限购1次。
 */
public class MascotShopEntry extends ShopEntry {
    public MascotShopEntry(int price) {
        super(TMMItems.DEFENSE_VIAL.getDefaultInstance(), price, Type.POISON);
    }

    @Override
    public boolean onBuy(@NotNull Player player) {
        return applyOnlyCanBuyOne(player);
    }

    private boolean applyOnlyCanBuyOne(@NotNull Player player) {
        DynamicShopComponent dynamicShop = DynamicShopComponent.KEY.get(player);
        ResourceLocation ItemId = BuiltInRegistries.ITEM.getKey(this.stack().getItem());
        int purchaseCount = dynamicShop.getPurchaseCount(ItemId);

        if (purchaseCount >= 1) {
            Component message = Component.translatable("message.mascot.cant_buy");
            setFailedMessage(message);
            return false;
        } else {
            RoleUtils.insertStackInFreeSlot(player, this.stack().copy());
            dynamicShop.recordPurchase(ItemId);
        }
        return true;
    }

    @Override
    public void setFailedMessage(Component message) {
        super.setFailedMessage(message);
    }
}
