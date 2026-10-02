package org.agmas.noellesroles.role.qust.roles.dragon_girl;

import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.role.qust.QUSTRoles;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

/**
 * 龙娘客户端皮肤状态。
 * <p>
 * 当龙娘技能激活（skinActive=true）时，将玩家皮肤替换为龙娘皮肤。
 * 对所有玩家可见（组件通过 shouldSyncWith 同步给所有玩家）。
 */
public class DragonGirlClientState {

    /** 龙娘皮肤贴图路径 */
    public static final ResourceLocation DRAGON_GIRL_SKIN =
            QUSTRoles.id("textures/entity/dragon_girl.png");

    public static void register() {
        OnGettingPlayerSkin.EVENT.register((player, originalSkin) -> {
            // 获取该玩家的龙娘组件
            DragonGirlPlayerComponent comp = QUSTComponentKeys.Keys.DRAGON_GIRL.get(player);
            if (comp == null || !comp.skinActive) {
                return PlayerSkinResult.SKIP;
            }

            // 龙娘皮肤使用 slim 模型，完整替换皮肤对象
            return PlayerSkinResult.playerSkin(DRAGON_GIRL_SKIN, PlayerSkin.Model.SLIM);
        });
    }
}
