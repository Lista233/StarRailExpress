package org.agmas.noellesroles.role.qust.roles.minigame_master;

import io.wifi.starrailexpress.content.item.api.SREItemProperties;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 击退剑 — 小游戏达人商店武器（150金币）
 * <p>
 * 持有左键攻击玩家只造成击退，不造成任何伤害。
 * 每次击退消耗 1 点耐久（共 8 点）。
 */
public class KnockbackSwordItem extends Item implements SREItemProperties.LeftClickHurtable {

    /** 最大耐久 */
    public static final int KNOCKBACK_SWORD_MAX_DURABILITY = 8;
    /** 击退力度：与原版普通攻击一致（基础 0.4，无附魔加成） */
    private static final double KNOCKBACK_STRENGTH = 0.4;

    public KnockbackSwordItem(Properties properties) {
        super(properties);
    }

    public static int maxDurability() {
        return KNOCKBACK_SWORD_MAX_DURABILITY;
    }

    @Override
    public boolean onServerAttack(ServerPlayer attacker, ServerPlayer target, ItemStack mainhandItem) {
        // 只击退，不造成伤害：使用原版 knockback 逻辑（含速度减半），距离与普通攻击一致
        target.knockback(KNOCKBACK_STRENGTH,
                attacker.getX() - target.getX(),
                attacker.getZ() - target.getZ());
        target.hurtMarked = true;
        target.setLastHurtByMob(attacker);

        // 每次击退消耗 1 点耐久
        mainhandItem.hurtAndBreak(1, attacker, LivingEntity.getSlotForHand(InteractionHand.MAIN_HAND));

        // 取消原版攻击（无伤害、无击杀）
        return false;
    }
}
