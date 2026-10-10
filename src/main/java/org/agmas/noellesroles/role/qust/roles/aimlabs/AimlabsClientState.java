package org.agmas.noellesroles.role.qust.roles.aimlabs;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Aimlabs 客户端状态缓存：存储当前活跃会话的信息供 HUD 渲染使用。
 */
public class AimlabsClientState {

    private static boolean active = false;
    private static BlockPos blockPos;
    private static AimlabsBlockEntity.GameMode mode = AimlabsBlockEntity.GameMode.TIMED;
    private static int remainingSeconds;
    private static int score;
    private static int countdownTotal;
    private static int targetHits;
    private static int elapsedTicks;
    private static int areaWidth;
    private static int areaHeight;
    private static float sphereRadius;
    private static int maxSpheres;
    private static Direction facing = Direction.SOUTH;

    /** 最终分数（结束后显示）。 */
    private static int finalScore = -1;
    /** 最终用时 tick（计次模式结束后显示）。 */
    private static int finalElapsedTicks = 0;
    /** 最终分数/成绩显示剩余 tick。 */
    private static int finalScoreDisplayTicks = 0;

    public static void onStart(AimlabsPayload.StartSession payload) {
        active = true;
        blockPos = payload.blockPos();
        mode = payload.mode();
        countdownTotal = payload.countdownSeconds();
        remainingSeconds = payload.countdownSeconds();
        targetHits = payload.targetHits();
        score = 0;
        elapsedTicks = 0;
        areaWidth = payload.areaWidth();
        areaHeight = payload.areaHeight();
        sphereRadius = payload.sphereRadius();
        maxSpheres = payload.maxSpheres();
        facing = payload.facing();
    }

    public static void onUpdate(AimlabsPayload.SessionUpdate payload) {
        remainingSeconds = payload.remainingSeconds();
        score = payload.score();
        elapsedTicks = payload.elapsedTicks();
    }

    public static void onHit(AimlabsPayload.TargetHit payload) {
        // 仅自己可听到的击中音效（通过客户端音效管理器，不会广播到服务端）
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player != null) {
            mc.getSoundManager().play(
                    net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(
                            net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP, 1.4f)
            );
        }
    }

    public static void onEnd(AimlabsPayload.EndSession payload) {
        active = false;
        finalScore = payload.finalScore();
        finalElapsedTicks = payload.elapsedTicks();
        finalScoreDisplayTicks = 100; // 5秒
    }

    public static void tick() {
        if (active && mode == AimlabsBlockEntity.GameMode.COUNT) {
            // 计次模式：客户端自增计时（更流畅）
            elapsedTicks++;
        }
        if (finalScoreDisplayTicks > 0) {
            finalScoreDisplayTicks--;
        }
    }

    public static boolean isActive() { return active; }
    public static AimlabsBlockEntity.GameMode getMode() { return mode; }
    public static int getRemainingSeconds() { return remainingSeconds; }
    public static int getScore() { return score; }
    public static int getCountdownTotal() { return countdownTotal; }
    public static int getTargetHits() { return targetHits; }
    public static int getElapsedTicks() { return elapsedTicks; }
    public static int getFinalScore() { return finalScore; }
    public static int getFinalElapsedTicks() { return finalElapsedTicks; }
    public static int getFinalScoreDisplayTicks() { return finalScoreDisplayTicks; }
    public static float getSphereRadius() { return sphereRadius; }

    public static void clear() {
        active = false;
        finalScore = -1;
        finalElapsedTicks = 0;
        finalScoreDisplayTicks = 0;
    }
}
