package org.agmas.noellesroles.role.qust.roles.minigame_master;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.qust.QUSTConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 小游戏达人（Minigame Master / 这个_骇客）角色定义
 * <ul>
 *   <li>平民阵营，拥有正常平民商店 + 小游戏券</li>
 *   <li>商店可购买小游戏券（50金币）、击退剑（200金币，持有即可左键击退）</li>
 * </ul>
 */
public class MinigameMasterRole extends NormalRole {

    public MinigameMasterRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            SRERole.MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    // ==================== 商店：覆写 getShopEntries ====================

    @Override
    public @Nullable List<ShopEntry> getShopEntries(@Nullable net.minecraft.world.entity.player.Player player) {
        List<ShopEntry> entries = new ArrayList<>();
        // 小游戏券
        entries.add(createMinigameTicketEntry());
        // 击退剑（200金币，持有即可左键击退）
        entries.add(createKnockbackSwordEntry());
        // 华容道挑战（1200金币，通关直接胜利）
        entries.add(createKlotskiChallengeEntry());
        return entries;
    }

    /**
     * 创建小游戏券商店条目（一次购买获得 4 张）
     */
    public static ShopEntry createMinigameTicketEntry() {
        ItemStack ticket = createMinigameTicketStack();
        return new ShopEntry(ticket, QUSTConfig.instance().minigameMasterTicketPrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                // 一次给 4 张小游戏券
                ItemStack stack4 = ticket.copy();
                stack4.setCount(4);
                return SREItemUtils.insertStackInFreeSlot(player, stack4);
            }
        };
    }

    /**
     * 创建击退剑商店条目（持有左键攻击只击退，200金币）
     */
    public static ShopEntry createKnockbackSwordEntry() {
        ItemStack sword = createKnockbackSwordStack();
        return new ShopEntry(sword, QUSTConfig.instance().minigameMasterKnockbackSwordPrice, ShopEntry.Type.WEAPON) {
            @Override
            public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                return SREItemUtils.insertStackInFreeSlot(player, sword.copy());
            }
        };
    }

    /**
     * 创建华容道挑战商店条目（1200金币，通关后平民与义警阵营胜利）
     * <p>购买获得物品，右键随时开始，未通关不消耗。</p>
     */
    public static ShopEntry createKlotskiChallengeEntry() {
        ItemStack challenge = createKlotskiChallengeStack();
        int price = QUSTConfig.instance().minigameMasterKlotskiChallengePrice;
        return new ShopEntry(challenge, price, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(net.minecraft.world.entity.player.Player player) {
                // 购买获得华容道挑战物品
                return SREItemUtils.insertStackInFreeSlot(player, challenge.copy());
            }
        };
    }

    /**
     * 创建华容道挑战物品栈（带自定义名称和 Lore）
     */
    public static ItemStack createKlotskiChallengeStack() {
        ItemStack stack = ModItems.KLOTSKI_CHALLENGE.getDefaultInstance();
        stack.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                Component.translatable("item.minigame_master.klotski_challenge")
                        .withStyle(ChatFormatting.RED, ChatFormatting.BOLD));
        stack.set(net.minecraft.core.component.DataComponents.LORE,
                new ItemLore(List.of(
                        Component.translatable("item.minigame_master.klotski_challenge.lore")
                                .withStyle(style -> style.withItalic(false)
                                        .withColor(ChatFormatting.GRAY)),
                        Component.translatable("item.minigame_master.klotski_challenge.lore2")
                                .withStyle(style -> style.withItalic(false)
                                        .withColor(ChatFormatting.GOLD)))));
        return stack;
    }

    /**
     * 创建小游戏券物品栈（带自定义名称和 Lore）
     */
    public static ItemStack createMinigameTicketStack() {
        ItemStack ticket = ModItems.MINIGAME_TICKET.getDefaultInstance();
        ticket.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                Component.translatable("item.minigame_master.ticket")
                        .withStyle(ChatFormatting.GOLD));
        ticket.set(net.minecraft.core.component.DataComponents.LORE,
                new ItemLore(List.of(
                        Component.translatable("item.minigame_master.ticket.lore")
                                .withStyle(style -> style.withItalic(false)
                                        .withColor(ChatFormatting.GRAY)))));
        return ticket;
    }

    /**
     * 创建击退剑物品栈（带自定义名称和耐久提示）
     */
    public static ItemStack createKnockbackSwordStack() {
        ItemStack sword = ModItems.KNOCKBACK_SWORD.getDefaultInstance();
        sword.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                Component.translatable("item.minigame_master.knockback_sword")
                        .withStyle(ChatFormatting.AQUA));
        sword.set(net.minecraft.core.component.DataComponents.LORE,
                new ItemLore(List.of(
                        Component.translatable("item.minigame_master.knockback_sword.lore")
                                .withStyle(style -> style.withItalic(false)
                                        .withColor(ChatFormatting.GRAY)))));
        return sword;
    }

    // 左键击退由 KnockbackSwordItem（LeftClickHurtable）实现，无需技能激活
}
