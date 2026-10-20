package org.agmas.noellesroles.game.modes.echo.client;

import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.*;
import org.agmas.noellesroles.client.TaskBlockOverlayRenderer;
import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.agmas.noellesroles.game.modes.echo.content.EchoCardProxyItem;
import org.agmas.noellesroles.game.modes.echo.inventory.EchoInventoryPayload;
import java.util.*;

/** 代理仅采集目标意图；线框、方向与到期均读取本人的服务端授权。 */
public final class EchoCardClient {
    private static Vec3 firstPoint;
    private static UUID firstInstance;
    private static final MultiBufferSource.BufferSource LINES=MultiBufferSource.immediate(new ByteBufferBuilder(4096));
    private EchoCardClient() { }
    public static boolean selectingSecond() { return firstPoint!=null; }
    public static void initialize() {
        EchoCardProxyItem.clientUse=EchoCardClient::use;
        EchoSceneClient.initialize();
        EchoMapPreviewClient.initialize();
        EchoHud.initialize();
        EchoSoundClient.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            var data=EchoBagClient.data();
            if(data==null || data.selectedCard()==null || !data.selectedCard().equals(firstInstance) || client.screen!=null) { firstPoint=null; firstInstance=null; }
        });
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            var client=Minecraft.getInstance(); var data=EchoBagClient.data();
            if(client.level==null || client.player==null || client.player.isSpectator() || data==null || !data.life().standing() || context.matrixStack()==null) return;
            var pose=context.matrixStack(); Vec3 camera=context.camera().getPosition();
            pose.pushPose(); pose.translate(-camera.x,-camera.y,-camera.z);
            var buffer=LINES.getBuffer(TaskBlockOverlayRenderer.ALWAYS_VISIBLE_THICK_LINES);
            for(var signal:data.signals()) if(signal.target()!=null && !Set.of("mask","ally").contains(signal.kind()) && data.projectedTick()<signal.until()) {
                Entity target=client.level.getPlayerByUUID(signal.target());
                if(target!=null && !target.isSpectator() && target.isAlive()) LevelRenderer.renderLineBox(pose,buffer,target.getBoundingBox().inflate(0.06),1F,0.35F,0.22F,0.9F);
            }
            if(firstPoint!=null) LevelRenderer.renderLineBox(pose,buffer,new AABB(firstPoint.add(-0.2,0,-0.2),firstPoint.add(0.2,0.4,0.2)),1F,0.8F,0.2F,0.9F);
            pose.popPose(); LINES.endBatch();
        });
    }
    private static void use() {
        var client=Minecraft.getInstance(); var data=EchoBagClient.data();
        if(client.player==null || client.screen!=null || data==null || data.selectedCard()==null || EchoBagClient.waiting()) return;
        var instance=data.items().stream().filter(i -> i.instanceId().equals(data.selectedCard())).findFirst().orElse(null);
        if(instance==null) return;
        var card=data.rules().card(instance.definitionId()); double range=card.params().getOrDefault("range",12D);
        var hit=client.player.pick(range,0,false); Vec3 point=hit.getLocation(); UUID target=null;
        if(card.target()==EchoCardDefinition.Target.PLAYER) {
            Vec3 eye=client.player.getEyePosition(),end=eye.add(client.player.getLookAngle().scale(range));
            double nearest=hit.getType()==HitResult.Type.MISS?range*range:eye.distanceToSqr(hit.getLocation());
            for(Player p:client.level.players()) if(p!=client.player && !p.isSpectator()) {
                var intersection=p.getBoundingBox().inflate(0.05).clip(eye,end);
                if(intersection.isPresent() && eye.distanceToSqr(intersection.get())<nearest) { nearest=eye.distanceToSqr(intersection.get()); target=p.getUUID(); }
            }
        } else if(hit instanceof BlockHitResult block && hit.getType()==HitResult.Type.BLOCK) {
            point=card.target()==EchoCardDefinition.Target.BEACON || card.target()==EchoCardDefinition.Target.CORE_GATE?Vec3.atCenterOf(block.getBlockPos()):hit.getLocation();
        } else return;
        if(card.target()==EchoCardDefinition.Target.GROUND_PAIR && firstPoint==null) {
            firstPoint=point; firstInstance=instance.instanceId(); return;
        }
        Vec3 previous=firstPoint; firstPoint=null; firstInstance=null;
        EchoBagClient.send(EchoInventoryPayload.Operation.USE_CARD,instance.instanceId(),target,card.shortId().equals("forged_signal") && client.player.isShiftKeyDown()?1:0,previous==null?point:previous,previous==null?Vec3.ZERO:point);
    }
}
