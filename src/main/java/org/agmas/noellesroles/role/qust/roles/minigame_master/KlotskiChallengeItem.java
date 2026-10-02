package org.agmas.noellesroles.role.qust.roles.minigame_master;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/**
 * 华容道挑战 — 小游戏达人商店物品（1200金币）
 * <p>
 * 右键随时开始/结束华容道挑战；未通关不消耗物品，可丢弃；
 * 通关后平民与义警阵营获得胜利（消耗由 QUSTHandlers 通关处理执行）。
 */
public class KlotskiChallengeItem extends Item {

    /** San 值低于此阈值时不能游玩（与小游戏券一致） */
    private static final float HALLUCINATION_THRESHOLD = 0.35f;

    public KlotskiChallengeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level world, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(player instanceof ServerPlayer sp)) {
            return InteractionResultHolder.pass(stack);
        }

        // San 值检查：低到产生幻觉时不能游玩
        float mood = io.wifi.starrailexpress.cca.SREPlayerMoodComponent.KEY.get(sp).getMood();
        if (mood < HALLUCINATION_THRESHOLD) {
            sp.displayClientMessage(
                    Component.translatable("message.minigame_master.low_san")
                            .withStyle(ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        // 打开华容道挑战界面（challenge = true）；未通关不消耗物品
        ServerPlayNetworking.send(sp, new ItemMinigamePayload.OpenItemGame("klotski", true));

        sp.displayClientMessage(
                Component.translatable("message.minigame_master.klotski_challenge_started")
                        .withStyle(ChatFormatting.GOLD),
                true);
        return InteractionResultHolder.success(stack);
    }
}
