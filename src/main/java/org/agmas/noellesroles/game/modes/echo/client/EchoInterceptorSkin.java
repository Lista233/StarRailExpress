package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.api.data.RoleData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;
import org.agmas.noellesroles.game.modes.echo.state.EchoRoleData;

/**
 * 追捕者的「狂暴形态」外观：固定使用疯魔（psycho）默认皮肤，并按 1.5 倍放大。
 *
 * <p>只影响表现，两侧都从同一份 {@link EchoRoleData} 读阵营，不做额外网络同步：
 * <ul>
 * <li>皮肤：{@code wathe:textures/entity/psycho(_thin).png}——即原版疯魔形态的默认贴图；</li>
 * <li>体型：渲染时在 {@code PlayerRenderer.scale} 之后乘 1.5（模型、手臂、手持物一起放大）；</li>
 * <li>碰撞箱：**完全不变**。{@code Entity#getDimensions} / {@code Pose} 一律不动，
 * 因此判定、射线距离、挤压与服务器位置全部保持原值——放大只是「看起来更大」，
 * 不会让追捕者更难打中，也不会改变追击距离。</li>
 * </ul>
 *
 * <p>皮肤替换走 {@code PlayerEntityRendererMixin} 已有的入口：那里优先读取职业的
 * {@code getPsychoSkin}，而回声追捕者的职业并不处于疯魔状态，所以这里提供独立的
 * 判定函数，由同一个 mixin 在「普通皮肤」之前短路。
 */
public final class EchoInterceptorSkin {
    private EchoInterceptorSkin() { }

    /** 狂暴形态的视觉放大倍率（只影响渲染）。 */
    public static final float SCALE = 1.5F;

    /** 原版疯魔默认皮肤（与 {@code SRERole.getPsychoSkin} 的默认值一致）。 */
    private static ResourceLocation psychoTexture(boolean slim) {
        return ResourceLocation.fromNamespaceAndPath("wathe",
                "textures/entity/psycho" + (slim ? "_thin" : "") + ".png");
    }

    /** 该玩家是否应按追捕者狂暴形态渲染：本局、截流者、且仍在局内。 */
    public static boolean applies(AbstractClientPlayer player) {
        if (player == null) return false;
        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return false;
        if (!(io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(client.level).getGameMode()
                instanceof EchoGameMode)) return false;
        EchoRoleData data = RoleData.getNullable(EchoRoleData.class, player);
        return data != null && data.matchId() != null && data.faction() == EchoFaction.INTERCEPTOR;
    }

    /** 狂暴形态贴图；不适用时返回 {@code null}，调用方继续走既有的皮肤逻辑。 */
    public static ResourceLocation texture(AbstractClientPlayer player) {
        if (!applies(player)) return null;
        boolean slim = player.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM;
        return psychoTexture(slim);
    }

    /**
     * 本地玩家的第一人称手臂贴图：与第三人称保持同一张，避免「身子是怪物、手臂还是原皮肤」。
     * 不适用时返回 {@code null}。
     */
    public static ResourceLocation firstPersonTexture() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null) return null;
        return texture(client.player);
    }
}