package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.client.gui.screen.ingame.RoleInventoryScreenExtension;
import io.wifi.starrailexpress.client.gui.screen.ingame.LimitedInventoryScreen;
import io.wifi.starrailexpress.client.gui.widget.SreButton;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.EchoRoles;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoInventoryPayload;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.UUID;

/** 每连接顺序请求；不预测扣物，超时只读重同步，不重发写操作。 */
public final class EchoBagClient {
    private static UUID epoch;
    private static long sequence,pending;
    private static int waiting;
    private static UUID openingPack;
    private EchoBagClient() { }
    public static EchoRoleData data() {
        var player=Minecraft.getInstance().player;
        return player==null?null:RoleData.getNullable(EchoRoleData.class,player);
    }
    public static void initialize() {
        EchoRulesClient.initialize();
        EchoSpecializationClient.initialize();
        for(EchoFaction faction:EchoFaction.values()) EchoRoles.get(faction).setInventoryScreenExtensionFactory(() -> new RoleInventoryScreenExtension() {
            @Override public void onInventoryScreenInitTail(LimitedInventoryScreen screen) {
                var button=new SreButton(Component.translatable("echo.ui.bag"),b -> Minecraft.getInstance().setScreen(new EchoInventoryScreen(screen)));
                button.setX(screen.width/2-65); button.setY(Math.max(4,screen.height/2-116)); button.setWidth(130); screen.addRoleWidget(button);
            }
        });
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var data=data();
            if(data==null || data.epoch()==null) { epoch=null; sequence=0; pending=0; openingPack=null; return; }
            if(!data.epoch().equals(epoch)) { epoch=data.epoch(); sequence=0; pending=0; openingPack=null; }
            if(pending!=0 && data.acknowledged()>=pending) { pending=0; waiting=0; }
            else if(pending!=0 && ++waiting>100) { pending=0; waiting=0; send(EchoInventoryPayload.Operation.SNAPSHOT,null,null,0); }
        });
    }
    public static boolean waiting() { var data=data(); return pending!=0 || data!=null && !data.rulesReady(); }
    public static boolean send(EchoInventoryPayload.Operation operation,UUID first,UUID second,int option) {
        return send(operation,first,second,option,net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO);
    }
    public static boolean send(EchoInventoryPayload.Operation operation,UUID first,UUID second,int option,net.minecraft.world.phys.Vec3 point,net.minecraft.world.phys.Vec3 secondPoint) {
        var data=data(); if(data==null || data.matchId()==null || data.epoch()==null || data.bag()==null) return false;
        if(!data.rulesReady() && operation!=EchoInventoryPayload.Operation.SNAPSHOT && operation!=EchoInventoryPayload.Operation.CANCEL_PACK && operation!=EchoInventoryPayload.Operation.TRADE_CANCEL) return false;
        if(pending!=0 && operation!=EchoInventoryPayload.Operation.SNAPSHOT && operation!=EchoInventoryPayload.Operation.CANCEL_PACK && operation!=EchoInventoryPayload.Operation.TRADE_CANCEL) return false;
        if(!data.epoch().equals(epoch)) { epoch=data.epoch(); sequence=0; pending=0; }
        long id=++sequence; pending=id; waiting=0;
        if(operation==EchoInventoryPayload.Operation.BEGIN_PACK) openingPack=first;
        var trade=data.trade();
        ClientPlayNetworking.send(new EchoInventoryPayload(1,data.matchId(),epoch,id,data.bag().revision(),operation,
                first==null?EchoInventoryPayload.NONE:first,second==null?EchoInventoryPayload.NONE:second,option,point,secondPoint,
                operation.trade() && trade!=null?trade.id():EchoInventoryPayload.NONE,operation.trade() && trade!=null?trade.version():0,java.util.List.of()));
        return true;
    }
    public static boolean tradeOffer(java.util.List<UUID> offer) {
        var data=data(); if(data==null || data.trade()==null || data.epoch()==null || data.bag()==null || waiting()) return false;
        if(!data.epoch().equals(epoch)) { epoch=data.epoch(); sequence=0; }
        pending=++sequence; waiting=0; var trade=data.trade();
        ClientPlayNetworking.send(new EchoInventoryPayload(1,data.matchId(),epoch,pending,data.bag().revision(),EchoInventoryPayload.Operation.TRADE_OFFER,
                EchoInventoryPayload.NONE,EchoInventoryPayload.NONE,0,net.minecraft.world.phys.Vec3.ZERO,net.minecraft.world.phys.Vec3.ZERO,trade.id(),trade.version(),offer));
        return true;
    }
    public static void close() {
        var data=data();
        if(data!=null && data.draft()!=null && data.draft().state().equals("CHANNELING")) send(EchoInventoryPayload.Operation.CANCEL_PACK,data.draft().offerId(),null,0);
        else if(waiting() && openingPack!=null) send(EchoInventoryPayload.Operation.CANCEL_PACK,openingPack,null,0);
        openingPack=null;
    }
}
