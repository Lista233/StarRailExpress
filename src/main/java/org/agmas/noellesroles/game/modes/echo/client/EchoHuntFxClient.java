package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds;
import org.joml.Vector3f;

/**
 * 追猎攻击的客户端特效：刀光、命中血花、扑击冲击环、擦刀血线。
 *
 * <p>服务端每类特效只发一个包（id + 原点 + 参数），这里按 id 逐 tick 在本地摆形状，
 * 客户端粒子不进网络。所有形状都由参数决定，不读实体状态，因此不会与判定产生分歧，
 * 也不会因为丢包导致状态不一致——丢包只是少一段特效。
 *
 * <p>形状是「一次性播放」的：收到包后记录一条动画，由客户端 tick 推进若干帧，
 * 与实体 tick 解耦，因此 20 Hz 的实体刷新不会让刀光变成一帧闪现。
 */
public final class EchoHuntFxClient {
    private EchoHuntFxClient() { }

    /** 血雾/血珠。 */
    private static final DustParticleOptions BLOOD_DUST = new DustParticleOptions(new Vector3f(0.42F, 0.03F, 0.05F), 1.35F);
    private static final DustParticleOptions BLOOD_DEEP = new DustParticleOptions(new Vector3f(0.24F, 0.01F, 0.02F), 1.6F);
    /** 刀光：暖白偏金，与 HUD 的金色一致。 */
    private static final DustParticleOptions BLADE = new DustParticleOptions(new Vector3f(1.0F, 0.93F, 0.78F), 1.15F);
    /** 扑击：赤红。 */
    private static final DustParticleOptions RAGE = new DustParticleOptions(new Vector3f(0.95F, 0.22F, 0.12F), 1.7F);

    /** 同时存在的特效上限，防止极端情况下堆积。 */
    private static final int MAX_ACTIVE = 24;

    private record Burst(ResourceKind kind, Vec3 origin, float yaw, float pitch, float strength,
                         int elapsed, int duration, float extra) { }

    private enum ResourceKind { SLASH, BLOOD, IMPACT, WIPE }

    private static final java.util.ArrayDeque<Burst> ACTIVE = new java.util.ArrayDeque<>();

    public static void initialize() {
        CustomParticleHandlers.register(EchoHuntFxIds.SLASH, (level, origin, durationTicks, params) -> {
            float yaw = params.length > 0 ? params[0] : 0F;
            float pitch = params.length > 1 ? params[1] : 0F;
            float strength = params.length > 2 ? params[2] : 1F;
            spawn(new Burst(ResourceKind.SLASH, origin, yaw, pitch, strength, 0, 9, 0F));
        });
        CustomParticleHandlers.register(EchoHuntFxIds.BLOOD, (level, origin, durationTicks, params) -> {
            float yaw = params.length > 0 ? params[0] : 0F;
            float strength = params.length > 1 ? params[1] : 1F;
            spawn(new Burst(ResourceKind.BLOOD, origin, yaw, 0F, strength, 0, 14, 0F));
        });
        CustomParticleHandlers.register(EchoHuntFxIds.IMPACT, (level, origin, durationTicks, params) -> {
            float strength = params.length > 0 ? params[0] : 1F;
            spawn(new Burst(ResourceKind.IMPACT, origin, 0F, 0F, strength, 0, 16, 0F));
        });
        CustomParticleHandlers.register(EchoHuntFxIds.WIPE, (level, origin, durationTicks, params) -> {
            float yaw = params.length > 0 ? params[0] : 0F;
            spawn(new Burst(ResourceKind.WIPE, origin, yaw, 0F, 1F, 0, 16, 0F));
        });
        ClientTickEvents.END_CLIENT_TICK.register(EchoHuntFxClient::tick);
    }

    private static void spawn(Burst burst) {
        while (ACTIVE.size() >= MAX_ACTIVE) ACTIVE.pollFirst();
        ACTIVE.addLast(burst);
    }

    private static void tick(Minecraft client) {
        if (ACTIVE.isEmpty() || client.level == null) return;
        ClientLevel level = client.level;
        var iterator = ACTIVE.iterator();
        java.util.ArrayList<Burst> next = new java.util.ArrayList<>(ACTIVE.size());
        while (iterator.hasNext()) {
            Burst burst = iterator.next();
            paint(level, burst);
            if (burst.elapsed + 1 < burst.duration) {
                next.add(new Burst(burst.kind, burst.origin, burst.yaw, burst.pitch, burst.strength,
                        burst.elapsed + 1, burst.duration, burst.extra));
            }
        }
        ACTIVE.clear();
        ACTIVE.addAll(next);
    }

    private static void paint(ClientLevel level, Burst burst) {
        float t = burst.duration <= 0 ? 1F : burst.elapsed / (float) burst.duration;
        switch (burst.kind) {
            case SLASH -> paintSlash(level, burst, t);
            case BLOOD -> paintBlood(level, burst, t);
            case IMPACT -> paintImpact(level, burst, t);
            case WIPE -> paintWipe(level, burst, t);
        }
    }

    /** 刀光：从起手侧扫到收势侧的一段弧，前半程画得快。 */
    private static void paintSlash(ClientLevel level, Burst burst, float t) {
        Vec3 forward = look(burst.yaw, burst.pitch);
        Vec3 up = new Vec3(0D, 1D, 0D);
        Vec3 side = forward.cross(up);
        side = side.lengthSqr() < 1.0E-6D ? new Vec3(1D, 0D, 0D) : side.normalize();
        Vec3 lift = side.cross(forward).normalize();
        double reach = 1.4D + burst.strength * 1.9D;
        double sweep = Mth.clamp(t / 0.55F, 0F, 1F);
        int steps = 18;
        for (int i = 0; i <= steps; i++) {
            double p = i / (double) steps;
            if (p > sweep) break;
            double along = reach * (0.35D + 0.65D * p);
            double lateral = (p - 0.5D) * 1.6D * burst.strength;
            double arc = Math.sin(p * Math.PI) * 0.55D;
            Vec3 at = burst.origin.add(forward.scale(along)).add(side.scale(lateral)).add(lift.scale(arc));
            level.addParticle(BLADE, at.x, at.y, at.z, side.x * 0.02D, 0.01D, side.z * 0.02D);
            if (i % 3 == 0) level.addParticle(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 0D, 0D, 0D);
        }
    }

    /** 命中血花：中心血雾 + 沿刀势方向的锥形血珠，逐渐下坠。 */
    private static void paintBlood(ClientLevel level, Burst burst, float t) {
        Vec3 direction = look(burst.yaw, 0F);
        int count = Math.round(12 * burst.strength);
        if (t < 0.55F) {
            for (int i = 0; i < count; i++) {
                double spread = 0.16D + 0.5D * t;
                double dx = (level.random.nextDouble() - 0.5D) * spread + direction.x * 0.35D;
                double dz = (level.random.nextDouble() - 0.5D) * spread + direction.z * 0.35D;
                double dy = level.random.nextDouble() * 0.35D + 0.05D;
                level.addParticle(i % 3 == 0 ? BLOOD_DEEP : BLOOD_DUST,
                        burst.origin.x + dx * 0.4D, burst.origin.y + dy, burst.origin.z + dz * 0.4D,
                        dx * 1.6D, 0.55D * (1F - t), dz * 1.6D);
            }
        }
        if (t < 0.35F) {
            level.addParticle(ParticleTypes.DAMAGE_INDICATOR, burst.origin.x, burst.origin.y + 0.9D, burst.origin.z, 0D, 0D, 0D);
        }
        if (t > 0.2F && t < 0.8F && burst.strength > 1.2F) {
            for (int i = 0; i < 4; i++) {
                double angle = level.random.nextDouble() * Math.PI * 2D;
                level.addParticle(ParticleTypes.CRIMSON_SPORE,
                        burst.origin.x + Math.cos(angle) * 0.35D, burst.origin.y + 0.15D,
                        burst.origin.z + Math.sin(angle) * 0.35D, 0D, 0.02D, 0D);
            }
        }
    }

    /** 扑击冲击环：贴地扩散的一圈尘与赤红火星。 */
    private static void paintImpact(ClientLevel level, Burst burst, float t) {
        double radius = 0.6D + t * 2.6D;
        int ring = 22;
        for (int i = 0; i < ring; i++) {
            double angle = Math.PI * 2D * i / ring;
            double x = burst.origin.x + Math.cos(angle) * radius;
            double z = burst.origin.z + Math.sin(angle) * radius;
            level.addParticle(i % 2 == 0 ? RAGE : ParticleTypes.CLOUD, x, burst.origin.y + 0.06D, z,
                    Math.cos(angle) * 0.03D, 0.02D, Math.sin(angle) * 0.03D);
        }
        if (t < 0.5F) {
            for (int i = 0; i < 6; i++) {
                double angle = Math.PI * 2D * i / 6D + t * 0.6D;
                level.addParticle(ParticleTypes.CRIT,
                        burst.origin.x + Math.cos(angle) * radius * 0.55D, burst.origin.y + 0.3D,
                        burst.origin.z + Math.sin(angle) * radius * 0.55D, 0D, 0.12D, 0D);
            }
        }
    }

    /** 擦刀血线：沿刀身方向甩出的一道细长血珠，快速下坠。 */
    private static void paintWipe(ClientLevel level, Burst burst, float t) {
        Vec3 direction = look(burst.yaw, 0F);
        Vec3 side = direction.cross(new Vec3(0D, 1D, 0D));
        side = side.lengthSqr() < 1.0E-6D ? new Vec3(1D, 0D, 0D) : side.normalize();
        int steps = 10;
        for (int i = 0; i <= steps; i++) {
            double p = i / (double) steps;
            double along = 0.45D + p * 0.9D;
            double lateral = (0.35D - p * 0.7D) * (0.4D + t);
            Vec3 at = burst.origin.add(direction.scale(along)).add(side.scale(lateral));
            double fall = -0.35D * t;
            level.addParticle(i % 2 == 0 ? BLOOD_DUST : BLOOD_DEEP,
                    at.x, at.y + 0.9D + fall, at.z,
                    side.x * 0.05D, -0.12D - t * 0.1D, side.z * 0.05D);
        }
    }

    private static Vec3 look(float yaw, float pitch) {
        float yawRad = yaw * Mth.DEG_TO_RAD;
        float pitchRad = pitch * Mth.DEG_TO_RAD;
        float cosPitch = Mth.cos(pitchRad);
        Vec3 vec = new Vec3(-Mth.sin(yawRad) * cosPitch, -Mth.sin(pitchRad), Mth.cos(yawRad) * cosPitch);
        return vec.lengthSqr() < 1.0E-6D ? new Vec3(0D, 0D, 1D) : vec.normalize();
    }
}