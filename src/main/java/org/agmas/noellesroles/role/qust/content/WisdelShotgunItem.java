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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
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
import org.agmas.noellesroles.gunfx.GunTracers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;

/**
 * 祖宗发射器 —— QUST 页面收录的重型霰弹发射器。
 * <p>
 * 与其它枪械一样具备左轮式握持姿势（{@link HeldLikeRevolver}）与枪口火光特效。
 * 右键开火：后坐力较大，朝视线正前方发射一发炮弹，弹道曳光使用统一枪械曳光系统的深红样式
 * （{@link GunTracers}：服务端只发一个包、客户端渲染渐隐深红轨迹线，其它枪械不受影响），
 * 最大射程 {@value #MAX_RANGE} 格（与狙击枪一致）；命中最近的方块 / 玩家 / 实体后<b>立即引爆</b>（无飞行延迟），
 * 爆炸半径 {@value #BLAST_RADIUS} 格（强化的大爆炸 + 浓烟 + 火光特效），并且<b>会波及发射者本人</b>。
 * <p>
 * 只有在游戏正式开始（{@link SREGameWorldComponent#isRunning()}）后，爆炸范围内的玩家才会被结算击杀，
 * 死因为独立的「祖宗发射器」（{@link GameConstants.DeathReasons#WISDEL_SHOTGUN}），不再复用手雷死因判定。
 * 游戏未开始时改为对范围内玩家施加击退：被直接命中的玩家沿弹道方向推开，其余玩家从爆心向外推开。
 */
public class WisdelShotgunItem extends SkinableItem implements HeldLikeRevolver {

    /** 最大射程（格，与狙击枪一致） */
    public static final double MAX_RANGE = 200.0;
    /** 爆炸半径（格） */
    public static final float BLAST_RADIUS = 5.0F;
    /** 开火冷却（ticks） */
    public static final int COOLDOWN_TICKS = 30;
    /** 后坐力抬头的角度（度）——比左轮（4°）大得多 */
    public static final float RECOIL_PITCH = 20.0F;
    /** 游戏未开始时，爆炸对范围内玩家的击退强度（水平速度上限，随距离线性衰减） */
    public static final double KNOCKBACK_STRENGTH = 1.2;

    // ─── 枪口对齐：由 128×128 贴图上枪口尖端像素 (23,13) 换算，供第一人称枪口火光对齐 ───
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

        // 服务端：权威判定冷却、计算弹道、播放音效并即时结算曳光 + 爆炸
        if (user.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        user.getCooldowns().addCooldown(this, COOLDOWN_TICKS);

        boolean gameRunning = SREGameWorldComponent.KEY.get(world).isRunning();
        if (world instanceof ServerLevel serverLevel) {
            fire(serverLevel, user);
        }
        world.playSound(null, user.getX(), user.getEyeY(), user.getZ(), TMMSounds.ITEM_REVOLVER_SHOOT,
                SoundSource.PLAYERS, 4f, 0.7f + world.random.nextFloat() * 0.1f - 0.05f);

        if (SRE.REPLAY_MANAGER != null) {
            SRE.REPLAY_MANAGER.recordItemUse(user.getUUID(),
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(this));
        }
        // 仅游戏正式开始时一次性消耗；未开始时不消耗，可反复使用（此时只做击退，不结算击杀）
        if (gameRunning) {
            stack.shrink(1);
        }
        return InteractionResultHolder.consume(stack);
    }

    /**
     * 服务端开火：从眼睛沿视线投射一条最长 {@value #MAX_RANGE} 格的射线，取「方块命中」与「实体命中」的较近者
     * 作为落点，广播一条深红样式的统一枪械曳光后<b>立即引爆</b>（无飞行延迟）。
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
        Player hitPlayer = null;
        Entity hitEntity = null;
        HitResult entityHit = SREHitManager.getTarget(shooter, HitType.GUN, MAX_RANGE);
        if (entityHit instanceof EntityHitResult ehr) {
            double entityDist = eye.distanceTo(ehr.getLocation());
            if (entityDist < travel) {
                travel = entityDist;
                hitEntity = ehr.getEntity();
                // 记录被直接命中的玩家：游戏未开始时的击退需沿弹道方向将其推开（见 applyKnockback）
                if (ehr.getEntity() instanceof Player targetPlayer) {
                    hitPlayer = targetPlayer;
                }
            }
        }

        Vec3 hitPos = eye.add(dir.scale(travel));
        // 弹道曳光：统一枪械曳光系统的深红样式（服务端每个接收者只发一个包、客户端渲染渐隐深红轨迹线；
        // 200 格长弹道不再逐点刷粒子包，且遵循 NoellesRolesConfig.gunTracerEffect 总开关）
        if (shooter instanceof ServerPlayer serverPlayer) {
            GunTracers.broadcastRed(serverPlayer, hitEntity, MAX_RANGE);
        }
        explode(level, shooter, hitPos, hitPlayer);
    }

    /**
     * 落点引爆（命中即时触发）：强化爆炸特效——多层大爆炸sprite + 浓烟云团 + 深红冲击波，
     * 配合手雷爆炸音效。游戏正式开始后，对爆炸范围内（含发射者本人）的玩家
     * 按「祖宗发射器」（{@link GameConstants.DeathReasons#WISDEL_SHOTGUN}）结算击杀。
     */
    private static void explode(ServerLevel level, Player shooter, Vec3 pos, @Nullable Player hitPlayer) {
        double x = pos.x, y = pos.y, z = pos.z;
        level.playSound(null, x, y, z, TMMSounds.ITEM_GRENADE_EXPLODE, SoundSource.PLAYERS, 5f,
                1f + level.getRandom().nextFloat() * 0.1f - 0.05f);
        // 核心火球：巨型爆炸 sprite 多重叠加，中心 + 小范围散布，视觉上远大于手雷
        level.sendParticles(TMMParticles.BIG_EXPLOSION, x, y + 0.1, z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, y + 0.2, z, 4, 1.2, 0.6, 1.2, 0.0);
        // 外围爆轰：在整颗爆炸半径内散布普通爆炸 sprite，让火球铺满 5 格范围
        level.sendParticles(ParticleTypes.EXPLOSION, x, y + 0.1, z, 14, BLAST_RADIUS * 0.6, BLAST_RADIUS * 0.35, BLAST_RADIUS * 0.6, 0.0);
        // 浓烟云团：量大、范围广、上飘
        level.sendParticles(ParticleTypes.SMOKE, x, y + 0.3, z, 400, 2.2, 1.4, 2.2, 0.35f);
        // 深红冲击波：贴地圆环扩散，呼应弹道颜色
        DustParticleOptions shockwave = new DustParticleOptions(new Vector3f(0.55F, 0.0F, 0.0F), 2.0F);
        level.sendParticles(shockwave, x, y + 0.05, z, 80, BLAST_RADIUS * 0.8, 0.15, BLAST_RADIUS * 0.8, 0.12);

        ArrayList<Entity> affected = GrenadeEntity.getPlayersAffectedByExplosion(level, x, y, z, BLAST_RADIUS);
        // 游戏未开始：不结算击杀，改为对爆炸范围内的玩家施加击退
        if (!SREGameWorldComponent.KEY.get(level).isRunning()) {
            applyKnockback(level, shooter, pos, affected, hitPlayer);
            return;
        }
        // 只有游戏正式开始后才产生击杀结算
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
                GameUtils.killPlayer(victim, true, shooter, GameConstants.DeathReasons.WISDEL_SHOTGUN);
            } else if (entity instanceof PuppeteerBodyEntity bodyEntity) {
                bodyEntity.playerHurt(shooter, GameConstants.DeathReasons.WISDEL_SHOTGUN);
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

    /**
     * 游戏未开始时的击退：以爆炸中心 {@code center} 为源，对范围内每个玩家沿「爆心→玩家」的水平方向
     * 施加击退速度，强度随距离线性衰减（越近越强），并附带一定上抛。不造成任何伤害或击杀。
     * <p>
     * 被射线直接命中的玩家（{@code hitPlayer}）例外：其与爆心几乎重合，按径向计算会退化甚至反向
     * （表现为被拉向发射者）。因此改为沿弹道方向击退，保证沿发射方向被推开。
     */
    private static void applyKnockback(ServerLevel level, Player shooter, Vec3 center,
            ArrayList<Entity> affected, @Nullable Player hitPlayer) {
        for (Entity entity : affected) {
            if (!(entity instanceof Player victim)) {
                continue;
            }
            Vec3 fromCenter = victim.position().subtract(center);
            double horizontal = Math.sqrt(fromCenter.x * fromCenter.x + fromCenter.z * fromCenter.z);
            double distance = victim.position().distanceTo(center);
            double power = KNOCKBACK_STRENGTH * Math.max(0.0, 1.0 - distance / (BLAST_RADIUS + 1.0E-3));
            Vec3 push;
            if (victim == hitPlayer) {
                // 被直接命中：沿弹道方向击退，绝不可能朝发射者反向拉扯
                Vec3 look = shooter.getLookAngle();
                Vec3 flat = new Vec3(look.x, 0.0, look.z);
                if (flat.lengthSqr() < 1.0E-8) {
                    // 视线垂直（水平分量为零）时改用「射手→目标」方向兜底
                    Vec3 away = victim.position().subtract(shooter.position());
                    flat = new Vec3(away.x, 0.0, away.z);
                }
                if (flat.lengthSqr() < 1.0E-8) {
                    double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
                    flat = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
                }
                push = flat.normalize();
            } else if (horizontal < 1.0E-3) {
                // 正好位于爆心：给随机水平方向，避免原地不动
                double angle = level.getRandom().nextDouble() * Math.PI * 2.0;
                push = new Vec3(Math.cos(angle), 0.0, Math.sin(angle));
            } else {
                push = new Vec3(fromCenter.x / horizontal, 0.0, fromCenter.z / horizontal);
            }
            Vec3 motion = victim.getDeltaMovement();
            victim.setDeltaMovement(
                    motion.x + push.x * power,
                    motion.y + power * 0.6,
                    motion.z + push.z * power
            );
            victim.hurtMarked = true;
        }
    }

    /** 枪口火光（与左轮 / 救赎之枪一致的贴手粒子）。 */
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
