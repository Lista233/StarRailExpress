package org.agmas.noellesroles.client;

import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin;
import io.wifi.starrailexpress.event.OnGettingPlayerSkin.PlayerSkinResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.resources.PlayerSkin;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.DreadlordRole;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.DreadlordRoleData;

/**
 * 恐惧魔王的细手皮只在狂暴时露出。皮肤由职业的 getPsychoSkin 给出，这里不再抢。
 */
@Environment(EnvType.CLIENT)
public final class DreadlordSkinHandler {
    private static final PlayerSkin SKIN = new PlayerSkin(
            Noellesroles.id("textures/entity/player/dreadlord.png"),
            null, null, null, PlayerSkin.Model.SLIM, false);

    private DreadlordSkinHandler() {
    }

    public static void register() {
        OnGettingPlayerSkin.EVENT.register((player, originalSkin) -> {
            if (player == null || player.level() == null) {
                return PlayerSkinResult.SKIP;
            }
            SREGameWorldComponent game = SREGameWorldComponent.KEY.get(player.level());
            if (game == null || !game.isRole(player, ModRoles.DREADLORD)) {
                return PlayerSkinResult.SKIP;
            }
            DreadlordRoleData data = DreadlordRole.dataOf(player);
            if (data == null || !data.isKilling() || data.isDark()) {
                return PlayerSkinResult.SKIP;
            }
            return PlayerSkinResult.playerSkin(SKIN);
        });
    }
}
