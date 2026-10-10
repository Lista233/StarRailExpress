package org.agmas.noellesroles.role.qust.roles.wanderer;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

/**
 * 游荡者玩家组件
 * <ul>
 *   <li>灵魂出窍状态与计时</li>
 *   <li>幽灵状态（死亡后）</li>
 *   <li>幽灵显隐控制</li>
 *   <li>彻底死亡标记</li>
 * </ul>
 */
public class WandererPlayerComponent implements RoleComponent, ServerTickingComponent {

    private final Player player;

    public void sync() {
        QUSTComponentKeys.Keys.WANDERER.sync(player);
    }

    // ── 灵魂出窍（存活时 G 键） ──
    /** 是否正在灵魂出窍 */
    private boolean soulOutActive = false;
    /** 灵魂出窍剩余 tick */
    private int soulOutRemainingTicks = 0;

    // ── 幽灵状态（死亡后） ──
    /** 是否已死亡进入幽灵状态 */
    private boolean isGhost = false;
    /** 幽灵是否当前显形 */
    private boolean ghostVisible = false;
    /** 显形后自动隐身倒计时 tick（0.5s = 10 tick 内有人看到则隐身） */
    private int ghostVisibleAutoHideTicks = 0;
    /** 是否已彻底死亡（幽灵再次被击杀后进入旁观者模式） */
    private boolean finalDeath = false;
    /** 死亡后商店是否已解锁 */
    private boolean shopUnlocked = false;
    /** 是否已购买撬棍（死亡后只能买一次） */
    private boolean boughtCrowbar = false;
    /** 便签已购买次数（不限购，每次价格翻倍） */
    private int notePurchaseCount = 0;
    /** 便签基础价格 */
    public static final int NOTE_BASE_PRICE = 75;
    /** 便签每次购买数量 */
    public static final int NOTE_PER_PURCHASE = 2;

    // ── 常量 ──
    public static final int SOUL_OUT_DURATION = 7 * 20; // 7s
    public static final int SOUL_OUT_COOLDOWN = 20 * 20; // 20s
    public static final int GHOST_VISIBLE_COOLDOWN = 60 * 20; // 60s
    public static final int GHOST_AUTO_HIDE_TICKS = 10; // 0.5s
    /** 幽灵商店撬棍的耐久点数（逐栈 MAX_DAMAGE 组件标记，耗尽后撬棍损坏消失） */
    public static final int CROWBAR_DURABILITY = 4;

    public WandererPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void init() {
        soulOutActive = false;
        soulOutRemainingTicks = 0;
        isGhost = false;
        ghostVisible = false;
        ghostVisibleAutoHideTicks = 0;
        finalDeath = false;
        shopUnlocked = false;
        boughtCrowbar = false;
        notePurchaseCount = 0;
        // 状态复位后同步清除隐身标记，避免残留到下一局
        applyInvisibility();
    }

    @Override
    public void clear() { init(); }

    @Override
    public void serverTick() {
        // 防御：死亡 / 游戏结束 / 角色被移除后强制结束灵魂出窍，防止状态残留
        //（残留会导致旁观者被冻结速度、HUD 持续显示、客户端自由相机不退出）
        if (soulOutActive && (isGhost || player.isSpectator() || !isGameActive())) {
            endSoulOut();
        }

        // 灵魂出窍倒计时
        if (soulOutActive) {
            soulOutRemainingTicks--;
            // 灵魂出窍时冻结本体位置：禁止移动 + 减速效果
            if (player instanceof ServerPlayer sp) {
                player.noPhysics = true;
                player.setNoGravity(true);
                player.setDeltaMovement(0, 0, 0);
                // 施加 255 级减速效果（每 tick 重新施加），使本体完全无法移动
                player.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN,
                        3, 255, false, false, false));
            }
            if (soulOutRemainingTicks <= 0) {
                endSoulOut();
            } else if (soulOutRemainingTicks % 20 == 0) {
                // 每秒同步一次，减少网络负担
                sync();
            }
        }

        // 隐身平民（初次死亡后）：保持正常物理与重力，可自由行走、可碰撞、可被再次击杀，
        // 仅默认隐身（不做无碰撞/无重力的"幽灵飞行"处理，否则会漂浮穿墙、手感类似旁观者）。
        // 这里做兜底自愈：
        //  1) 死亡链末尾会把玩家切成旁观者，只要仍处于「隐身平民」状态却被切成旁观，就强制拉回冒险模式；
        //  2) 归位 noPhysics / 无重力等灵魂出窍残留；
        //  3) 保证隐身标记与显隐状态一致（隐身逻辑完全独立于幽灵状态，直接用原版 invisible 标记）。
        //
        // 主修复：游荡者组件键已在 QUSTRoles.init() 里通过 addRoleComponents 补登记进
        // TMMRoles.COMPONENT_KEYS，onStartGame/onEndGame 会 clear()→init() 复位 isGhost，跨局不再残留。
        // 下面的 isGameActive() 守卫作为兜底：应对游戏刚结束的 STOPPING 窗口、或玩家带持久 isGhost
        // 重连回大厅等边界场景——只要不是「进行中的局且本人仍是游荡者」，就强制解除隐身、不改游戏模式。
        if (isGhost && !finalDeath && player instanceof ServerPlayer sp) {
            if (!isGameActive()) {
                if (player.isInvisible()) {
                    player.setInvisible(false);
                }
                if (player.noPhysics) {
                    player.noPhysics = false;
                }
                if (player.isNoGravity()) {
                    player.setNoGravity(false);
                }
            } else {
                if (sp.isSpectator()) {
                    sp.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
                    io.wifi.starrailexpress.game.GameUtils.releaseRoleFlight(sp);
                }
                if (player.noPhysics) {
                    player.noPhysics = false;
                }
                if (player.isNoGravity()) {
                    player.setNoGravity(false);
                }
                applyInvisibility();
            }
        }

        // 幽灵显形自动隐身检测
        if (isGhost && ghostVisible && ghostVisibleAutoHideTicks > 0) {
            ghostVisibleAutoHideTicks--;
            if (ghostVisibleAutoHideTicks <= 0) {
                // 检查是否有其他玩家在视野内
                if (isAnyPlayerLookingAtMe()) {
                    setGhostVisible(false);
                } else {
                    // 没人看到，继续延长检测
                    ghostVisibleAutoHideTicks = GHOST_AUTO_HIDE_TICKS;
                }
            }
        }
    }

    // ── 灵魂出窍 ──

    public boolean startSoulOut() {
        if (soulOutActive || isGhost || finalDeath) return false;
        soulOutActive = true;
        soulOutRemainingTicks = SOUL_OUT_DURATION;
        sync();
        return true;
    }

    public void endSoulOut() {
        if (!soulOutActive) {
            return; // 幂等：未处于灵魂出窍时不重复处理
        }
        soulOutActive = false;
        soulOutRemainingTicks = 0;
        // 恢复本体的物理和重力状态，防止漂浮（幽灵状态由幽灵逻辑自行管理）
        if (!isGhost && player instanceof ServerPlayer) {
            player.noPhysics = false;
            player.setNoGravity(false);
            // 清除灵魂出窍期间施加的减速效果
            player.removeEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);
        }
        sync();
        // 通知客户端退出自由相机（含死亡/游戏结束等异常结束场景）
        if (player instanceof ServerPlayer sp) {
            net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                    sp, new WandererPayload.SoulOutState(false));
        }
    }

    /** 游戏是否正在进行且该玩家当前仍持有游荡者角色 */
    private boolean isGameActive() {
        if (!(player instanceof ServerPlayer)) {
            return false;
        }
        var game = SREGameWorldComponent.KEY.get(player.level());
        return game.getGameStatus() == SREGameWorldComponent.GameStatus.ACTIVE
                && game.getRole(player) == org.agmas.noellesroles.role.qust.QUSTRoles.WANDERER;
    }

    public boolean isSoulOutActive() { return soulOutActive; }
    public int getSoulOutRemainingTicks() { return soulOutRemainingTicks; }

    // ── 幽灵状态 ──

    public void setGhostState(boolean ghost) {
        this.isGhost = ghost;
        if (ghost) {
            this.shopUnlocked = true;
        }
        applyInvisibility();
        sync();
    }

    public boolean isGhost() { return isGhost; }

    /** 是否处于「隐身平民」状态（初次死亡后、彻底死亡前）。 */
    public boolean isHiddenCivilian() { return isGhost && !finalDeath; }

    public void setGhostVisible(boolean visible) {
        this.ghostVisible = visible;
        if (visible) {
            ghostVisibleAutoHideTicks = GHOST_AUTO_HIDE_TICKS;
        } else {
            ghostVisibleAutoHideTicks = 0;
        }
        applyInvisibility();
        sync();
    }

    public boolean isGhostVisible() { return ghostVisible; }

    /**
     * 独立的隐身 / 显形逻辑：直接使用原版 {@code invisible} 标记，
     * 不再依赖 GhostStateComponent 与 GHOST_STATE 效果。
     * <ul>
     *   <li>隐身平民（已死亡且未彻底死亡）且未显形 → 隐身</li>
     *   <li>其余情况（存活 / 显形中 / 彻底死亡） → 可见</li>
     * </ul>
     * 仅在标记发生变化时写入，避免每 tick 重复发包。
     */
    public void applyInvisibility() {
        boolean shouldBeInvisible = isGhost && !finalDeath && !ghostVisible;
        if (player.isInvisible() != shouldBeInvisible) {
            player.setInvisible(shouldBeInvisible);
        }
    }

    /**
     * 初次死亡后以冒险模式回到自己房间，成为默认隐身的普通平民。
     * 由 {@link WandererRole#onDeath} 的延迟任务调用，也被 {@link #serverTick()} 兜底复用。
     */
    public void returnToRoomAsHiddenCivilian() {
        if (!(player instanceof ServerPlayer sp)) return;
        io.wifi.starrailexpress.game.GameUtils.teleportBackToRoom(sp);
        sp.setGameMode(net.minecraft.world.level.GameType.ADVENTURE);
        // 清除死亡切旁观时残留的飞行能力，并归位物理状态，
        // 确保隐身平民“不能飞、能正常行走”（不会漂浮/穿墙）。
        io.wifi.starrailexpress.game.GameUtils.releaseRoleFlight(sp);
        sp.noPhysics = false;
        sp.setNoGravity(false);
        sp.setHealth(sp.getMaxHealth());
        applyInvisibility();
        if (!io.wifi.starrailexpress.compat.TrainVoicePlugin.isVoiceChatMissing()) {
            io.wifi.starrailexpress.compat.TrainVoicePlugin.addPlayer(sp.getUUID());
        }
        // 发送死亡通知包：客户端显示 8 秒大字提示
        net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(
                sp, new WandererPayload.DeathNotification());
    }

    // ── 彻底死亡 ──

    public void setFinalDeath(boolean finalDeath) {
        this.finalDeath = finalDeath;
        applyInvisibility();
        sync();
    }

    public boolean isFinalDeath() { return finalDeath; }

    // ── 幽灵商店 ──

    public boolean isShopUnlocked() { return shopUnlocked; }
    public boolean hasBoughtCrowbar() { return boughtCrowbar; }
    public void setBoughtCrowbar(boolean b) { boughtCrowbar = b; sync(); }
    /** 当前便签价格 = 基础价格 × 2^已购次数 */
    public int getNotePrice() { return NOTE_BASE_PRICE * (1 << notePurchaseCount); }
    public int getNotePurchaseCount() { return notePurchaseCount; }
    public void incrementNotePurchase() { notePurchaseCount++; sync(); }

    // ── 辅助 ──

    private boolean isAnyPlayerLookingAtMe() {
        if (!(player instanceof ServerPlayer sp)) return false;
        for (var p : sp.serverLevel().players()) {
            if (p.getUUID().equals(sp.getUUID())) continue;
            if (!io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(p)) continue;
            double dist = p.distanceTo(sp);
            if (dist > 8) continue; // 仅 8 格范围内看到才会触发自动隐身
            // 简单视野检测：目标朝向与到玩家方向夹角 < 60°
            var toPlayer = sp.position().subtract(p.position()).normalize();
            var lookDir = p.getLookAngle();
            double dot = toPlayer.dot(lookDir);
            if (dot > 0.5) return true; // cos(60°) = 0.5
        }
        return false;
    }

    // ── CCA 序列化 ──

    @Override
    public Player getPlayer() { return player; }

    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) { return true; }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putBoolean("soulOutActive", soulOutActive);
        tag.putInt("soulOutRemaining", soulOutRemainingTicks);
        tag.putBoolean("isGhost", isGhost);
        tag.putBoolean("ghostVisible", ghostVisible);
        tag.putBoolean("finalDeath", finalDeath);
        tag.putBoolean("shopUnlocked", shopUnlocked);
        tag.putBoolean("boughtCrowbar", boughtCrowbar);
        tag.putInt("notePurchaseCount", notePurchaseCount);
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        soulOutActive = tag.getBoolean("soulOutActive");
        soulOutRemainingTicks = tag.getInt("soulOutRemaining");
        isGhost = tag.getBoolean("isGhost");
        ghostVisible = tag.getBoolean("ghostVisible");
        finalDeath = tag.getBoolean("finalDeath");
        shopUnlocked = tag.getBoolean("shopUnlocked");
        boughtCrowbar = tag.getBoolean("boughtCrowbar");
        // 兼容旧存档：旧字段 boughtNote → 新字段 notePurchaseCount
        if (tag.contains("notePurchaseCount")) {
            notePurchaseCount = tag.getInt("notePurchaseCount");
        } else if (tag.getBoolean("boughtNote")) {
            notePurchaseCount = 1;
        }
    }
}
