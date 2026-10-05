package org.agmas.noellesroles.role.qust.roles.bettor;

import io.wifi.starrailexpress.api.RoleComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ThreadLocalRandom;

/**
 * 筹客玩家组件：跟踪恶魔轮盘的滚动状态和结果。
 */
public class BettorPlayerComponent implements RoleComponent {

    private final Player player;

    /** 是否正在滚动 */
    private boolean rolling = false;

    /** 最终结果（停止后设置） */
    private int lastResult = 0;

    /** 结果展示剩余 tick */
    private int resultDisplayTicks = 0;

    /** 结果描述翻译键 */
    private String resultDescription = "";

    public BettorPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public Player getPlayer() {
        return player;
    }

    @Override
    public void init() {
        rolling = false;
        lastResult = 0;
        resultDisplayTicks = 0;
        resultDescription = "";
    }

    @Override
    public void clear() {
        init();
    }

    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) {
        return spectator.getUUID().equals(player.getUUID());
    }

    private void sync() {
        if (!player.level().isClientSide && player instanceof ServerPlayer) {
            org.agmas.noellesroles.role.qust.QUSTComponentKeys.Keys.BETTOR.sync(player);
        }
    }

    public boolean isRolling() {
        return rolling;
    }

    public void setRolling(boolean rolling) {
        this.rolling = rolling;
        sync();
    }

    public int getLastResult() {
        return lastResult;
    }

    public int getResultDisplayTicks() {
        return resultDisplayTicks;
    }

    public String getResultDescription() {
        return resultDescription;
    }

    /**
     * 停止滚动，生成最终结果并应用效果。
     *
     * @return 最终随机数
     */
    public int stopAndApplyResult(ServerPlayer sp) {
        int result = ThreadLocalRandom.current().nextInt(1, 1001);
        this.lastResult = result;
        this.rolling = false;
        this.resultDisplayTicks = 200; // 10秒展示

        // 根据结果应用效果
        String desc = applyResult(sp, result);
        this.resultDescription = desc;
        sync();
        return result;
    }

    /**
     * 根据随机数结果应用效果，返回效果描述。
     */
    private String applyResult(ServerPlayer sp, int result) {
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

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        rolling = tag.getBoolean("Rolling");
        lastResult = tag.getInt("LastResult");
        resultDisplayTicks = tag.getInt("ResultDisplayTicks");
        resultDescription = tag.getString("ResultDescription");
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("Rolling", rolling);
        tag.putInt("LastResult", lastResult);
        tag.putInt("ResultDisplayTicks", resultDisplayTicks);
        tag.putString("ResultDescription", resultDescription);
    }

    @Override
    public void writeToSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }

    @Override
    public void readFromSyncNbt(@NotNull CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }
}
