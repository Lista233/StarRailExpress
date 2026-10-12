package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import com.mojang.math.Transformation;

import io.wifi.starrailexpress.api.hit.HitPriority;
import io.wifi.starrailexpress.api.hit.HitType;
import io.wifi.starrailexpress.api.hit.SREHitManager;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.tag.TMMItemTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 任务点旁边的裂缝。方块展示负责外形，交互实体负责被手枪打中。
 * 击碎限定好人阵营的手枪，含平民和警长。
 */
public final class CommandBlockCracks {
    public static final String TAG = "sre.command_block_crack";

    private CommandBlockCracks() {
    }

    public static void registerAim() {
        SREHitManager.registerTargetFilter((entity, attacker, type) -> {
            if (type == HitType.GUN && isCrack(entity)) {
                return HitPriority.FALLBACK;
            }
            return null;
        });
    }

    public static boolean isCrack(Entity entity) {
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        if (entity.getTags().contains(TAG)) {
            return true;
        }
        Component name = entity.getCustomName();
        return name != null && TAG.equals(name.getString());
    }

    public static boolean isVisibleCrack(Entity entity) {
        return entity instanceof Display.BlockDisplay && isCrack(entity);
    }

    public static void spawn(ServerLevel level, int quota) {
        if (GameUtils.taskBlocks == null || GameUtils.taskBlocks.isEmpty() || quota <= 0) {
            return;
        }
        List<BlockPos> points = new ArrayList<>(GameUtils.taskBlocks.keySet());
        Collections.shuffle(points, new java.util.Random(level.getGameTime()));
        int wanted = Math.min(points.size(), Math.max(2, quota));
        int spawned = 0;
        for (BlockPos point : points) {
            if (spawned >= wanted) {
                break;
            }
            Vec3 spot = beside(level, point);
            if (spot == null) {
                continue;
            }
            Display.BlockDisplay crack = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
            crack.setPos(spot.x, spot.y, spot.z);
            mark(crack);
            crack.setBlockState(Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState());
            crack.setWidth(1.2F);
            crack.setHeight(1.8F);
            float yaw = level.random.nextFloat() * 360.0F;
            crack.setTransformation(new Transformation(
                    new Vector3f(-0.6F, 0.0F, -0.15F),
                    new Quaternionf().rotateY((float) Math.toRadians(yaw)),
                    new Vector3f(1.2F, 1.6F, 0.3F),
                    new Quaternionf()));
            level.addFreshEntity(crack);

            Interaction hitbox = new Interaction(EntityType.INTERACTION, level);
            hitbox.setPos(spot.x, spot.y + 0.9D, spot.z);
            hitbox.setBoundingBox(new AABB(spot.x - 0.6D, spot.y, spot.z - 0.6D, spot.x + 0.6D, spot.y + 1.8D,
                    spot.z + 0.6D));
            mark(hitbox);
            level.addFreshEntity(hitbox);
            spawned++;
        }
    }

    /** 好人阵营（平民和警长）的手枪才能击碎。返回 true 表示这一枪打在裂缝上并已结算。 */
    public static boolean tryShatter(ServerPlayer shooter, Entity target) {
        if (shooter == null || !isCrack(target)) {
            return false;
        }
        if (!shooter.getMainHandItem().is(TMMItemTags.GUNS)) {
            return false;
        }
        var role = SREGameWorldComponent.KEY.get(shooter.level()).getRole(shooter);
        if (role == null || !role.isInnocent() || role.canUseKiller() || role.isNeutrals()) {
            return false;
        }
        Vec3 at = target.position();
        if (shooter.level() instanceof ServerLevel level) {
            discardNear(level, at);
            level.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.8D, at.z, 16, 0.3D, 0.5D, 0.3D, 0.05D);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.GLASS_BREAK, SoundSource.PLAYERS, 0.7F, 0.7F);
            CommandBlockDirector.addDestruction(level);
        }
        shooter.displayClientMessage(Component.translatable("message.noellesroles.command_block.crack_hit"), true);
        return true;
    }

    public static Entity alongLook(ServerPlayer player, double range) {
        if (player == null) {
            return null;
        }
        HitResult hit = ProjectileUtil.getHitResultOnViewVector(player, CommandBlockCracks::isCrack, (float) range);
        if (hit instanceof EntityHitResult entityHit && isCrack(entityHit.getEntity())) {
            return entityHit.getEntity();
        }
        return null;
    }

    /** 裂缝比当前准星目标更近时，让手枪打到裂缝，而不是穿过它打到后面的人。 */
    public static HitResult prefer(Player user, HitResult current, float range) {
        if (user == null) {
            return current;
        }
        HitResult crack = ProjectileUtil.getHitResultOnViewVector(user, CommandBlockCracks::isCrack, range);
        if (!(crack instanceof EntityHitResult crackHit) || !isCrack(crackHit.getEntity())) {
            return current;
        }
        if (!(current instanceof EntityHitResult currentHit)) {
            return crack;
        }
        return crackHit.getEntity().distanceToSqr(user) < currentHit.getEntity().distanceToSqr(user) ? crack : current;
    }

    public static void discard(ServerLevel level) {
        if (level == null) {
            return;
        }
        List<Entity> found = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (isCrack(entity)) {
                found.add(entity);
            }
        }
        for (Entity entity : found) {
            entity.discard();
        }
    }

    private static void mark(Entity entity) {
        entity.addTag(TAG);
        entity.setCustomName(Component.literal(TAG));
        entity.setCustomNameVisible(false);
    }

    private static void discardNear(ServerLevel level, Vec3 at) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : level.getEntities((Entity) null, new AABB(at, at).inflate(1.2D), CommandBlockCracks::isCrack)) {
            found.add(entity);
        }
        for (Entity entity : found) {
            entity.discard();
        }
    }

    private static Vec3 beside(ServerLevel level, BlockPos point) {
        int[][] offsets = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 }, { 2, 0 }, { -2, 0 }, { 0, 2 }, { 0, -2 } };
        for (int[] offset : offsets) {
            Vec3 at = Vec3.atBottomCenterOf(point.offset(offset[0], 0, offset[1]));
            Vec3 spot = StandSpots.find(level, at, new Vec3(0.0D, 0.0D, 1.0D), 1.0D);
            if (spot != null) {
                return spot;
            }
        }
        return null;
    }
}
