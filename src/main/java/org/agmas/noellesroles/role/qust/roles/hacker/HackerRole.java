package org.agmas.noellesroles.role.qust.roles.hacker;

import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.CustomWinnerRole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameUtils.WinStatus;
import io.wifi.starrailexpress.index.TMMItems;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.agmas.noellesroles.init.FunnyItems;
import org.agmas.noellesroles.role.bouns.roles.ProgrammerRole;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * 黑客职业（林然） - 中立阵营
 * <ul>
 *   <li>中立阵营 (isNeutrals = true)</li>
 *   <li>不能使用杀手能力 (canUseKiller = false)</li>
 *   <li>伪装心情 (MoodType.FAKE)</li>
 *   <li>胜利条件：标记并发送场上3/4的人（上限15人）</li>
 *   <li>物品：干扰芯片 - 手持右键对视线内玩家进行标记，获取其IP/UUID等信息</li>
 *   <li>标记后8秒自动通知被标记者（延迟泄漏）</li>
 *   <li>商店：黑入电力系统（购买即关灯，与杀手关灯同价同冷却）、社会工程学钥匙(开锁器)、
 *       终端（与程序员同价同冷却，黑客也能用 /help 查看指令，见 TerminalScreen）</li>
 * </ul>
 */
public class HackerRole extends CustomWinnerRole {

    public HackerRole(ResourceLocation identifier, int color, boolean isInnocent, boolean canUseKiller,
                      MoodType moodType, int maxSprintTime, boolean canSeeTime) {
        super(identifier, color, isInnocent, canUseKiller, moodType, maxSprintTime, canSeeTime);
    }

    /**
     * 专属商店：黑入电力系统（购买即生效，不占背包）+ 开锁器 + 终端。
     * 关灯与开锁器沿用原版杀手商店物品的材质与定价，仅显示名不同；
     * 终端与程序员共用同一物品，价格/冷却完全一致（CD 挂在 FunnyItems.TERMINAL 上自然共享）。
     */
    @Override
    public List<ShopEntry> getShopEntries() {
        List<ShopEntry> shop = new ArrayList<>();

        // 黑入电力系统（断电）：购买即切断全场照明，走 useBlackout 的公共冷却与回放记录
        ItemStack blackoutDisplay = TMMItems.BLACKOUT.getDefaultInstance();
        blackoutDisplay.set(DataComponents.ITEM_NAME,
                Component.translatable("item.noellesroles.hacker.blackout"));
        shop.add(new ShopEntry(blackoutDisplay, SREConfig.instance().blackoutPrice, ShopEntry.Type.TOOL) {
            @Override
            public boolean onBuy(@NotNull Player player) {
                return SREPlayerShopComponent.useBlackout(player);
            }
        });

        // 社会工程学钥匙（开锁器）：普通物品条目，默认 onBuy 直接进背包
        ItemStack lockpickDisplay = TMMItems.LOCKPICK.getDefaultInstance();
        lockpickDisplay.set(DataComponents.ITEM_NAME,
                Component.translatable("item.noellesroles.hacker.lockpick"));
        shop.add(new ShopEntry(lockpickDisplay, SREConfig.instance().lockpickPrice, ShopEntry.Type.TOOL));

        // 终端：与程序员同价（TERMINAL_PRICE），canUseTerminal 不限职业，黑客可正常使用
        shop.add(new ShopEntry(FunnyItems.TERMINAL.getDefaultInstance(),
                ProgrammerRole.TERMINAL_PRICE, ShopEntry.Type.TOOL));

        return shop;
    }

    @Override
    public WinStatus checkWin(ServerPlayer player, WinStatus winStatus) {
        // 检查黑客是否达成胜利条件
        if (RoleUtils.isPlayerTheJob(player, this)) {
            var data = org.agmas.noellesroles.role.qust.QUSTComponentKeys.Keys.HACKER.maybeGet(player).orElse(null);
            if (data != null && data.hasWon()) {
                return WinStatus.CUSTOM;
            }
        }
        return WinStatus.NOT_MODIFY;
    }

    @Override
    public boolean didPlayerWin(ServerPlayer player, boolean original, WinStatus winStatus) {
        if (winStatus == WinStatus.CUSTOM && RoleUtils.isPlayerTheJob(player, this)) {
            // 注意：结算（recordWinStats）发生在 RoleMethodDispatcher.onEndGame 清空职业组件之后，
            // 此时重算 hasWon() 必然失败（sentPlayers 已被 clear），会把 switch 分支已判定的
            // 胜利覆盖为失败，导致结算 HUD 上黑客没有皇冠。
            // 因此这里不重算组件状态，改为校验 CustomWinnerID：它只在 checkWin 确认
            // hasWon() 为 true 后由 win(player) 写入本职业 path，且其他职业的 CUSTOM
            // 胜利不会写入该值（此时黑客仍正确判负）。
            var roundEnd = SREGameRoundEndComponent.KEY.get(player.level());
            return roundEnd != null
                    && this.identifier().getPath().equals(roundEnd.CustomWinnerID);
        }
        return original;
    }
}
