package org.agmas.noellesroles.role.qust.roles.mascot;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerMoodComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTConfig;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.agmas.noellesroles.utils.RoleUtils;
import org.jetbrains.annotations.Nullable;
import org.ladysnake.cca.api.v3.component.tick.ClientTickingComponent;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.List;
import java.util.OptionalInt;

/**
 * 吉祥物玩家组件
 * <ul>
 *   <li>完成任务后给周围玩家恢复心情(25%)和金币(25)</li>
 *   <li>完成指定数量任务 → 乘客阵营直接胜利</li>
 *   <li>90秒后开始周期性发光（2秒发光，12秒冷却循环）</li>
 *   <li>发光状态通过 CCA 同步给所有客户端，供本能透视读取</li>
 *   <li>发光时被刀杀/棍杀 → 杀手直接胜利</li>
 *   <li>非发光时或其他方式被杀 → 杀手获得200金币</li>
 * </ul>
 */
public class MascotPlayerComponent implements RoleComponent, ServerTickingComponent, ClientTickingComponent {

    private final Player player;

    public MascotPlayerComponent(Player player) {
        this.player = player;
    }

    private int completedTaskCnt = 0;
    private int requiredTaskCnt = 10;
    private int glowCutDownTimer = 90 * 20;
    private Boolean isGlowing = false;
    private double buffRange = 15.0;
    private int glowCutDownCD = 12 * 20;
    private int glowDuration = (int) (2 * 20);
    private int glowDurationTimer = glowDuration;

    @Override
    public void init() {
        completedTaskCnt = 0;
        requiredTaskCnt = QUSTConfig.instance().mascotRequiredTaskCount;
        isGlowing = false;
        buffRange = QUSTConfig.instance().mascotBuffRange;
        player.removeEffect(MobEffects.GLOWING);
        glowCutDownTimer = QUSTConfig.instance().mascotGlowCutDownSeconds * 20;
        glowCutDownCD = QUSTConfig.instance().mascotGlowCutDownCDSeconds * 20;
        glowDuration = QUSTConfig.instance().mascotGlowDurationSeconds * 20;
        glowDurationTimer = glowDuration;
    }

    public int getGlowCutDownTimer() { return glowCutDownTimer; }
    public int getGlowDurationTimer() { return glowDurationTimer; }
    public void decGlowCutDownTimer() { this.glowCutDownTimer--; }
    public Boolean isCutDownFinished() { return this.glowCutDownTimer <= 0; }
    public Boolean isGlowDurationTimerFinished() { return this.glowDurationTimer <= 0; }
    private void addCompletedTaskCnt() { this.completedTaskCnt++; }
    public int getCompletedTaskCnt() { return completedTaskCnt; }
    public int getRequiredTaskCnt() { return requiredTaskCnt; }

    public void callOnFinishQuest(Player player, String quest) {
        SRE.LOGGER.info("完成任务：" + quest);
        QUSTComponentKeys.Keys.MASCOT.get(player).addCompletedTaskCnt();
        SRE.LOGGER.info("当前已完成:" + completedTaskCnt + "项任务");
        handleMascotNearByBenefit();
        sync();
        if (completedTaskCnt >= requiredTaskCnt && player.level() instanceof ServerLevel level) {
            SRE.LOGGER.info("当前任务数量:" + completedTaskCnt + "大于目标任务数量：" + requiredTaskCnt);
            customWinWithPassenger(level, "mascot",
                    OptionalInt.of(new java.awt.Color(238, 221, 130).getRGB()));
        }
    }

    public void handleMascotNearByBenefit() {
        if (this.player instanceof ServerPlayer serverPlayer) {
            ServerLevel level = serverPlayer.serverLevel();
            for (Player target : level.players()) {
                if (target == serverPlayer) continue;
                if (target.distanceToSqr(serverPlayer) > buffRange * buffRange) continue;
                // 周围人恢复完成任务的四分之一的心情
                var pmd = SREPlayerMoodComponent.KEY.get(target);
                pmd.addMood(GameConstants.MOOD_GAIN * 0.25f);
                // 周围人获得完成任务25金币
                var shop = SREPlayerShopComponent.KEY.get(target);
                shop.addToBalance(25);
            }
            level.playSound(null, serverPlayer.getX(), serverPlayer.getY(), serverPlayer.getZ(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 1.0f, 1.0f);
        }
    }

    private void sync() {
        if (!player.level().isClientSide) {
            QUSTComponentKeys.Keys.MASCOT.sync(player);
        }
    }

    @Override
    public Player getPlayer() { return player; }

    /**
     * 广播同步给所有玩家，使杀手本能透视等客户端逻辑也能读取到
     * 吉祥物的发光状态（isGlowing）。
     */
    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) { return true; }

    @Override
    public void clear() {}

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("CompletedTaskCnt", this.completedTaskCnt);
        tag.putInt("GlowCutDownTimer", this.glowCutDownTimer);
        tag.putInt("MaxTaskCnt", this.requiredTaskCnt);
        tag.putInt("GlowDurationTimer", this.glowDurationTimer);
        tag.putBoolean("IsGlowing", this.isGlowing);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        this.completedTaskCnt = tag.getInt("CompletedTaskCnt");
        this.glowCutDownTimer = tag.getInt("GlowCutDownTimer");
        this.requiredTaskCnt = tag.getInt("MaxTaskCnt");
        this.glowDurationTimer = tag.getInt("GlowDurationTimer");
        this.isGlowing = tag.contains("IsGlowing") && tag.getBoolean("IsGlowing");
    }

    @Override
    public void clientTick() {}

    @Override
    public void serverTick() {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent == null) return;
        if (!gameWorldComponent.isRunning()) return;
        if (!gameWorldComponent.isRole(player, QUSTRoles.MASCOT)) return;

        boolean shouldSync = false;
        if (!this.isCutDownFinished()) {
            this.decGlowCutDownTimer();
            if (this.getGlowCutDownTimer() % 20 == 0) {
                shouldSync = true;
            }
        } else {
            handleGlowing();
            if (this.glowDurationTimer % 20 == 0) {
                shouldSync = true;
            }
        }

        if (shouldSync) {
            sync();
        }
    }

    public void handleGlowing() {
        boolean currentlyGlowing = this.player.hasEffect(MobEffects.GLOWING);
        if (isGlowDurationTimerFinished()) {
            this.glowCutDownTimer = glowCutDownCD;
            this.glowDurationTimer = glowDuration;
            this.player.removeEffect(MobEffects.GLOWING);
            this.isGlowing = false;
            sync();
            return;
        }
        if (!currentlyGlowing) {
            boolean added = this.player.addEffect(new MobEffectInstance(MobEffects.GLOWING, glowDuration, 0, true, true, true));
            this.isGlowing = added;
            if (added) {
                sync();
            }
        } else {
            this.glowDurationTimer--;
        }
    }

    /**
     * 是否正在发光。该状态通过 CCA 同步广播给所有客户端，
     * 供本能透视客户端逻辑读取。
     */
    public boolean isGlowing() { return this.isGlowing; }

    @Override
    public void readFromNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {}

    @Override
    public void writeToNbt(CompoundTag compoundTag, HolderLookup.Provider provider) {}

    public void customWinWithPassenger(ServerLevel serverWorld, String winnerId, OptionalInt winnerColor) {
        var roundComponent = SREGameRoundEndComponent.KEY.get(serverWorld);
        if (winnerId != null && roundComponent != null) {
            roundComponent.CustomWinnerID = winnerId;
        }
        if (winnerColor != null && !winnerColor.isEmpty() && roundComponent != null) {
            roundComponent.CustomWinnerColor = winnerColor.getAsInt();
        }

        var gameComponent = SREGameWorldComponent.KEY.get(serverWorld);
        SREGameRoundEndComponent endComponent = SREGameRoundEndComponent.KEY.get(serverWorld);
        List<ServerPlayer> players = serverWorld.players();
        endComponent.players.clear();

        for (Player serverPlayer : players) {
            SRERole role = gameComponent.getRole(serverPlayer);
            endComponent.players.add(endComponent.new RoundEndData(serverPlayer.getGameProfile(),
                    !GameUtils.isPlayerAliveAndSurvival(player),
                    role.isInnocent()));
        }
        endComponent.setWinStatus(GameUtils.WinStatus.CUSTOM);
        endComponent.sync();
        GameUtils.stopGame(serverWorld);
    }

    public void customWinWithKiller(ServerLevel serverWorld, String winnerId, OptionalInt winnerColor) {
        var roundComponent = SREGameRoundEndComponent.KEY.get(serverWorld);
        if (winnerId != null && roundComponent != null) {
            roundComponent.CustomWinnerID = winnerId;
        }
        if (winnerColor != null && !winnerColor.isEmpty() && roundComponent != null) {
            roundComponent.CustomWinnerColor = winnerColor.getAsInt();
        }

        var gameComponent = SREGameWorldComponent.KEY.get(serverWorld);
        SREGameRoundEndComponent endComponent = SREGameRoundEndComponent.KEY.get(serverWorld);
        List<ServerPlayer> players = serverWorld.players();
        endComponent.players.clear();

        for (Player serverPlayer : players) {
            SRERole role = gameComponent.getRole(serverPlayer);
            endComponent.players.add(endComponent.new RoundEndData(serverPlayer.getGameProfile(),
                    !GameUtils.isPlayerAliveAndSurvival(player),
                    !role.isInnocent()));
        }
        endComponent.setWinStatus(GameUtils.WinStatus.CUSTOM);
        endComponent.sync();
        GameUtils.stopGame(serverWorld);
    }

    public void callOnDeath(Player victim, boolean spawnBody, @Nullable Player killer, ResourceLocation deathReason, boolean forceDeath) {
        SREGameWorldComponent gameWorldComponent = SREGameWorldComponent.KEY.get(player.level());
        if (gameWorldComponent == null) return;
        if (!gameWorldComponent.isRunning()) return;
        if (!gameWorldComponent.isRole(player, QUSTRoles.MASCOT)) return;
        if (killer != null) {
            SRERole playerRole = RoleUtils.getPlayerRole(killer);
            if (!playerRole.isInnocent()) {
                handleDeathByKiller(killer, deathReason);
            }
        }
    }

    public void handleDeathByKiller(Player killer, ResourceLocation deathReason) {
        // 发光状态被刀杀或棍杀 → 杀手直接胜利
        // 其他武器或不在发光状态 → 杀手额外获得200金币
        if (this.isGlowing() && (deathReason.equals(GameConstants.DeathReasons.KNIFE) || deathReason.equals(GameConstants.DeathReasons.BAT))) {
            if (player.level() instanceof ServerLevel serverWorld) {
                customWinWithKiller(serverWorld, "mascot_lose",
                        OptionalInt.of(new java.awt.Color(167, 21, 21).getRGB()));
            }
        } else {
            SREPlayerShopComponent.KEY.get(killer).addToBalance(200);
        }
    }
}
