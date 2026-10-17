package org.agmas.noellesroles.client;

import org.agmas.noellesroles.game.command_block.LegendFx;

import io.wifi.starrailexpress.client.particle.CustomParticleHandlers;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

/** 远境 / 白瞳的光环。本地摆点，不额外发包。 */
public final class LegendRingClientFx {
    private static final DustParticleOptions PURPLE = dust(0.62F, 0.22F, 1.0F, 1.3F);
    private static final DustParticleOptions AMBER = dust(1.0F, 0.62F, 0.15F, 1.2F);
    private static final DustParticleOptions WHITE = dust(0.95F, 0.95F, 1.0F, 1.4F);
    private static final DustParticleOptions GOLD = dust(0.95F, 0.78F, 0.25F, 1.1F);
    private static final DustParticleOptions PAPER = dust(0.96F, 0.93F, 0.84F, 1.2F);
    private static final DustParticleOptions FOG = dust(0.55F, 0.62F, 0.68F, 1.5F);
    private static final DustParticleOptions CANDLE = dust(1.0F, 0.55F, 0.12F, 1.3F);

    private LegendRingClientFx() {
    }

    public static void register() {
        CustomParticleHandlers.register(LegendFx.RING, LegendRingClientFx::play);
    }

    private static void play(ClientLevel level, Vec3 origin, int durationTicks, float[] params) {
        float radius = params.length > 0 ? params[0] : 2.0F;
        float style = params.length > 1 ? params[1] : 0.0F;
        int kind = Math.round(style);
        int count = kind == 3 ? 36 : 24;
        DustParticleOptions dust = switch (kind) {
            case 1 -> AMBER;
            case 2, 3 -> WHITE;
            case 4 -> GOLD;
            case 5 -> PAPER;
            case 6 -> FOG;
            case 7 -> GOLD;
            case 8 -> CANDLE;
            default -> PURPLE;
        };
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0D * i / count;
            double x = origin.x + Math.cos(angle) * radius;
            double z = origin.z + Math.sin(angle) * radius;
            double y = origin.y + 0.08D;
            level.addParticle(dust, x, y, z, 0.0D, 0.03D, 0.0D);
            if (i % 3 == 0) {
                var spark = kind == 2 || kind == 4 || kind == 7 ? ParticleTypes.END_ROD
                        : kind == 6 ? ParticleTypes.CAMPFIRE_COSY_SMOKE
                        : kind == 8 ? ParticleTypes.FLAME
                        : kind == 5 ? ParticleTypes.CLOUD : ParticleTypes.PORTAL;
                level.addParticle(spark, x, y + 0.4D, z, 0.0D, 0.08D, 0.0D);
            }
        }
        if (kind == 2 || kind == 3) {
            level.addParticle(ParticleTypes.FLASH, origin.x, origin.y + 1.0D, origin.z, 0.0D, 0.0D, 0.0D);
        }
    }

    private static DustParticleOptions dust(float r, float g, float b, float size) {
        return new DustParticleOptions(new Vector3f(r, g, b), size);
    }
}
