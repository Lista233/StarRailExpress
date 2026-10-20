package org.agmas.noellesroles.game.modes.echo.anim;

import net.minecraft.resources.ResourceLocation;

/** 回声攻击特效用到的自定义粒子 id（服务端只发 id，形状由客户端画）。 */
public final class EchoHuntFxIds {
    private EchoHuntFxIds() { }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath("noellesroles", path);
    }

    /** 出击刀光：沿挥砍平面的一段弧形。 */
    public static final ResourceLocation SLASH = id("echo_hunt_slash");
    /** 命中血花：从命中点向外飞散的血珠与血雾。 */
    public static final ResourceLocation BLOOD = id("echo_hunt_blood");
    /** 扑击的地面冲击环。 */
    public static final ResourceLocation IMPACT = id("echo_hunt_impact");
    /** 擦刀时从刀身甩下的血线。 */
    public static final ResourceLocation WIPE = id("echo_hunt_wipe");
}