/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.wifi.starrailexpress.cca;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * 动态商店组件 / Dynamic shop component.
 *
 * <p>
 * 按玩家维度存储「商品价格修正」与「购买次数」，让局内商店的商品可以拥有动态价格
 * （折扣、按比例打折、固定减价、甚至溢价）。修正以「商品价格键」为键
 * （见 {@link #keyOf(ItemStack)}），因此同一件商品在不同玩家身上可以有不同的实时价格，
 * 且互不影响其它商品。
 *
 * <p>
 * Stores per-player price modifiers and purchase counts, keyed by the product's
 * price key, so in-game shop items can have dynamic prices (percentage discounts,
 * flat reductions, multipliers, or surge pricing). The component auto-syncs to its
 * owner, so the client shop UI can display the same effective price the server
 * will charge.
 *
 * <p>
 * 价格计算 / Price formula:
 * {@code effective = max(0, round(basePrice * multiplier) - flatReduction)}.
 *
 * @author canyuesama
 */
public class DynamicShopComponent implements RoleComponent {
    public static final ComponentKey<DynamicShopComponent> KEY = ComponentRegistry.getOrCreate(SRE.id("dynamic_shop"),
            DynamicShopComponent.class);

    private final Player player;
    /** 商品价格键 -> 价格修正 / product price key -> price modifier */
    private final Map<String, PriceModifier> modifiers = new HashMap<>();
    /** 商品价格键 -> 购买次数 / product price key -> times purchased */
    private final Map<String, Integer> purchaseCounts = new HashMap<>();

    public DynamicShopComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer player) {
        return this.player == player;
    }

    @Override
    public void init() {
        this.modifiers.clear();
        this.purchaseCounts.clear();
        this.sync();
    }

    @Override
    public void clear() {
        init();
    }

    /** 仅服务端：把改动同步给所有者客户端。 / Server-only: push changes to the owner client. */
    public void sync() {
        if (!this.player.level().isClientSide) {
            KEY.sync(this.player);
        }
    }

    // ------------------------------------------------------------------
    // 商品价格键 / Product price key
    // ------------------------------------------------------------------

    /**
     * 计算一个商品（物品栈）的「价格键」/ computes the price key of a shop product.
     *
     * <p>
     * 自定义列车物品（包含「还没登记的自定义物品空壳」）<b>共用一个物品本体</b>
     * {@code starrailexpress:custom_item}，只拿 {@code BuiltInRegistries.ITEM} 的物品 id
     * 无法区分不同商品，于是「某件商品每次购买涨价 / 降价」会误伤商店里其它自定义物品商品
     * （买一次全都涨价/降价）。这里把物品栈上的 {@code CUSTOM_ITEM_ID} 组件并入键：
     * 自定义物品用 {@code custom:&lt;id&gt;} 区分，其余物品仍用其注册 id。这样价格修正
     * 只作用于被购买的那一件商品。
     *
     * <p>
     * Custom train items (including not-yet-registered placeholders) all share one base item,
     * so their registry id alone cannot tell two products apart — a per-purchase price
     * change would then bleed onto every other custom-item product. Folding the
     * {@code CUSTOM_ITEM_ID} component into the key keeps each product's price
     * independent.
     */
    public static String keyOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return "minecraft:air";
        }
        String customId = io.wifi.starrailexpress.customitem.CustomItemLoader.getCustomItemId(stack);
        if (customId != null && !customId.isEmpty()) {
            return "custom:" + customId;
        }
        return BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
    }

    // ------------------------------------------------------------------
    // 价格修正 API / Price-modifier API
    // ------------------------------------------------------------------

    /**
     * 设置百分比折扣。 / Set a percentage discount.
     *
     * @param item    商品价格键（{@link #keyOf(ItemStack)}）/ product price key
     * @param percent 折扣百分比（0~100），如 50 表示降价 50% / discount percent (0~100); 50
     *                means -50%
     */
    public void setPercentDiscount(String item, int percent) {
        int clamped = Math.min(100, percent);
        setModifier(item, (100 - clamped) / 100.0, 0);
    }

    /**
     * 设置固定减价。 / Set a flat price reduction.
     *
     * @param amount 减少的价格 / amount to subtract from the price
     */
    public void setFlatReduction(String item, int amount) {
        setModifier(item, 1.0, amount);
    }

    /**
     * 设置价格乘数（{@code < 1} 打折，{@code > 1} 溢价）。
     * Set a price multiplier ({@code < 1} discounts, {@code > 1} surge-prices).
     */
    public void setMultiplier(String item, double multiplier) {
        setModifier(item, Math.max(0.0, multiplier), 0);
    }

    /** 同时设置乘数与固定减价。 / Set both a multiplier and a flat reduction. */
    public void setModifier(String item, double multiplier, int flatReduction) {
        if (item == null || item.isEmpty()) {
            return;
        }
        this.modifiers.put(item, new PriceModifier(Math.max(0.0, multiplier), Math.max(0, flatReduction)));
        this.sync();
    }

    /**
     * 设置「每次购买后按绝对金额变动」的下一档价格（涨价 / 降价共用这一条实现）。
     *
     * <p>
     * 涨价用乘数表达（{@code 目标价 / 基础价}），降价用固定减价表达，两者都能被
     * {@link #effectivePrice} 精确还原；目标价夹在 {@code [0, Integer.MAX_VALUE]}，
     * 因此商品最低降到 0、涨价也不会溢出 int。
     *
     * <p>
     * 自定义职业商店的「每次购买涨价 / 降价」走这个方法（{@code maxChanges} 即那里的
     * 「价格变动次数」）。
     *
     * @param item           商品价格键 / product price key
     * @param basePrice      基础价 / base price
     * @param amountPerBuy   每次购买后变动多少 / price change per purchase
     * @param raise          true = 涨价；false = 降价 / true: surge, false: discount
     * @param purchasedCount 已购买次数（含本次） / purchases so far, including this one
     */
    public void setSteppedPrice(String item, int basePrice, int amountPerBuy, boolean raise,
            int purchasedCount) {
        setSteppedPrice(item, basePrice, amountPerBuy, raise, purchasedCount, 0);
    }

    /**
     * 同 {@link #setSteppedPrice(String, int, int, boolean, int)}，但可限制价格变动次数。
     *
     * @param maxChanges 最多变动多少次；{@code <= 0} 表示不受限制。达到上限后价格不再变动
     *                  （涨价停在上限值、降价停在下限值）。
     */
    public void setSteppedPrice(String item, int basePrice, int amountPerBuy, boolean raise,
            int purchasedCount, int maxChanges) {
        if (item == null || item.isEmpty() || basePrice <= 0 || amountPerBuy <= 0) {
            return;
        }
        int purchases = Math.max(0, purchasedCount);
        if (maxChanges > 0) {
            // 达到变动次数上限后冻结价格
            purchases = Math.min(purchases, maxChanges);
        }
        long target = raise
                ? (long) basePrice + (long) amountPerBuy * purchases
                : (long) basePrice - (long) amountPerBuy * purchases;
        // 夹到 [0, int 上限]：商品最低降到 0，涨价不溢出
        target = Math.max(0, Math.min(Integer.MAX_VALUE, target));
        if (raise) {
            setModifier(item, (double) target / (double) basePrice, 0);
        } else {
            setModifier(item, 1.0, (int) Math.min(Integer.MAX_VALUE, (long) basePrice - target));
        }
    }

    /** 移除某件商品的价格修正。 / Remove the modifier for a single item. */
    public void clearModifier(String item) {
        if (item != null && this.modifiers.remove(item) != null) {
            this.sync();
        }
    }

    /** 清空所有价格修正（保留购买次数）。 / Clear all price modifiers (keeps purchase counts). */
    public void clearAllModifiers() {
        if (!this.modifiers.isEmpty()) {
            this.modifiers.clear();
            this.sync();
        }
    }

    public boolean hasModifier(String item) {
        return item != null && this.modifiers.containsKey(item);
    }

    /** 计算某个基础价格经过修正后的实际价格。 / Compute the effective price for a base price. */
    public int effectivePrice(String item, int basePrice) {
        PriceModifier modifier = item == null ? null : this.modifiers.get(item);
        if (modifier == null) {
            return basePrice;
        }
        double price = basePrice * modifier.multiplier - modifier.flatReduction;
        return Math.max(0, (int) Math.round(price));
    }

    /** 按物品栈计算实际价格（自动取 {@link #keyOf(ItemStack)} 作为价格键）。 */
    public int effectivePrice(ItemStack stack, int basePrice) {
        return effectivePrice(keyOf(stack), basePrice);
    }

    /**
     * 便捷方法：根据商店条目的物品与基础价格计算实际价格。 / Convenience: resolve a {@link ShopEntry}'s
     * effective price.
     */
    public int effectivePrice(@NotNull ShopEntry entry) {
        return effectivePrice(keyOf(entry.stack()), entry.price());
    }

    // ------------------------------------------------------------------
    // 购买次数 API / Purchase-count API
    // ------------------------------------------------------------------

    /** 某件商品已被购买的次数。 / How many times an item has been purchased. */
    public int getPurchaseCount(String item) {
        return item == null ? 0 : this.purchaseCounts.getOrDefault(item, 0);
    }

    /** 记录一次购买并返回更新后的次数。 / Record a purchase and return the new count. */
    public int recordPurchase(String item) {
        if (item == null || item.isEmpty()) {
            return 0;
        }
        int next = getPurchaseCount(item) + 1;
        this.purchaseCounts.put(item, next);
        this.sync();
        return next;
    }

    // ------------------------------------------------------------------
    // 同步 / Sync (round state only; not persisted to disk)
    // ------------------------------------------------------------------

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        CompoundTag modifiersTag = new CompoundTag();
        for (Map.Entry<String, PriceModifier> entry : this.modifiers.entrySet()) {
            CompoundTag modifierTag = new CompoundTag();
            modifierTag.putDouble("mul", entry.getValue().multiplier);
            modifierTag.putInt("flat", entry.getValue().flatReduction);
            modifiersTag.put(entry.getKey(), modifierTag);
        }
        tag.put("modifiers", modifiersTag);

        CompoundTag purchasesTag = new CompoundTag();
        for (Map.Entry<String, Integer> entry : this.purchaseCounts.entrySet()) {
            purchasesTag.putInt(entry.getKey(), entry.getValue());
        }
        tag.put("purchases", purchasesTag);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.modifiers.clear();
        CompoundTag modifiersTag = tag.getCompound("modifiers");
        for (String key : modifiersTag.getAllKeys()) {
            CompoundTag modifierTag = modifiersTag.getCompound(key);
            this.modifiers.put(key, new PriceModifier(modifierTag.getDouble("mul"), modifierTag.getInt("flat")));
        }

        this.purchaseCounts.clear();
        CompoundTag purchasesTag = tag.getCompound("purchases");
        for (String key : purchasesTag.getAllKeys()) {
            this.purchaseCounts.put(key, purchasesTag.getInt(key));
        }
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 仅同步局内状态，不写入磁盘 / round-only state, not persisted to disk
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
    }

    /** 单件商品的价格修正。 / Price modifier for a single item. */
    private record PriceModifier(double multiplier, int flatReduction) {
    }
}
