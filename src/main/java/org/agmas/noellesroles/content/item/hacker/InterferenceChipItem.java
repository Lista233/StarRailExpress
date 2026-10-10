package org.agmas.noellesroles.content.item.hacker;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.roles.hacker.HackerPayload;

/**
 * 干扰芯片 - 黑客（林然）专属标记物品
 * <p>
 * 手持右键对视线内的玩家进行标记，获取其真实 IP 地址。
 * 标记成功不播放任何音效（保持标记行为隐匿，避免提前暴露）。
 */
public class InterferenceChipItem extends Item {

    private static final double RANGE = 16.0;

    public InterferenceChipItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player user, InteractionHand hand) {
        ItemStack stack = user.getItemInHand(hand);

        if (level.isClientSide() || !(user instanceof ServerPlayer hacker)) {
            return InteractionResultHolder.consume(stack);
        }

        // 检查是否在冷却中（4秒 = 80 tick）
        if (hacker.getCooldowns().isOnCooldown(this)) {
            int remainingTicks = (int) Math.ceil(
                    hacker.getCooldowns().getCooldownPercent(this, 0) * 80);
            hacker.displayClientMessage(
                    Component.translatable("message.hacker.chip_cooldown", remainingTicks / 20 + 1)
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        // 检查是否是黑客职业
        var gameWorld = SREGameWorldComponent.KEY.get(level);
        if (gameWorld == null || !gameWorld.isRole(hacker, QUSTRoles.HACKER)) {
            return InteractionResultHolder.fail(stack);
        }

        var data = QUSTComponentKeys.Keys.HACKER.maybeGet(hacker).orElse(null);
        if (data == null) {
            return InteractionResultHolder.fail(stack);
        }

        // Raycast 找视线内的玩家
        ServerPlayer target = rayTraceTarget(level, hacker);
        if (target == null) {
            hacker.displayClientMessage(
                    Component.translatable("message.hacker.no_target")
                            .withStyle(net.minecraft.ChatFormatting.RED),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        // 不能标记自己
        if (target.getUUID().equals(hacker.getUUID())) {
            hacker.displayClientMessage(
                    Component.literal("§c不能标记自己！"),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        // 检查是否已标记
        if (data.isMarked(target.getUUID())) {
            hacker.displayClientMessage(
                    Component.translatable("message.hacker.already_marked")
                            .withStyle(net.minecraft.ChatFormatting.YELLOW),
                    true);
            return InteractionResultHolder.fail(stack);
        }

        // 标记玩家
        data.markPlayer(target.getUUID());

        // 记录标记时间戳（用于延迟通知）
        int currentTick = (int) level.getGameTime();
        data.setMarkTimestamp(target.getUUID(), currentTick);

        // 获取真实 IP 地址
        String ip = extractIP(target);

        // 获取目标职业名称
        String roleName = getTargetRoleName(target, gameWorld);

        // 生成红石粉粒子效果（干扰芯片材质）
        if (level instanceof net.minecraft.server.level.ServerLevel sl) {
            for (int i = 0; i < 30; i++) {
                sl.sendParticles(net.minecraft.core.particles.DustParticleOptions.REDSTONE,
                        target.getX(), target.getY() + 1.0, target.getZ(),
                        1, 0.5, 0.5, 0.5, 0);
            }
        }

        // 标记不播放音效：音效会在黑客位置向周围广播，提前暴露标记行为，
        // 破坏「8秒后延迟通知被标记者」的隐匿设计

        // 发送标记信息给黑客（立即显示）
        ServerPlayNetworking.send(
                hacker,
                new HackerPayload.ShowMarkedInfo(
                        target.getName().getString(),
                        target.getUUID(),
                        ip,
                        roleName
                )
        );

        // 安排8秒后通知被标记者
        data.addPendingNotification(target.getUUID(), target.getName().getString(), ip, currentTick);

        hacker.displayClientMessage(
                Component.literal("§a已标记玩家：" + target.getName().getString()),
                true);

        // 添加4秒冷却（80 tick）
        hacker.getCooldowns().addCooldown(this, 80);

        return InteractionResultHolder.consume(stack);
    }

    /**
     * Raycast 检测视线内的其他玩家
     */
    private static ServerPlayer rayTraceTarget(Level level, ServerPlayer shooter) {
        Vec3 start = shooter.getEyePosition();
        Vec3 direction = shooter.getViewVector(1.0F);
        Vec3 end = start.add(direction.scale(RANGE));

        BlockHitResult blockHit = level.clip(new ClipContext(start, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        if (blockHit.getType() != HitResult.Type.MISS) {
            end = blockHit.getLocation();
        }

        EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, shooter, start, end,
                shooter.getBoundingBox().expandTowards(end.subtract(start)).inflate(1.0D),
                entity -> entity instanceof ServerPlayer candidate
                        && !candidate.getUUID().equals(shooter.getUUID())
                        && GameUtils.isPlayerAliveAndSurvival(candidate));

        return hit != null && hit.getEntity() instanceof ServerPlayer sp ? sp : null;
    }

    /**
     * 获取目标玩家的角色名称（中文翻译）
     */
    private static String getTargetRoleName(ServerPlayer target, SREGameWorldComponent gameWorld) {
        var role = gameWorld.getRole(target);
        if (role == null) return "未知";
        return org.agmas.noellesroles.utils.RoleUtils.getRoleName(role.identifier()).getString();
    }

    /**
     * 从玩家连接中提取真实 IP 地址
     */
    private static String extractIP(ServerPlayer player) {
        String ip = player.getIpAddress();
        if (ip == null || ip.isEmpty()) {
            return "unknown";
        }
        // 去掉前导斜杠和端口号
        if (ip.contains("/")) {
            ip = ip.substring(ip.lastIndexOf("/") + 1);
        }
        if (ip.contains(":")) {
            ip = ip.substring(0, ip.indexOf(":"));
        }
        return ip;
    }
}
