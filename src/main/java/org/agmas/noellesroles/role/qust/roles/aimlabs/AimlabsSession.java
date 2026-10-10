package org.agmas.noellesroles.role.qust.roles.aimlabs;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.init.ModEntities;
import org.agmas.noellesroles.init.ModItems;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aimlabs 服务端游戏会话：管理靶标生成、倒计时/计次、计分。
 * <p>
 * 每个玩家拥有独立的会话，互不影响。
 */
public class AimlabsSession {

    /** 活跃的会话表（按玩家UUID索引，每个玩家独立）。 */
    private static final Map<UUID, AimlabsSession> SESSIONS = new ConcurrentHashMap<>();

    private final ServerPlayer player;
    private final BlockPos blockPos;
    private final ServerLevel level;
    /** 是否为虚拟会话（不绑定方块，由全局 tick 驱动）。 */
    private final boolean virtual;

    // ── 配置 ──
    private final AimlabsBlockEntity.GameMode mode;
    private int countdownSeconds;
    private int targetHits;
    private int areaWidth;
    private int areaHeight;
    private float sphereRadius;
    private int maxSpheres;
    private int heightOffset;
    private Direction facing;

    // ── 运行时 ──
    private int remainingTicks;
    private int elapsedTicks;
    private int score;
    private final List<AimlabsTargetEntity> activeTargets = new ArrayList<>();
    private final Random random = new Random();
    private boolean ended = false;

    public AimlabsSession(ServerPlayer player, BlockPos blockPos, ServerLevel level,
                          AimlabsBlockEntity.GameMode mode,
                          int countdownSeconds, int targetHits,
                          int areaWidth, int areaHeight,
                          float sphereRadius, int maxSpheres, int heightOffset,
                          Direction facing) {
        this(player, blockPos, level, mode, countdownSeconds, targetHits,
                areaWidth, areaHeight, sphereRadius, maxSpheres, heightOffset, facing, false);
    }

    /** 完整构造（含虚拟标记）。 */
    public AimlabsSession(ServerPlayer player, BlockPos blockPos, ServerLevel level,
                          AimlabsBlockEntity.GameMode mode,
                          int countdownSeconds, int targetHits,
                          int areaWidth, int areaHeight,
                          float sphereRadius, int maxSpheres, int heightOffset,
                          Direction facing, boolean virtual) {
        this.player = player;
        this.blockPos = blockPos;
        this.level = level;
        this.virtual = virtual;
        this.mode = mode;
        this.countdownSeconds = countdownSeconds;
        this.targetHits = targetHits;
        this.areaWidth = areaWidth;
        this.areaHeight = areaHeight;
        this.sphereRadius = sphereRadius;
        this.maxSpheres = maxSpheres;
        this.heightOffset = heightOffset;
        this.facing = facing;
        this.remainingTicks = countdownSeconds * 20;
    }

    // ── 静态管理 ──

    /** 根据方块位置查找活跃会话。 */
    public static List<AimlabsSession> getSessionsByBlock(BlockPos pos) {
        return SESSIONS.values().stream().filter(s -> s.blockPos.equals(pos)).toList();
    }

    public static boolean hasPlayerSession(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static AimlabsSession getPlayerSession(ServerPlayer player) {
        return SESSIONS.get(player.getUUID());
    }

    /** 终止所有活跃会话（游戏开始时调用）。 */
    public static void endAll() {
        for (AimlabsSession session : SESSIONS.values()) {
            session.end();
        }
        SESSIONS.clear();
    }

    /** tick所有与指定方块关联的会话（方块实体ticker调用）。 */
    public static void tickAllForBlock(BlockPos pos) {
        for (AimlabsSession session : SESSIONS.values()) {
            if (!session.virtual && session.blockPos.equals(pos)) {
                session.tick();
            }
        }
    }

    /** tick所有虚拟会话（全局tick事件调用）。 */
    public static void tickAll() {
        for (AimlabsSession session : SESSIONS.values()) {
            if (session.virtual) {
                session.tick();
            }
        }
    }

    // ── 启动 ──

    public void start() {
        SESSIONS.put(player.getUUID(), this);
        ServerPlayNetworking.send(player, new AimlabsPayload.StartSession(
                blockPos, mode, countdownSeconds, targetHits,
                areaWidth, areaHeight, sphereRadius, maxSpheres, facing));
        for (int i = 0; i < maxSpheres; i++) {
            spawnTarget();
        }
    }

    // ── 每 tick 调用 ──

    public void tick() {
        if (ended) return;

        if (mode == AimlabsBlockEntity.GameMode.TIMED) {
            remainingTicks--;
            if (remainingTicks <= 0) {
                end();
                return;
            }
            if (remainingTicks % 10 == 0) {
                syncState();
            }
        } else {
            // COUNT 模式：持续计时
            elapsedTicks++;
            if (elapsedTicks % 10 == 0) {
                syncState();
            }
        }
    }

    // ── 命中处理 ──

    public void onTargetHit(AimlabsTargetEntity target, Player attacker) {
        if (ended) return;
        if (attacker != player) return;

        score++;
        activeTargets.remove(target);
        ServerPlayNetworking.send(player, new AimlabsPayload.TargetHit(target.getId()));
        target.discard();
        spawnTarget();

        // 场景任务：射击练习 — 报告击中
        org.agmas.noellesroles.scene.SceneTaskManager.reportAimlabsPracticeHit(player);

        // COUNT 模式：达到目标击中次数则结束
        if (mode == AimlabsBlockEntity.GameMode.COUNT && score >= targetHits) {
            syncState();
            end();
            return;
        }

        syncState();
    }

    // ── 靶标生成 ──

    private void spawnTarget() {
        if (activeTargets.size() >= maxSpheres) return;

        List<Vec3> occupied = activeTargets.stream()
                .map(AimlabsTargetEntity::position)
                .toList();

        for (int attempt = 0; attempt < 20; attempt++) {
            int col = random.nextInt(areaWidth);
            int row = random.nextInt(areaHeight);

            double x, y, z;
            y = blockPos.getY() + heightOffset + row + 0.5;

            switch (facing) {
                case SOUTH -> {
                    x = blockPos.getX() + 0.5 - (col - (areaWidth - 1) / 2.0);
                    z = blockPos.getZ() + 0.5;
                }
                case NORTH -> {
                    x = blockPos.getX() + 0.5 + (col - (areaWidth - 1) / 2.0);
                    z = blockPos.getZ() + 0.5;
                }
                case EAST -> {
                    x = blockPos.getX() + 0.5;
                    z = blockPos.getZ() + 0.5 - (col - (areaWidth - 1) / 2.0);
                }
                case WEST -> {
                    x = blockPos.getX() + 0.5;
                    z = blockPos.getZ() + 0.5 + (col - (areaWidth - 1) / 2.0);
                }
                default -> {
                    x = blockPos.getX() + 0.5;
                    z = blockPos.getZ() + 0.5;
                }
            }

            Vec3 pos = new Vec3(x, y, z);

            boolean overlap = false;
            for (Vec3 occ : occupied) {
                if (occ.distanceTo(pos) < sphereRadius * 2.5) {
                    overlap = true;
                    break;
                }
            }
            if (overlap) continue;

            AimlabsTargetEntity target = new AimlabsTargetEntity(ModEntities.AIMLABS_TARGET, level);
            target.setPos(pos);
            target.setOrigin(blockPos);
            target.setSphereRadius(sphereRadius);
            target.refreshDimensions();
            level.addFreshEntity(target);
            activeTargets.add(target);
            return;
        }
    }

    // ── 状态同步 ──

    private void syncState() {
        int remaining = (mode == AimlabsBlockEntity.GameMode.TIMED)
                ? Math.max(0, (remainingTicks + 19) / 20)
                : 0;
        ServerPlayNetworking.send(player, new AimlabsPayload.SessionUpdate(remaining, score, elapsedTicks));
    }

    // ── 结束 ──

    public void end() {
        if (ended) return;
        ended = true;

        // 结束包：分数 + 已用时间（计次模式用于显示成绩）
        ServerPlayNetworking.send(player, new AimlabsPayload.EndSession(score, elapsedTicks));

        for (AimlabsTargetEntity target : activeTargets) {
            if (!target.isRemoved()) {
                target.discard();
            }
        }
        activeTargets.clear();

        SESSIONS.remove(player.getUUID());
        AimlabsSession.giveOrRemovePracticeRevolver(player, false);
    }

    // ── 练习手枪管理 ──

    /** 给予或收回练习手枪。 */
    public static void giveOrRemovePracticeRevolver(ServerPlayer player, boolean give) {
        if (give) {
            var stack = new net.minecraft.world.item.ItemStack(ModItems.PRACTICE_REVOLVER);
            player.getInventory().add(stack);
        } else {
            var inv = player.getInventory();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                if (inv.getItem(i).is(ModItems.PRACTICE_REVOLVER)) {
                    inv.setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                }
            }
        }
    }

    // ── Getters ──

    public ServerPlayer getPlayer() { return player; }
    public BlockPos getBlockPos() { return blockPos; }
    public AimlabsBlockEntity.GameMode getMode() { return mode; }
    public int getScore() { return score; }
    public int getElapsedTicks() { return elapsedTicks; }
    public int getRemainingSeconds() { return Math.max(0, (remainingTicks + 19) / 20); }
    public int getTargetHits() { return targetHits; }
    public boolean isEnded() { return ended; }
}
