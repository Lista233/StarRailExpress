package org.agmas.noellesroles.role.qust.roles.dragon_girl;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.game.GameUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.role.qust.QUSTConfig;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

/**
 * 龙娘角色组件
 * <ul>
 *   <li>技能1 (G): 龙娘魅惑 - 发光+吸引5格内玩家视线+禁用物品栏/技能+强制移动4s</li>
 *   <li>技能2 (Shift+G): 恶龙咆哮 - 引导1.5s+范围击退(衰减)+粒子效果</li>
 * </ul>
 * <p>
 * 所有技能数值从 QUSTConfig 读取，支持服务端动态调整。
 */
public class DragonGirlPlayerComponent implements RoleComponent, ServerTickingComponent {

    public Player player;

    // 皮肤贴图路径
    public static final String SKIN_PATH = "textures/entity/dragon_girl.png";

    // ==================== 配置数值（从 QUSTConfig 初始化） ====================

    public final int charmCooldownTicks = QUSTConfig.instance().dragonGirlCharmCooldownSeconds * 20;
    public final int charmDurationTicks = QUSTConfig.instance().dragonGirlCharmDurationSeconds * 20;
    public final int charmRange = QUSTConfig.instance().dragonGirlCharmRange;
    public final double charmPullSpeed = QUSTConfig.instance().dragonGirlCharmPullSpeed;
    public final int roarCooldownTicks = QUSTConfig.instance().dragonGirlRoarCooldownSeconds * 20;
    public final int roarChannelDurationTicks = (int) (QUSTConfig.instance().dragonGirlRoarChannelDurationSeconds * 20);
    public final int roarParticleRange = QUSTConfig.instance().dragonGirlRoarParticleRange;
    public final int roarKnockbackMaxRange = QUSTConfig.instance().dragonGirlRoarKnockbackMaxRange;
    public final int roarYRange = QUSTConfig.instance().dragonGirlRoarYRange;
    public final int roarKnockbackMax = QUSTConfig.instance().dragonGirlRoarKnockbackMax;
    public final int roarKnockbackMin = QUSTConfig.instance().dragonGirlRoarKnockbackMin;

    /** 击退持续时间固定 0.25s (5 ticks)，暂不提供配置 */
    public static final int ROAR_KNOCKBACK_DURATION = 5;

    // ---- 运行时状态 ----
    /** 龙娘魅惑冷却剩余 tick */
    public int charmCooldown = charmCooldownTicks;
    /** 恶龙咆哮冷却剩余 tick */
    public int roarCooldown = roarCooldownTicks;

    /** 龙娘魅惑正在生效的剩余 tick */
    public int charmActiveTicks = 0;
    /** 恶龙咆哮引导剩余 tick (>0 表示正在引导) */
    public int roarChannelTicks = 0;

    /** 被魅惑的玩家UUID列表 */
    public Set<UUID> charmedTargets = new HashSet<>();

    /** 是否正在使用龙娘皮肤 */
    public boolean skinActive = false;

    public DragonGirlPlayerComponent(Player player) {
        this.player = player;
    }


    @Override
    public Player getPlayer() {
        return player;
    }

    /**
     * 同步给所有玩家，使其他客户端也能看到皮肤替换效果。
     */
    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) {
        return true;
    }

    @Override
    public void init() {
        charmCooldown = charmCooldownTicks;
        roarCooldown = roarCooldownTicks;
        charmActiveTicks = 0;
        roarChannelTicks = 0;
        charmedTargets.clear();
        skinActive = false;
    }

    @Override
    public void clear() {
        // 结束时清除所有效果
        if (charmActiveTicks > 0) {
            endCharm();
        }
        if (roarChannelTicks > 0) {
            roarChannelTicks = 0;
        }
        skinActive = false;
        if (player instanceof ServerPlayer sp) {
            sp.removeEffect(MobEffects.GLOWING);
        }
        init();
    }

    public void sync() {
        QUSTComponentKeys.Keys.DRAGON_GIRL.sync(this.player);
    }

    // ==================== 目光注视辅助 ====================

    /**
     * 让 target 玩家朝 source 玩家方向调整 yaw/pitch（原地 teleport，仅改朝向）。
     * 用于龙娘魅惑/咆哮技能中的"目光注视"表现。
     */
    private void makePlayerLookAt(ServerPlayer target, ServerPlayer source) {
        double dx = source.getX() - target.getX();
        double dy = (source.getY() + source.getEyeHeight(source.getPose()))
                - (target.getY() + target.getEyeHeight(target.getPose()));
        double dz = source.getZ() - target.getZ();
        double hDist = Math.sqrt(dx * dx + dz * dz);
        if (hDist < 1.0e-4)
            return;
        float yaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90);
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, hDist));
        target.connection.teleport(target.getX(), target.getY(), target.getZ(), yaw, pitch);
    }

    // ==================== 技能1: 龙娘魅惑 ====================

    public boolean useCharm() {
        ServerPlayer sp = (ServerPlayer) player;
        if(charmCooldown > 0){
            sp.displayClientMessage(Component.translatable("冷却时间未结束"), true);
            return false;
        }


        charmActiveTicks = charmDurationTicks;
        skinActive = true;
        charmCooldown = charmCooldownTicks;
        // 自身发光
        sp.addEffect(new MobEffectInstance(MobEffects.GLOWING, charmDurationTicks, 0, false, false));
        sync();
        // 魅惑5格内所有玩家
        charmedTargets.clear();
        ServerLevel level = sp.serverLevel();
        for (Player p : level.players()) {
            if (p == sp)
                continue;
            if (GameUtils.isPlayerEliminated(p))
                continue;
            if (p.distanceTo(sp) <= charmRange) {
                if (p instanceof ServerPlayer target) {
                    charmedTargets.add(target.getUUID());
                    applyCharmEffects(target, sp, level, charmDurationTicks);
                }
            }
        }

        // 播放声音
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0f, 1.5f);
        sync();
        return true;
    }

    /**
     * 对目标玩家施加魅惑效果（禁用物品栏/技能、虚弱、消息提示、目光注视、强制拉扯、粒子）。
     * durationTicks 为本次施加的效果持续时长：初始魅惑时传入完整持续时长，
     * 中途新进入范围被魅惑的玩家则传入剩余时长，保证与已被魅惑玩家同时解除。
     */
    private void applyCharmEffects(ServerPlayer target, ServerPlayer sp, ServerLevel level, int durationTicks) {
        // 禁用物品栏和技能：施加技能禁用+物品栏禁用效果
        target.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, durationTicks, 0, false, false, true));
        target.addEffect(new MobEffectInstance(ModEffects.INVENTORY_BANED, durationTicks, 0, false, false, true));
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, durationTicks, 5, false, false));
        target.displayClientMessage(Component.translatable("message.noellesroles.dragon_girl.charmed"), true);
        // 目光注视：让被魅惑玩家立即朝龙娘方向看
        makePlayerLookAt(target, sp);
        // 强制朝龙娘移动：施加朝向龙娘的初始拉扯速度（持续移动由 tickCharm 维持）
        Vec3 moveDir = sp.position().subtract(target.position()).normalize();
        target.setDeltaMovement(moveDir.x * charmPullSpeed, target.getDeltaMovement().y, moveDir.z * charmPullSpeed);
        target.hurtMarked = true;
        // 在目标玩家眼睛位置散发紫色龙焰粒子，表现"龙娘目光"
        double eyeX = target.getX();
        double eyeY = target.getY() + target.getEyeHeight(target.getPose());
        double eyeZ = target.getZ();
        level.sendParticles(ParticleTypes.DRAGON_BREATH,
                eyeX, eyeY, eyeZ, 6, 0.0, 0.0, 0.0, 0.02);
    }

    private void tickCharm() {
        if (charmActiveTicks <= 0)
            return;

        ServerPlayer sp = (ServerPlayer) player;
        if (sp == null || sp.level().isClientSide)
            return;

        ServerLevel level = sp.serverLevel();

        // 扫描范围内新进入的玩家，使其在魅惑持续期间也被魅惑（剩余时长与已被魅惑玩家一致，确保同时解除）
        for (Player p : level.players()) {
            if (p == sp)
                continue;
            if (GameUtils.isPlayerEliminated(p))
                continue;
            if (charmedTargets.contains(p.getUUID()))
                continue;
            if (p.distanceTo(sp) <= charmRange && p instanceof ServerPlayer newTarget) {
                charmedTargets.add(newTarget.getUUID());
                applyCharmEffects(newTarget, sp, level, charmActiveTicks);
            }
        }

        // 强制被魅惑的玩家朝龙娘移动
        for (UUID uuid : new HashSet<>(charmedTargets)) {
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(uuid);
            if (target == null || GameUtils.isPlayerEliminated(target)) {
                continue;
            }
            // 朝龙娘方向施加速度效果
            Vec3 dir = sp.position().subtract(target.position()).normalize();
            target.setDeltaMovement(dir.x * charmPullSpeed, target.getDeltaMovement().y, dir.z * charmPullSpeed);
            target.hurtMarked = true;
            // 目光注视：每 tick 持续让被魅惑玩家面朝龙娘
            makePlayerLookAt(target, sp);
            // 每 5 tick 在目标玩家眼前生成一次龙焰粒子，表现被目光笼罩
            if (charmActiveTicks % 5 == 0) {
                double eyeX = target.getX();
                double eyeY = target.getY() + target.getEyeHeight(target.getPose());
                double eyeZ = target.getZ();
                level.sendParticles(ParticleTypes.DRAGON_BREATH,
                        eyeX, eyeY, eyeZ, 2, 0.0, 0.0, 0.0, 0.0);
            }
        }

        charmActiveTicks--;

        // 结束时还原皮肤
        if (charmActiveTicks <= 0) {
            endCharm();
        }
    }

    private void endCharm() {
        charmActiveTicks = 0;
        if (!isRoarChanneling()) {
            skinActive = false;
        }
        charmedTargets.clear();
        if (player instanceof ServerPlayer sp) {
            sp.removeEffect(MobEffects.GLOWING);
        }
        sync();
    }

    // ==================== 技能2: 恶龙咆哮 ====================

    public boolean useRoar(RoleSkill.RoleSkillContext context) {
        ServerPlayer sp = (ServerPlayer) player;
        if(roarCooldown > 0){
            sp.displayClientMessage(Component.translatable("冷却时间未结束"), true);
            return false;
        }
        if (charmActiveTicks > 0) {
            sp.displayClientMessage(Component.translatable("message.noellesroles.dragon_girl.skill_active"), true);
            return false;
        }

        // 开始引导
        roarChannelTicks = roarChannelDurationTicks;
        skinActive = true;

        // 引导期间自身发光
        sp.addEffect(new MobEffectInstance(MobEffects.GLOWING, roarChannelDurationTicks + 10, 0, false, false));
        roarCooldown =  roarCooldownTicks;
        sync();
        return true;
    }

    private void tickRoarChannel() {
        if (roarChannelTicks <= 0)
            return;

        ServerPlayer sp = (ServerPlayer) player;
        if (sp == null || sp.level().isClientSide)
            return;

        roarChannelTicks--;

        // 引导期间持续发光
        if (roarChannelTicks % 10 == 0) {
            sp.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20, 0, false, false));
        }

        // 引导结束后释放咆哮
        if (roarChannelTicks <= 0) {
            executeRoar(sp);
        }
    }

    private void executeRoar(ServerPlayer sp) {
        ServerLevel level = sp.serverLevel();
        Vec3 pos = sp.position();

        // 1. 释放粒子效果：沿x与z轴成淡蓝色圆形快速散开
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2 * i / 32.0;
            for (int r = 1; r <= roarParticleRange; r++) {
                double offsetX = Math.cos(angle) * r;
                double offsetZ = Math.sin(angle) * r;
                level.sendParticles(ParticleTypes.SNOWFLAKE,
                        pos.x + offsetX, pos.y + 1.0, pos.z + offsetZ,
                        3, 0.0, 0.2, 0.0, 0.05);
                level.sendParticles(ParticleTypes.CLOUD,
                        pos.x + offsetX, pos.y + 0.5, pos.z + offsetZ,
                        2, 0.0, 0.1, 0.0, 0.02);
            }
        }

        // 2. 播放咆哮音效
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 3.0f, 0.8f);
        level.playSound(null, pos.x, pos.y, pos.z,
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.5f, 0.6f);

        // 3. 击退范围内所有玩家
        for (Player p : level.players()) {
            if (p == sp)
                continue;
            if (GameUtils.isPlayerEliminated(p))
                continue;

            double dx = p.getX() - sp.getX();
            double dz = p.getZ() - sp.getZ();
            double dy = p.getY() - sp.getY();
            double horizontalDist = Math.sqrt(dx * dx + dz * dz);

            // y轴相差不超过2格，水平距离6格内
            if (Math.abs(dy) > roarYRange)
                continue;
            if (horizontalDist > roarKnockbackMaxRange)
                continue;
            if (horizontalDist < 0.1)
                continue;

            // 击退距离衰减：离得越近击退越远
            double ratio = 1.0 - (horizontalDist / roarKnockbackMaxRange);
            double knockbackDistance = roarKnockbackMin + (roarKnockbackMax - roarKnockbackMin) * ratio;

            // 计算击退方向
            Vec3 knockbackDir = new Vec3(dx, 0, dz).normalize();
            // 击退持续0.25s (5 ticks)，速度 = 距离 / ticks
            double motionPerTick = knockbackDistance / ROAR_KNOCKBACK_DURATION;

            // 施加击退
            p.setDeltaMovement(
                    knockbackDir.x * motionPerTick,
                    0.3, // 轻微向上
                    knockbackDir.z * motionPerTick
            );
            if (p instanceof ServerPlayer sp2) {
                sp2.hurtMarked = true;
            }

            // 击退期间施加短暂禁用
            p.addEffect(new MobEffectInstance(ModEffects.SKILL_BANED, ROAR_KNOCKBACK_DURATION, 0, false, false, true));

            // 目光注视：咆哮瞬间让玩家面向龙娘
            if (p instanceof ServerPlayer lookTarget) {
                makePlayerLookAt(lookTarget, sp);
                lookTarget.displayClientMessage(
                        Component.translatable("message.noellesroles.dragon_girl.roar_struck"), true);
            }
            // 在被击退玩家眼前散发暴怒龙焰粒子
            double eyeX = p.getX();
            double eyeY = p.getY() + p.getEyeHeight(p.getPose());
            double eyeZ = p.getZ();
            level.sendParticles(ParticleTypes.DRAGON_BREATH,
                    eyeX, eyeY, eyeZ, 8, 0.0, 0.0, 0.0, 0.03);
        }

        // 结束皮肤
        skinActive = false;
        sp.removeEffect(MobEffects.GLOWING);
        sync();
    }

    // ==================== 服务端 tick ====================

    @Override
    public void serverTick() {
        if (!GameUtils.isGameRunning(player))
            return;

        // 减少冷却
        if (charmCooldown > 0)
            charmCooldown--;
        if (roarCooldown > 0)
            roarCooldown--;

        // tick 技能效果
        tickCharm();
        tickRoarChannel();

        // 每1秒（20 tick）同步一次
        if (player.level().getGameTime() % 20 == 0) {
            sync();
        }
    }

    public boolean isRoarChanneling() {
        return roarChannelTicks > 0;
    }

    public boolean isCharmActive() {
        return charmActiveTicks > 0;
    }

    // ==================== 网络同步 ====================

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        tag.putInt("charmCooldown", charmCooldown);
        tag.putInt("roarCooldown", roarCooldown);
        tag.putInt("charmActiveTicks", charmActiveTicks);
        tag.putInt("roarChannelTicks", roarChannelTicks);
        tag.putBoolean("skinActive", skinActive);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        charmCooldown = tag.contains("charmCooldown") ? tag.getInt("charmCooldown") : 0;
        roarCooldown = tag.contains("roarCooldown") ? tag.getInt("roarCooldown") : 0;
        charmActiveTicks = tag.contains("charmActiveTicks") ? tag.getInt("charmActiveTicks") : 0;
        roarChannelTicks = tag.contains("roarChannelTicks") ? tag.getInt("roarChannelTicks") : 0;
        skinActive = tag.contains("skinActive") && tag.getBoolean("skinActive");
    }
}
