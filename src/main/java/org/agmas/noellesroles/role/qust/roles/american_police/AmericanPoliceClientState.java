package org.agmas.noellesroles.role.qust.roles.american_police;

import io.wifi.starrailexpress.client.SREClient;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.PlayerSkin;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;

/**
 * 美国警察客户端皮肤处理器。
 * <p>
 * 被美国警察技能标记的玩家，皮肤变为 black_man（slim/Alex 模型）。
 */
@Environment(EnvType.CLIENT)
public class AmericanPoliceClientState {

    public static void register() {
        OnGettingPlayerSkin.EVENT.register((player, originalSkin) -> {
            if (player == null || player.level() == null)
                return null;
            if (SREClient.gameComponent == null)
                return null;

            var comp = QUSTComponentKeys.Keys.AMERICAN_POLICE.maybeGet(player).orElse(null);
            if (comp != null && comp.isMarked()) {
                return OnGettingPlayerSkin.PlayerSkinResult.playerSkin(
                        AmericanPolicePlayerComponent.BLACK_MAN_TEXTURE,
                        PlayerSkin.Model.SLIM);
            }
            return null;
        });
    }
}
