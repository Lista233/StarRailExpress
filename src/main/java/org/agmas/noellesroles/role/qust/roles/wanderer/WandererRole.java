package org.agmas.noellesroles.role.qust.roles.wanderer;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import io.wifi.starrailexpress.util.TrueFalseResult;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * 游荡者（Wanderer）
 * <p>
 * 平民阵营。初次死亡后不进入旁观者，而是传送回自己的房间、以一个默认隐身的普通平民
 * 状态继续活动（保持正常物理与重力，可自由行走、可碰撞，不能飞行；结算视同已死亡，
 * 不影响杀手杀完其他人的胜负判定，且进入死者语音频道）。
 * 存活时可按 G 灵魂出窍（7s 自由视角），死亡后可按 G 显形/隐身，解锁幽灵商店。
 * 显形后被 8 格内的其他玩家看到会在 0.8s 自动重新隐身。
 * 该状态下被其他玩家再次击杀则彻底死亡，进入旁观者模式。
 */
public class WandererRole extends NormalRole {

    public WandererRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                        MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /** 死亡时进入隐身平民状态而非旁观者 */
    @Override
    public void onDeath(Player victim, boolean spawnBody, @Nullable Player killer,
                        ResourceLocation deathReason, boolean forceDeath) {
        if (!(victim instanceof ServerPlayer sp)) return;
        var comp = QUSTComponentKeys.Keys.WANDERER.maybeGet(sp).orElse(null);
        if (comp == null) return;

        // 再次死亡（隐身平民被击杀 / 强制死亡）：彻底死亡，进入旁观者模式
        if (comp.isGhost()) {
            enterFinalDeath(sp, comp);
            return;
        }

        // 初次死亡：立即结束灵魂出窍（防止状态残留），进入「隐身平民」状态。
        // 隐身逻辑完全独立于幽灵状态：只用 WandererPlayerComponent 记录状态，
        // 并用原版 invisible 标记实现隐身，不再依赖 GhostStateComponent / GHOST_STATE 效果。
        comp.endSoulOut();
        comp.setGhostState(true);   // 解锁幽灵商店 + 应用隐身

        // 初次死亡后金币变为原来的 1/4
        var shopComp = SREPlayerShopComponent.KEY.get(sp);
        int newBalance = shopComp.balance / 4;
        shopComp.setBalance(newBalance);
        sp.displayClientMessage(
                Component.translatable("message.wanderer_qust.coins_quartered", newBalance)
                        .withStyle(ChatFormatting.GOLD),
                true);

        // 死亡链的最后会把玩家切到旁观者模式，这里延后一 tick 再传送回自己的房间并
        // 恢复冒险模式。WandererPlayerComponent.serverTick 还会做兜底：只要处于隐身平民
        // 状态却被切成旁观，就强制拉回冒险模式。死亡后若玩家已离线则不进行后续逻辑，
        // 状态由组件持久化，重新上线后继续生效。
        var server = sp.getServer();
        if (server != null) {
            server.execute(() -> {
                if (sp.isRemoved() || sp.connection == null) return;
                var c = QUSTComponentKeys.Keys.WANDERER.maybeGet(sp).orElse(null);
                if (c == null || !c.isGhost() || c.isFinalDeath()) return;
                c.returnToRoomAsHiddenCivilian();
            });
        }
    }

    /**
     * 彻底死亡：清除隐身并进入旁观者模式。
     * 隐身平民被击杀、或被强制死亡时触发。
     */
    public static void enterFinalDeath(ServerPlayer sp, WandererPlayerComponent comp) {
        if (comp.isFinalDeath()) return;
        comp.setFinalDeath(true);
        comp.setGhostVisible(false);
        // 解除隐身与物理残留（setFinalDeath 内部已 applyInvisibility，这里再显式归位一次）
        sp.setInvisible(false);
        sp.noPhysics = false;
        sp.setNoGravity(false);
        sp.setGameMode(GameType.SPECTATOR);
        // 与正常死亡一致：清理职业飞行技能残留，否则旁观者飞不起来
        GameUtils.normalizeSpectatorFlightAbilities(sp);
        sp.displayClientMessage(
                Component.translatable("message.wanderer_qust.final_death")
                        .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD),
                true);
    }

    /**
     * 幽灵特殊商店：初次死亡后解锁，可购买一次撬棍与一次便签。
     * 未死亡时返回 null，与普通平民一致（无专属商店）。
     */
    @Override
    public List<ShopEntry> getShopEntries(@Nullable Player player) {
        if (player == null) return null;
        var comp = QUSTComponentKeys.Keys.WANDERER.maybeGet(player).orElse(null);
        if (comp == null || !comp.isGhost() || comp.isFinalDeath()) {
            return null;
        }
        ArrayList<ShopEntry> shop = new ArrayList<>();
        // 撬棍：100 金币，限购一次
        if (!comp.hasBoughtCrowbar()) {
            shop.add(new ShopEntry(TMMItems.CROWBAR.getDefaultInstance(),
                    100, ShopEntry.Type.TOOL) {
                @Override
                public boolean onBuy(@NotNull Player p) {
                    var c = QUSTComponentKeys.Keys.WANDERER.maybeGet(p).orElse(null);
                    if (c == null || c.hasBoughtCrowbar()) return false;
                    if (!super.onBuy(p)) return false;
                    c.setBoughtCrowbar(true);
                    return true;
                }
            });
        }
        // 便签：不限购，每次给 2 个，价格每次翻倍（基础 150）
        int notePrice = comp.getNotePrice();
        shop.add(new ShopEntry(new ItemStack(TMMItems.NOTE, WandererPlayerComponent.NOTE_PER_PURCHASE),
                notePrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player p) {
                var c = QUSTComponentKeys.Keys.WANDERER.maybeGet(p).orElse(null);
                if (c == null) return false;
                if (!super.onBuy(p)) return false;
                c.incrementNotePurchase();
                return true;
            }
        });
        return shop;
    }

    /** 隐身平民状态下禁止拾取任何物品（含枪械） */
    @Override
    public TrueFalseResult onPickUpItem(Player player, ItemStack item) {
        var comp = QUSTComponentKeys.Keys.WANDERER.maybeGet(player).orElse(null);
        if (comp != null && comp.isGhost() && !comp.isFinalDeath()) {
            return TrueFalseResult.FALSE;
        }
        return TrueFalseResult.PASS;
    }

    // ==================== 死后禁止击杀其他玩家 ====================

    /** 是否处于「隐身平民」状态（初次死亡后、彻底死亡前）。 */
    private static boolean isHiddenCivilian(Player player) {
        var comp = QUSTComponentKeys.Keys.WANDERER.maybeGet(player).orElse(null);
        return comp != null && comp.isGhost() && !comp.isFinalDeath();
    }

    /** 死后禁止使用枪械击杀其他玩家 */
    @Override
    public boolean onUseGun(Player player) {
        return !isHiddenCivilian(player);
    }

    /** 死后禁止使用德林加手枪击杀其他玩家 */
    @Override
    public boolean onUseDerringer(Player player) {
        return !isHiddenCivilian(player);
    }

    /** 死后禁止使用刀击杀其他玩家 */
    @Override
    public boolean onUseKnife(Player player) {
        return !isHiddenCivilian(player);
    }

    /** 死后禁止近战攻击其他玩家（撬棍 / 球棒等），取消原版攻击 */
    @Override
    public InteractionResult leftClickEntity(Player player, Entity victim) {
        if (isHiddenCivilian(player) && victim instanceof Player) {
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }
}
