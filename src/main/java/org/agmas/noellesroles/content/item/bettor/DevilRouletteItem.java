package org.agmas.noellesroles.content.item.bettor;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.qust.roles.bettor.BettorPayload;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 恶魔轮盘 - 筹客物品
 * <p>
 * 可以被丢弃（按Q）和捡起，其他玩家可以捡起使用。
 * 使用者右键开始滚动数字（1-1000），再右键停止并根据最终随机数获得不同效果。
 * 每次使用随机返现 -30~+50 金币。
 * <p>
 * 滚动状态存储在物品 CustomData 中，使任何玩家都能使用。
 */
public class DevilRouletteItem extends Item {

    /** 返现范围下限（负数表示可能扣金币） */
    private static final int REFUND_MIN = -30;
    /** 返现范围上限 */
    private static final int REFUND_MAX = 50;

    public static final String TAG_ROLLING = "Rolling";

    public DevilRouletteItem(Properties properties) {
        super(properties);
    }

    /**
     * 获取物品的滚动状态
     */
    public static boolean isRolling(ItemStack stack) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        return tag.getBoolean(TAG_ROLLING);
    }

    /**
     * 设置物品的滚动状态
     */
    public static void setRolling(ItemStack stack, boolean rolling) {
        CustomData customData = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putBoolean(TAG_ROLLING, rolling);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(user instanceof ServerPlayer sp)) {
            return InteractionResultHolder.fail(stack);
        }

        boolean isRolling = isRolling(stack);

        if (isRolling) {
            // 第二次右键：停止滚动，生成结果（效果作用于使用者）
            int result = ThreadLocalRandom.current().nextInt(1, 1001);
            setRolling(stack, false);

            // 根据结果应用效果
            String desc = applyResultPublic(sp, result);

            // 随机返现 -30~+50 金币给使用者
            int refund = ThreadLocalRandom.current().nextInt(REFUND_MIN, REFUND_MAX + 1);
            if (refund != 0) {
                io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(sp).addToBalance(refund);
                // 发送金币变动给 HUD 显示
                ServerPlayNetworking.send(sp, new BettorPayload.CoinChange(refund));
            }

            // 播放经验升级音效
            level.playSound(null, sp.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 消耗物品
            stack.shrink(1);

            // 发送结果给客户端显示（包含金币变动）
            ServerPlayNetworking.send(sp, new BettorPayload.RouletteResult(result, desc, refund));

            return InteractionResultHolder.consume(stack);
        } else {
            // 第一次右键：开始滚动
            setRolling(stack, true);

            // 播放村庄英雄音效
            level.playSound(null, sp.blockPosition(),
                    SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 1.2F);

            // 通知客户端开始滚动动画
            ServerPlayNetworking.send(sp, new BettorPayload.StartRolling());

            sp.displayClientMessage(
                    Component.translatable("message.bettor.rolling_start")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW), true);

            return InteractionResultHolder.consume(stack);
        }
    }

    /**
     * 根据随机数结果应用效果，返回效果描述。（公开方法供 QUSTHandlers 调用）
     */
    public static String applyResultPublic(ServerPlayer sp, int result) {
        if (result == 1) {
            // 直接游戏获胜
            if (sp.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                org.agmas.noellesroles.utils.RoleUtils.customWinnerWin(
                        sl,
                        io.wifi.starrailexpress.game.GameUtils.WinStatus.CUSTOM,
                        "bettor",
                        java.util.OptionalInt.of(new java.awt.Color(255, 215, 0).getRGB())
                );
            }
            return "message.bettor.result.win";
        } else if (result >= 2 && result <= 39) {
            // 获得德林加手枪
            io.wifi.starrailexpress.util.SREItemUtils.insertStackInFreeSlot(
                    sp, io.wifi.starrailexpress.index.TMMItems.DERRINGER.getDefaultInstance());
            return "message.bettor.result.derringer";
        } else if (result >= 40 && result <= 89) {
            // 直接死亡
            io.wifi.starrailexpress.game.GameUtils.killPlayer(
                    sp, true, null,
                    io.wifi.starrailexpress.game.GameConstants.DeathReasons.GUN_SHOT);
            return "message.bettor.result.death";
        } else if (result >= 90 && result <= 189) {
            // 获得一层护盾
            io.wifi.starrailexpress.cca.SREArmorPlayerComponent.KEY.get(sp).giveArmor();
            return "message.bettor.result.shield";
        } else if (result >= 190 && result <= 239) {
            // 永久一把手枪（左轮）
            io.wifi.starrailexpress.util.SREItemUtils.insertStackInFreeSlot(
                    sp, io.wifi.starrailexpress.index.TMMItems.REVOLVER.getDefaultInstance());
            return "message.bettor.result.revolver";
        } else if (result >= 240 && result <= 399) {
            // 无敌效果10秒
            sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.DAMAGE_RESISTANCE,
                    200, 4, true, false, true)); // 5级抗性 = 近乎无敌
            return "message.bettor.result.invincible";
        } else if (result >= 400 && result <= 689) {
            // 速度I 15秒
            sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,
                    300, 0, true, false, true));
            return "message.bettor.result.speed";
        } else {
            // 690-1000：加速饼干（watheextraitems:flow_dust）
            var flowDust = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(net.minecraft.resources.ResourceLocation.parse("watheextraitems:flow_dust"));
            if (flowDust != net.minecraft.world.item.Items.AIR) {
                io.wifi.starrailexpress.util.SREItemUtils.insertStackInFreeSlot(
                        sp, flowDust.getDefaultInstance());
            }
            return "message.bettor.result.cookie";
        }
    }
}
