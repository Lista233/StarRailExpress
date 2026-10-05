package org.agmas.noellesroles.role.qust.roles.bettor;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.qust.QUSTConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 筹客（Bettor）角色定义
 * <ul>
 *   <li>平民阵营 (isInnocent = true)</li>
 *   <li>不能使用杀手能力 (canUseKiller = false)</li>
 *   <li>真实心情 (MoodType.REAL)</li>
 *   <li>核心机制：商店购买恶魔轮盘，右键开始滚动数字，再右键停止并获得随机奖励</li>
 * </ul>
 */
public class BettorRole extends NormalRole {

    public BettorRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                      SRERole.MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    @Override
    public @Nullable List<ShopEntry> getShopEntries(@Nullable net.minecraft.world.entity.player.Player player) {
        List<ShopEntry> entries = new ArrayList<>();
        entries.add(createDevilRouletteEntry());
        return entries;
    }

    /**
     * 创建恶魔轮盘商店条目（75金币）
     */
    public static ShopEntry createDevilRouletteEntry() {
        ItemStack roulette = createDevilRouletteStack();
        int price = QUSTConfig.instance().bettorDevilRoulettePrice;
        return new ShopEntry(roulette, price, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                return SREItemUtils.insertStackInFreeSlot(player, roulette.copy());
            }
        };
    }

    /**
     * 创建恶魔轮盘物品栈（带自定义名称和 Lore）
     */
    public static ItemStack createDevilRouletteStack() {
        ItemStack stack = ModItems.DEVIL_ROULETTE.getDefaultInstance();
        stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                Component.translatable("item.bettor.devil_roulette")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD));
        stack.set(net.minecraft.core.component.DataComponents.LORE,
                new ItemLore(List.of(
                        Component.translatable("item.bettor.devil_roulette.lore")
                                .withStyle(style -> style.withItalic(false)
                                        .withColor(ChatFormatting.GRAY)),
                        Component.translatable("item.bettor.devil_roulette.lore2")
                                .withStyle(style -> style.withItalic(false)
                                        .withColor(ChatFormatting.RED)))));
        return stack;
    }
}
