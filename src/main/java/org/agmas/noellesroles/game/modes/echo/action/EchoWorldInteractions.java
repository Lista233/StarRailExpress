package org.agmas.noellesroles.game.modes.echo.action;

import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import org.agmas.noellesroles.game.modes.echo.EchoBootstrap;
import org.agmas.noellesroles.game.modes.echo.EchoRoles;
import org.agmas.noellesroles.game.modes.echo.objective.EchoDropService;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions;
import org.agmas.noellesroles.game.modes.echo.state.EchoFaction;

/** 常量注册的世界输入入口，客户端只拦默认交互，所有业务在服务端校验。 */
public final class EchoWorldInteractions {
    private EchoWorldInteractions() { }
    private static boolean echo(Level level) {
        var game=SREGameWorldComponent.KEY.get(level);
        return game.isRunning() && game.getGameMode().identifier.equals(EchoBootstrap.ID);
    }
    public static void register() {
        UseBlockCallback.EVENT.register((player,level,hand,hit) -> {
            if(!echo(level)) return InteractionResult.PASS;
            if(hand==InteractionHand.MAIN_HAND && org.agmas.noellesroles.game.modes.echo.content.EchoContent.tool(player.getMainHandItem()))
                return player.getMainHandItem().use(level,player,hand).getResult();
            if(hand!=InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return InteractionResult.FAIL;
            if(level.isClientSide) return InteractionResult.SUCCESS;
            if(player instanceof ServerPlayer server && level instanceof ServerLevel world) {
                var match=EchoSessions.get(world); if(match!=null) match.useBlock(server,hit.getBlockPos().immutable());
            }
            return InteractionResult.CONSUME;
        });
        UseEntityCallback.EVENT.register((player,level,hand,entity,hit) -> {
            if(!echo(level)) return entity.getTags().contains(EchoDropService.PARCEL_TAG)?InteractionResult.FAIL:InteractionResult.PASS;
            if(hand==InteractionHand.MAIN_HAND && org.agmas.noellesroles.game.modes.echo.content.EchoContent.tool(player.getMainHandItem()))
                return player.getMainHandItem().use(level,player,hand).getResult();
            if(hand!=InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty()) return InteractionResult.FAIL;
            if(level.isClientSide) return InteractionResult.SUCCESS;
            if(player instanceof ServerPlayer server && level instanceof ServerLevel world) {
                var match=EchoSessions.get(world);
                if(match!=null) match.useEntity(server,entity.getUUID());
            }
            return InteractionResult.CONSUME;
        });
        AttackEntityCallback.EVENT.register((player,level,hand,entity,hit) -> {
            if(echo(level) && player instanceof ServerPlayer server) {
                var match=EchoSessions.get(server.serverLevel()); if(match!=null) match.trades().interrupt(server.getUUID());
            }
            return echo(level) || entity.getTags().contains(EchoDropService.PARCEL_TAG)?InteractionResult.FAIL:InteractionResult.PASS;
        });
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player,level,hand) -> {
            if(!echo(level)) return net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand));
            return org.agmas.noellesroles.game.modes.echo.content.EchoContent.tool(player.getItemInHand(hand))
                    ?net.minecraft.world.InteractionResultHolder.pass(player.getItemInHand(hand))
                    :net.minecraft.world.InteractionResultHolder.fail(player.getItemInHand(hand));
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity,source,amount) -> {
            if(!(entity instanceof ServerPlayer player) || !echo(player.level())) return true;
            if(io.wifi.starrailexpress.game.GameUtils.isForcedDeathInProgress()) return true;
            var match=EchoSessions.get(player.serverLevel());
            if(match==null || !match.phase().acceptsActions() || !match.standing(player)
                    || io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(player.level()).isTimeFrozen()) return false;
            // 任何原版玩家伤害（含投射物/爆炸）均不得绕过模式的机会数与服务器射线。
            return !(source.getEntity() instanceof net.minecraft.world.entity.player.Player);
        });
        net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.AFTER_DAMAGE.register((entity,source,baseDamage,damageTaken,blocked) -> {
            if(!(entity instanceof ServerPlayer player) || blocked || damageTaken<=0 || !echo(player.level())) return;
            var match=EchoSessions.get(player.serverLevel());
            if(match!=null && match.present(player)) {
                match.trades().interrupt(player.getUUID()); match.cards().breakMask(player.getUUID());
            }
        });
        ServerEntityEvents.ENTITY_LOAD.register((entity,world) -> {
            if(!entity.getTags().contains(EchoDropService.PARCEL_TAG) || EchoDropService.spawning(entity.getUUID())) return;
            var match=EchoSessions.get(world);
            if(match==null || match.result()!=null || !match.objectives().ownsDisplay(entity.getUUID())) entity.discard();
        });
        ServerPlayNetworking.registerGlobalReceiver(EchoCancelActionPayload.ID,(payload,context) -> context.server().execute(() -> {
            var match=EchoSessions.get(context.player().serverLevel());
            if(match!=null && match.result()==null && match.id().equals(payload.matchId())) match.cancel(context.player(),payload.actionId());
        }));
        ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.game.modes.echo.inventory.EchoInventoryPayload.ID,(payload,context) -> context.server().execute(() -> {
            var match=EchoSessions.get(context.player().serverLevel());
            if(match!=null && match.result()==null) match.inventoryRequest(context.player(),payload);
        }));
        ServerPlayNetworking.registerGlobalReceiver(org.agmas.noellesroles.game.modes.echo.specialization.EchoSpecializationSelectPayload.ID,(payload,context) -> context.server().execute(() -> {
            var match=EchoSessions.get(context.player().serverLevel());
            if(match!=null && match.result()==null && match.id().equals(payload.match())) match.selectSpecialization(context.player(),payload.option());
        }));
        RoleSkill.register(EchoRoles.get(EchoFaction.INTERCEPTOR),RoleSkill.skill(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("noellesroles","echo_overload"),"echo.skill.overload",context -> {
                    var match=EchoSessions.get(context.player().serverLevel());
                    return match!=null && match.overload(context.player());
                }).cooldownTicks(0).noAnnouncement().showOnHud(false).build());
        RoleSkill.register(EchoRoles.get(EchoFaction.CARRIER),RoleSkill.skill(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("noellesroles","echo_shove"),"echo.skill.shove",context -> {
                    var match=EchoSessions.get(context.player().serverLevel());
                    return match!=null && match.shove(context.player());
                }).cooldownTicks(0).noAnnouncement().showOnHud(false).build());
        RoleSkill.register(EchoRoles.get(EchoFaction.BROKER),RoleSkill.skill(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("noellesroles","echo_trade"),"echo.skill.trade",context -> {
                    var match=EchoSessions.get(context.player().serverLevel());
                    return match!=null && match.quickTrade(context.player());
                }).cooldownTicks(0).noAnnouncement().showOnHud(false).build());
    }
}
