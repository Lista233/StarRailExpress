package org.agmas.noellesroles.content.item.bettor;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.roles.bettor.BettorPayload;
import org.agmas.noellesroles.role.qust.roles.bettor.BettorPlayerComponent;

/**
 * 恶魔轮盘 - 筹客专属物品
 * <p>
 * 右键开始滚动数字（1-1000），再右键停止并消耗，根据最终随机数获得不同效果。
 */
public class DevilRouletteItem extends Item {

    public DevilRouletteItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (level.isClientSide()) {
            // 客户端直接放行，状态由服务端控制
            return InteractionResultHolder.sidedSuccess(stack, true);
        }
        if (!(user instanceof ServerPlayer sp)) {
            return InteractionResultHolder.fail(stack);
        }

        // 检查是否是筹客职业
        var gameWorld = SREGameWorldComponent.KEY.get(level);
        if (gameWorld == null || !gameWorld.isRole(sp, QUSTRoles.BETTOR)) {
            return InteractionResultHolder.fail(stack);
        }

        var comp = QUSTComponentKeys.Keys.BETTOR.maybeGet(sp).orElse(null);
        if (comp == null) {
            return InteractionResultHolder.fail(stack);
        }

        if (comp.isRolling()) {
            // 第二次右键：停止滚动，生成结果，消耗物品
            int result = comp.stopAndApplyResult(sp);

            // 播放经验升级音效
            level.playSound(null, sp.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0F, 1.0F);

            // 消耗物品
            stack.shrink(1);

            // 发送结果给客户端显示
            String desc = comp.getResultDescription();
            ServerPlayNetworking.send(sp, new BettorPayload.RouletteResult(result, desc));

            return InteractionResultHolder.consume(stack);
        } else {
            // 第一次右键：开始滚动
            comp.setRolling(true);

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
}
