package org.agmas.noellesroles.role.qust.roles.super_recorder;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.content.entity.PlayerBodyEntity;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.SREItemUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.qust.QUSTConfig;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 超级记录员（Super Recorder / 超级记录员_时星）角色定义
 * <ul>
 *   <li>中立阵营，12 人以上生成</li>
 *   <li>拥有记录员笔记 + 开局假枪</li>
 *   <li>可做任务获得金币，商店可购买真相之书（150 金币）</li>
 *   <li>感知 15 格内死亡，右键尸体查看物品栏</li>
 *   <li>标记 3/4 玩家后直接胜利</li>
 * </ul>
 */
public class SuperRecorderRole extends NormalRole {

    public SuperRecorderRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
            SRERole.MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    // ==================== 商店：覆写 getShopEntries ====================

    @Override
    public @Nullable List<ShopEntry> getShopEntries(@Nullable Player player) {
        List<ShopEntry> entries = new ArrayList<>();
        // 真相之书（类似帕秋莉的笔记，显示未被记录的角色）
        entries.add(createTruthBookEntry());
        // 记录笔记（标记所有玩家）
        entries.add(createNoteEntry());
        return entries;
    }

    /**
     * 创建记录笔记商店条目
     */
    public static ShopEntry createNoteEntry() {
        ItemStack note = ModItems.WRITTEN_NOTE.getDefaultInstance();
        return new ShopEntry(note, QUSTConfig.instance().superRecorderNotePrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                return SREItemUtils.insertStackInFreeSlot(player, note.copy());
            }
        };
    }

    /**
     * 创建真相之书商店条目
     */
    public static ShopEntry createTruthBookEntry() {
        ItemStack book = createTruthBookStack();
        return new ShopEntry(book, QUSTConfig.instance().superRecorderTruthBookPrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(Player player) {
                return SREItemUtils.insertStackInFreeSlot(player, book.copy());
            }
        };
    }

    /**
     * 创建真相之书物品栈
     */
    public static ItemStack createTruthBookStack() {
        ItemStack book = ModItems.TRUTH_BOOK.getDefaultInstance();
        book.set(net.minecraft.core.component.DataComponents.ITEM_NAME,
                Component.translatable("item.super_recorder.truth_book")
                        .withStyle(ChatFormatting.LIGHT_PURPLE));
        book.set(net.minecraft.core.component.DataComponents.LORE,
                new ItemLore(List.of(
                        Component.translatable("item.super_recorder.truth_book.lore")
                                .withStyle(style -> style.withItalic(false)
                                        .withColor(ChatFormatting.GRAY)))));
        return book;
    }

    /**
     * 判断物品是否为真相之书
     */
    public static boolean isTruthBook(ItemStack stack) {
        if (!stack.is(ModItems.TRUTH_BOOK)) return false;
        return true;
    }

    // ==================== 尸体交互：可查看物品栏 ====================

    @Override
    public boolean canSeeBodyItems(Player player, PlayerBodyEntity body) {
        return true;
    }
}
