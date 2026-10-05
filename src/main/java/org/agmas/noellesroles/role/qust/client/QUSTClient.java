package org.agmas.noellesroles.role.qust.client;

import org.agmas.noellesroles.role.qust.roles.american_police.AmericanPoliceClientState;
import org.agmas.noellesroles.role.qust.roles.american_police.AmericanPoliceHud;
import org.agmas.noellesroles.role.qust.roles.dragon_girl.DragonGirlClientState;
import org.agmas.noellesroles.role.qust.roles.dragon_girl.DragonGirlHud;
import org.agmas.noellesroles.role.qust.roles.mascot.MascotHud;
import org.agmas.noellesroles.role.qust.roles.mascot.MascotInstincts;
import org.agmas.noellesroles.role.qust.roles.pressure_monster.PressureMonsterHud;
import org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderHud;
import org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderRole;
import org.agmas.noellesroles.role.qust.roles.super_recorder.SuperRecorderScreen;
import org.agmas.noellesroles.role.qust.roles.wanderer.WandererHud;
import org.agmas.noellesroles.role.qust.roles.wanderer.WandererClientHandlers;
import org.agmas.noellesroles.role.qust.roles.super_doctor.SuperDoctorHud;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.init.ModItems;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionResultHolder;

/**
 * QUST 客户端注册入口。
 * <p>
 * 在 {@code NoellesrolesClient.onInitializeClient()} 中加一行
 * {@code QUSTClient.register()} 即可。
 */
public class QUSTClient {

    public static void register() {
        DragonGirlHud.register();
        DragonGirlClientState.register();
        MascotHud.register();
        MascotInstincts.register();
        AmericanPoliceHud.register();
        AmericanPoliceClientState.register();
        PressureMonsterHud.register();
        SuperRecorderHud.register();
        WandererHud.register();
        SuperDoctorHud.register();
        org.agmas.noellesroles.role.qust.roles.hacker.HackerHud.register();
        org.agmas.noellesroles.role.qust.roles.hacker.HackerClientHandlers.register();
        org.agmas.noellesroles.role.qust.roles.bettor.BettorHud.register();
        org.agmas.noellesroles.role.qust.roles.bettor.BettorClientHandlers.register();

        // 注册客户端 tick 事件：驱动游荡者灵魂出窍自由相机
        ClientTickEvents.END_CLIENT_TICK.register(client -> clientTick());

        // 超级记录员：右键记录笔记打开标记界面
        // 真相之书右键由 TruthBookItem.use() 自行处理（打开 TruthBookScreen）
        registerSuperRecorderItemUse();
    }

    /**
     * 超级记录员手持记录笔记右键时，
     * 直接在客户端打开标记界面，并返回 success 以取消原版行为。
     * <p>真相之书右键不在此拦截，由 {@code TruthBookItem.use()} → {@code TruthBookScreen} 处理。
     * <p>原版 {@code WrittenNoteItem.use()} 只对 {@code RECORDER} 生效，不认
     * {@code SUPER_RECORDER}，因此这里单独处理。
     */
    private static void registerSuperRecorderItemUse() {
        UseItemCallback.EVENT.register((player, world, hand) -> {
            var stack = player.getItemInHand(hand);
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(world);
            if (gameWorld == null || !gameWorld.isRole(player, QUSTRoles.SUPER_RECORDER)) {
                return InteractionResultHolder.pass(stack);
            }
            // 只拦截记录笔记，真相之书交给 TruthBookItem.use() 处理
            if (stack.is(ModItems.WRITTEN_NOTE)) {
                Minecraft.getInstance().setScreen(new SuperRecorderScreen(0));
                return InteractionResultHolder.success(stack);
            }
            return InteractionResultHolder.pass(stack);
        });
    }

    /** 客户端 tick 事件 */
    public static void clientTick() {
        WandererClientHandlers.tick();
    }
}
