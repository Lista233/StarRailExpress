package org.agmas.noellesroles.role.qust.roles.wisadel;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.game.ShopContent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 维什戴尔_星魂（Wisadel）—— QUST 杀手阵营职业。
 * <ul>
 *   <li>杀手阵营，商店与默认杀手一致（{@link ShopContent#getDefaultKnifeEntries()}）。</li>
 *   <li>额外商品：<b>祖宗发射器</b>（350 金币）、<b>肘子</b>（25 金币），均置顶展示。</li>
 *   <li>魂灵机制：右键与玩家尸体交互汲取 1 层「魂灵」（每具尸体限一次），最多 7 层；
 *       购买祖宗发射器需消耗 5 层，且购买后有 150 秒冷却。
 *       交互逻辑见 {@code QUSTHandlers.registerWisadelEvents()}。</li>
 * </ul>
 */
public class WisadelRole extends NormalRole {

    /** 祖宗发射器售价（金币） */
    public static final int SHOTGUN_PRICE = 325;
    /** 肘子售价（金币） */
    public static final int PORK_LEG_PRICE = 25;

    public WisadelRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            SRERole.MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public List<ShopEntry> getShopEntries(@Nullable Player player) {
        // 两件新品（祖宗发射器、肘子）置顶，其后跟默认杀手商店
        List<ShopEntry> shop = new ArrayList<>();

        // 祖宗发射器：需要至少 5 层魂灵，购买消耗 5 层并进入 150 秒购买冷却
        shop.add(new ShopEntry(ModItems.WISDEL_SHOTGUN.getDefaultInstance(), SHOTGUN_PRICE, ShopEntry.Type.WEAPON) {
            @Override
            public boolean canBuy(@NotNull Player buyer) {
                WisadelPlayerComponent component = QUSTComponentKeys.Keys.WISADEL.get(buyer);
                if (component == null) {
                    return false;
                }
                if (!component.hasEnoughSouls()) {
                    this.setFailedMessage(Component.translatable("shop.wisadel.shotgun.need_soul",
                            WisadelPlayerComponent.shotgunSoulCost())
                            .withStyle(ChatFormatting.RED));
                    return false;
                }
                if (!component.isShotgunPurchaseReady(buyer.level().getGameTime())) {
                    this.setFailedMessage(Component.translatable("shop.wisadel.shotgun.cooldown")
                            .withStyle(ChatFormatting.RED));
                    return false;
                }
                return super.canBuy(buyer);
            }

            @Override
            public boolean onBuy(@NotNull Player buyer) {
                WisadelPlayerComponent component = QUSTComponentKeys.Keys.WISADEL.get(buyer);
                if (component == null || !component.consumeSoulsForShotgun()) {
                    return false;
                }
                boolean success = super.onBuy(buyer);
                if (success) {
                    component.markShotgunPurchased(buyer.level().getGameTime());
                } else {
                    // 发放失败（背包满等）：返还魂灵，避免白白消耗
                    for (int i = 0; i < WisadelPlayerComponent.shotgunSoulCost(); i++) {
                        component.addSoul();
                    }
                }
                return success;
            }
        });

        // 肘子：普通可食物品
        shop.add(new ShopEntry(ModItems.PORK_LEG.getDefaultInstance(), PORK_LEG_PRICE, ShopEntry.Type.TOOL));

        // 默认杀手商店（刀+左轮+手雷等）排在两件新品之后
        shop.addAll(ShopContent.getDefaultKnifeEntries());

        return shop;
    }
}
