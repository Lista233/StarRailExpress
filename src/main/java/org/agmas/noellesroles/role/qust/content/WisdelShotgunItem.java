/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.agmas.noellesroles.role.qust.content;

import io.wifi.StarRailExpressID;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.SREConfig;
import io.wifi.starrailexpress.api.hit.HitType;
import io.wifi.starrailexpress.api.hit.SREHitManager;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.client.particle.HandParticle;
import io.wifi.starrailexpress.client.render.TMMRenderLayers;
import io.wifi.starrailexpress.content.entity.GrenadeEntity;
import io.wifi.starrailexpress.content.item.SkinableItem;
import io.wifi.starrailexpress.content.item.api.SREItemProperties.HeldLikeRevolver;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMParticles;
import io.wifi.starrailexpress.index.TMMSounds;
import io.wifi.starrailexpress.util.Scheduler;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.content.entity.PuppeteerBodyEntity;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

import java.util.ArrayList;

/**
 * 祖宗发射器 —— QUST 页面收录的重型霰弹发射器。
 * <p>
 * 与其它枪械一样具备左轮式握持姿势（{@link HeldLikeRevolver}）与枪口火光特效。
 * 右键开火：后坐力较大，朝视线正前方发射一发炮弹，用深红色粒子在 0.5 秒内描绘飞行弹道，
 * 最大射程 {@value #MAX_RANGE} 格；命中最近的方块 / 玩家 / 实体后在落点引爆，
 * 爆炸半径 {@value #BLAST_RADIUS} 格（爆炸特效参考 {@link GrenadeEntity}），并且<b>会波及发射者本人</b>。
 * <p>
 * 只有在游戏正式开始（{@link SREGameWorldComponent#isRunning()}）后，爆炸范围内的玩家才会被结算击杀，
 * 死因为「手雷杀」（{@link GameConstants.DeathReasons#GRENADE}）。
 */
public class WisdelShotgunItem extends SkinableItem implements HeldLikeRevolver {

    /** 最大射程（格） */
    public static final double MAX_RANGE = 12.0;
    /** 爆炸半径（格） */
    public static final float BLAST_RADIUS = 5.0F;
    /** 弹道展示时长（ticks），10 tick = 0.5 秒 */
    public static final int TRAVEL_TICKS = 10;
    /** 开火冷却（ticks） */
    public static final int COOLDOWN_TICKS = 30;
    /** 后坐力抬头的角度（度）——比左轮（4°）大得多 */
    public static final float RECOIL_PITCH = 20.0F;

    // ─── 枪口对齐：由 128×128 贴图上枪口尖端像素 (23,13) 换算，三处效果共用 ───
    /** 贴图像素系：枪口尖端距左上角的 X */
    public static final int MUZZLE_PX_X = 23;
    /** 贴图像素系：枪口尖端距左上角的 Y */
    public static final int MUZZLE_PX_Y = 13;
    /** 贴图画布边长（像素） */
    public static final int TEXTURE_SIZE = 128;
    /** 第一人称火光模型本地 X：贴图铺满 [0,1]²，x = 像素X / 边长 */
    public static final float MUZZLE_MODEL_X = (float) MUZZLE_PX_X / TEXTURE_SIZE;
    /** 第一人称火光模型本地 Y：贴图顶部对应模型 y=1，故 y = 1 - 像素Y / 边长 */
    public static final float MUZZLE_MODEL_Y = 1.0F - (float) MUZZLE_PX_Y / TEXTURE_SIZE;
    /** 第一人称火光沿模型 Z 的前伸量（负=朝屏幕外，避免被枪身挡住）；如需更贴枪口可微调 */
    public static final float MUZZLE_MODEL_Z = -0.28F;
    /** 世界系枪口：沿视线方向前伸（格）——弹道起点与第三人称火光用 */
    public static final double MUZZLE_FORWARD = 0.75;
    /** 世界系枪口：沿右手方向侧偏（格） */
    public static final double MUZZLE_SIDE = 0.22;
    /** 世界系枪口：竖直偏移（格，正=上） */
    public static final double MUZZLE_UP = -0.05;

    public WisdelShotgunItem(Properties settings) {
        super(settings);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level world, @NotNull Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);
        if (user.isSpectator()) {
            return InteractionResultHolder.pass(stack);
        }

        if (world.isClientSide) {
            // 客户端：只做手感（后坐力抬头 + 枪口火光），命中与爆炸一律交给服务端结算
            if (user.getCooldowns().isOnCooldown(this)) {
                return InteractionResultHolder.pass(stack);
            }
            user.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
            // 抬头：俯仰角减小即向上看
            user.setXRot(user.getXRot() - RECOIL_PITCH);
            spawnHandParticle();
            return InteractionResultHolder.consume(stack);
        }

        // 服务端：权威判定冷却、计算弹道、播放音效并调度飞行 + 爆炸
        if (user.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        user.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        if (world instanceof ServerLevel serverLevel) {
            fire(serverLevel, user);
        }
        world.playSound(null, user.getX(), user.getEyeY(), user.getZ(), TMMSounds.ITEM_REVOLVER_SHOOT,
                SoundSource.PLAYERS, 4f, 0.7f + world.random.nextFloat() * 0.1f - 0.05f);

        if (SRE.REPLAY_MANAGER != null) {
            SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this));
        }
        // 一次性：开火瞬间即消耗这支发射器（无论是否命中/爆炸）
        stack.shrink(1);
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 服务端开火：从眼睛沿视线投射一条最长 {@value #MAX_RANGE} 格的射线，取「方块命中」与「实体命中」的较近者
     * 作为落点，然后用深红色粒子把这段弹道在 {@value #TRAVEL_TICKS} tick 内逐步描绘出来，最后引爆。
     */
    private static void fire(ServerLevel level, Player shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 dir = shooter.getLookAngle();
        Vec3 rayEnd = eye.add(dir.scale(MAX_RANGE));

        // 命中判定从眼睛（准星）出发，保证「瞄哪打哪」
        BlockHitResult blockHit = level.clip(new ClipContext(eye, rayEnd,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        double travel = MAX_RANGE;
        if (blockHit.getType() != HitResult.Type.MISS) {
            travel = Math.min(travel, eye.distanceTo(blockHit.getLocation()));
        }

        // 实体命中距离（玩家 / 傀儡本体 / 其它可命中目标）
        HitResult entityHit = SREHitManager.getTarget(shooter, HitType.GUN, MAX_RANGE);
        if (entityHit instanceof EntityHitResult ehr) {
            double entityDist = eye.distanceTo(ehr.getLocation());
            if (entityDist < travel) {
                travel = entityDist;
            }
        }

        Vec3 hitPos = eye.add(dir.scale(travel));
        // 弹道从「世界系枪口」画到落点（视差：看起来从枪管射出并汇聚到准星处）
        Vec3 muzzle = muzzleWorldPos(shooter);
        scheduleTrailStep(level, shooter, muzzle, hitPos, 1);
    }

    /**
     * 世界系枪口坐标：眼睛 + 视线×前伸 + 右手×侧偏 + 竖直偏移。
     * 右手方向取 {@code (-look.z, 0, look.x)}（面朝 +Z 南时右手为 -X 西）。
     */
    private static Vec3 muzzleWorldPos(Player shooter) {
        Vec3 eye = shooter.getEyePosition();
        Vec3 look = shooter.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0.0, look.x).normalize();
        return eye.add(look.scale(MUZZLE_FORWARD))
                .add(right.scale(MUZZLE_SIDE))
                .add(new Vec3(0.0, MUZZLE_UP, 0.0));
    }

    /**
     * 弹道描绘的第 {@code step} 步：在 {@code from}(枪口) 到 {@code to}(落点) 之间按进度插值铺深红粒子，
     * 并补几点拖尾；走完最后一步后在落点引爆。
     */
    private static void scheduleTrailStep(ServerLevel level, Player shooter, Vec3 from, Vec3 to, int step) {
        Vec3 current = from.lerp(to, (double) step / TRAVEL_TICKS);
        Vec3 previous = from.lerp(to, (double) (step - 1) / TRAVEL_TICKS);

        DustParticleOptions bullet = new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 1.4F);
        // 弹头：当前点一簇
        level.sendParticles(bullet, current.x, current.y, current.z, 4, 0.04, 0.04, 0.04, 0.0);
        // 拖尾：上一步到当前步之间插值补点
        for (int k = 1; k <= 3; k++) {
            double t = k / 4.0;
            Vec3 trail = previous.lerp(current, t);
            level.sendParticles(bullet, trail.x, trail.y, trail.z, 1, 0.02, 0.02, 0.02, 0.0);
        }

        if (step < TRAVEL_TICKS) {
            Scheduler.schedule(() -> scheduleTrailStep(level, shooter, from, to, step + 1), 1);
        } else {
            explode(level, shooter, current);
        }
    }

    /**
     * 落点引爆：爆炸特效参考 {@link GrenadeEntity}（大爆炸 + 浓烟 + 手雷爆炸音效）。
     * 游戏正式开始后，对爆炸范围内（含发射者本人）的玩家按「手雷杀」（{@link GameConstants.DeathReasons#GRENADE}）结算击杀。
     */
    private static void explode(ServerLevel level, Player shooter, Vec3 pos) {
        double x = pos.x, y = pos.y, z = pos.z;
        level.playSound(null, x, y, z, TMMSounds.ITEM_GRENADE_EXPLODE, SoundSource.PLAYERS, 5f,
                1f + level.getRandom().nextFloat() * 0.1f - 0.05f);
        level.sendParticles(TMMParticles.BIG_EXPLOSION, x, y + 0.1, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SMOKE, x, y + 0.1, z, 100, 0, 0, 0, 0.2f);

        // 只有游戏正式开始后才产生击杀结算
        if (!SREGameWorldComponent.KEY.get(level).isRunning()) {
            return;
        }
        ArrayList<Entity> affected = GrenadeEntity.getPlayersAffectedByExplosion(level, x, y, z, BLAST_RADIUS);
        // 击杀金币结算：完全参照 GrenadeEntity —— 单价 grenadeMoneyPerKill × 击杀数，上限 grenadeMaxMoneyReward
        SREPlayerShopComponent killerShop = SREPlayerShopComponent.KEY.get(shooter);
        int balanceBefore = killerShop != null ? killerShop.balance : 0;
        int count = 0;
        for (Entity entity : affected) {
            if (entity instanceof Player victim) {
                // 发射者也在范围内时同样被自己炸死（会波及自己）
                if (victim != shooter) {
                    count++;
                }
                GameUtils.killPlayer(victim, true, shooter, GameConstants.DeathReasons.GRENADE);
            } else if (entity instanceof PuppeteerBodyEntity bodyEntity) {
                bodyEntity.playerHurt(shooter, GameConstants.DeathReasons.GRENADE);
            }
        }
        if (killerShop != null) {
            int moneyEarned = killerShop.balance - balanceBefore;
            int perKill = SREConfig.instance().grenadeMoneyPerKill;
            int maxReward = SREConfig.instance().grenadeMaxMoneyReward;
            int targetReward = count * perKill;
            if (maxReward > 0 && targetReward > maxReward) {
                targetReward = maxReward;
            }
            int adjustment = targetReward - moneyEarned;
            if (adjustment != 0) {
                killerShop.addToBalance(adjustment);
            }
        }
    }

    /** 枪口火光（与左轮 / 悔改之枪一致的贴手粒子）。 */
    public static void spawnHandParticle() {
        HandParticle handParticle = new HandParticle()
                .setTexture(StarRailExpressID.watheId("textures/particle/gunshot.png"))
                .setPos(MUZZLE_MODEL_X, MUZZLE_MODEL_Y, MUZZLE_MODEL_Z)
                .setMaxAge(3.0F)
                .setSize(0.7F)
                .setVelocity(0.0F, 0.0F, 0.0F)
                .setLight(15, 15)
                .setAlpha(new float[]{1.0F, 0.1F})
                .setRenderLayer(TMMRenderLayers::additive);
        SREClient.handParticleManager.spawn(handParticle);
    }

    @Override
    public String getItemSkinType() {
        return "wisdel_shotgun";
    }
}
