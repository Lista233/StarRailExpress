package org.agmas.noellesroles.role.qust.roles.minigame_master;

import io.wifi.starrailexpress.content.minigame.QuestMinigame;
import io.wifi.starrailexpress.content.minigame.QuestMinigames;
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

import java.util.List;
import java.util.Random;

/**
 * 小游戏券 — 右键使用开启随机小游戏
 */
public class MinigameTicketItem extends Item {

    private static final Random RANDOM = new Random();

    /** San 值低于此阈值时不能游玩小游戏（对应视觉幻觉效果启动的阈值） */
    private static final float HALLUCINATION_THRESHOLD = 0.35f;

    public MinigameTicketItem(Properties properties) {
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

        // 随机选一个小游戏
        List<QuestMinigame> allGames = QuestMinigames.getAll();
        if (allGames.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }
        String minigameId = allGames.get(RANDOM.nextInt(allGames.size())).id();
        // 发送 S2C 包打开小游戏（非挑战模式）
        ServerPlayNetworking.send(sp, new ItemMinigamePayload.OpenItemGame(minigameId, false));

        // 消耗 1 张小游戏券
        stack.shrink(1);

        sp.displayClientMessage(
                Component.translatable("message.minigame_master.minigame_started")
                        .withStyle(ChatFormatting.GREEN),
                true);
        return InteractionResultHolder.consume(stack);
    }
}
