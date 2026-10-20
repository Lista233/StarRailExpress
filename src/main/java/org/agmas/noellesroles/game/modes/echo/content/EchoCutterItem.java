package org.agmas.noellesroles.game.modes.echo.content;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.content.item.KnifeItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions;
import org.agmas.noellesroles.game.modes.echo.state.EchoRoleData;

/** 使用原版蓄力/松手传输，始终消费释放回调，绝不发默认KnifeStab包。 */
public final class EchoCutterItem extends KnifeItem {
    public EchoCutterItem(Properties properties) { super(properties); }
    @Override public int getMinKnifeChargeTicks(ItemStack stack,LivingEntity user) {
        var data=user instanceof Player player?RoleData.getNullable(EchoRoleData.class,player):null;
        return data==null?12:data.cutterChargeTicks();
    }
    @Override public int getUseDuration(ItemStack stack,LivingEntity user) { return 72000; }
    @Override public boolean canStartKnifeCharge(Level level,Player player,InteractionHand hand,ItemStack stack) {
        if(hand!=InteractionHand.MAIN_HAND) return false;
        if(level.isClientSide) {
            var data=RoleData.getNullable(EchoRoleData.class,player);
            return data!=null && data.phase().acceptsActions() && data.life().standing() && !data.controlled();
        }
        if(!(player instanceof ServerPlayer server)) return false;
        var match=EchoSessions.get(server.serverLevel());
        return match!=null && match.combat().beginCutter(server);
    }
    @Override public boolean onKnifeChargeReleased(ItemStack stack,Level level,Player player,int usedTicks) {
        if(!level.isClientSide && player instanceof ServerPlayer server) {
            var match=EchoSessions.get(server.serverLevel());
            if(match!=null) match.releaseCutter(server);
        }
        return true;
    }
}
