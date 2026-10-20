package org.agmas.noellesroles.game.modes.echo.combat;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.game.GameConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.agmas.noellesroles.game.modes.echo.EchoBootstrap;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.cards.EchoDraftService.PackQuality;
import org.agmas.noellesroles.game.modes.echo.content.EchoContent;
import org.agmas.noellesroles.game.modes.echo.objective.EchoDropService;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.*;

import java.util.*;
import java.util.function.Predicate;

/** 世界战斗适配：命中、冻结与最终死亡；机会数的唯一权威仍是成员的纯状态。 */
public final class EchoCombatService {
    private static final ResourceLocation IMMOBILIZED=ResourceLocation.fromNamespaceAndPath("noellesroles","echo_immobilized");
    private static final ResourceLocation HUNT_SPEED=ResourceLocation.fromNamespaceAndPath("noellesroles","echo_hunt_speed");
    /** 每个追捕者的受害者心跳节流：只按锁定值决定间隔，不额外发提醒文本。 */
    private final Map<UUID,Long> nextHeartbeat=new HashMap<>();
    private final EchoMatch match;
    private final ServerLevel level;
    private final Map<UUID,Vec3> anchors=new HashMap<>();
    private final Map<UUID,UUID> downedBy=new HashMap<>();
    private final Set<UUID> prone=new HashSet<>();
    public EchoCombatService(EchoMatch match,ServerLevel level) { this.match=match; this.level=level; }

    /** 起手蓄力：必须先回到 STALK 相（后摇中不能连点），并扣掉本次蓄力的起始 tick。 */
    public boolean beginCutter(ServerPlayer player) {
        if(!match.standing(player) || match.bags().busy(player.getUUID()) || !match.phase().acceptsActions() || SREGameTimeComponent.KEY.get(level).isTimeFrozen()
                || match.faction(player.getUUID())!=EchoFaction.INTERCEPTOR || player.getInventory().selected!=0
                || !player.getMainHandItem().is(EchoContent.CUTTER)) return false;
        long now=match.matchTick();
        var loop=match.hunt(player.getUUID());
        if(loop==null || !loop.canCharge(now)) return false;
        if(!match.state(player.getUUID()).beginCutter(now)) return false;
        loop.onCharge(now);
        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.charge(match,player);
        match.trades().interrupt(player.getUUID());
        match.objectives().cancelActor(player);
        publishHunt(player);
        return true;
    }
    /**
     * 松手结算：锁定满则本次出击升级为「扑击」（判定距离 +2 格、倒地时间 +10 秒），
     * 命中进入短后摇、挥空进入长后摇；扑击命中与狩猎本能期间免后摇。
     */
    public void releaseCutter(ServerPlayer player) {
        if(!match.standing(player) || !player.getMainHandItem().is(EchoContent.CUTTER) || player.getInventory().selected!=0
                || match.faction(player.getUUID())!=EchoFaction.INTERCEPTOR || !match.phase().acceptsActions()
                || SREGameTimeComponent.KEY.get(level).isTimeFrozen()) return;
        long now=match.matchTick();
        if(!match.state(player.getUUID()).releaseCutter(now)) return;
        var loop=match.hunt(player.getUUID());
        var strike=loop==null?new EchoHuntLoop.Strike(false,0,0):loop.beginLunge(now);
        match.cards().breakMask(player.getUUID());
        match.objectives().cancelActor(player);
        for(ServerPlayer nearby:match.onlinePlayers()) if(nearby.distanceToSqr(player)<=16*16)
            nearby.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,1,strike.empowered()?0.8F:1);
        double range=match.rules().combat().cutterRange()+strike.rangeBonus();
        // 刀光：从眼睛前方开始，沿视线方向铺开；参数只有 yaw/pitch/强度，不含实体位置。
        Vec3 look=player.getLookAngle();
        float yaw=(float)(Math.toDegrees(Math.atan2(-look.x,look.z)));
        float pitch=(float)Math.toDegrees(Math.asin(-Math.max(-1,Math.min(1,look.y))));
        float slashStrength=strike.empowered()?1.7F:1F;
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.SLASH,
                player.getEyePosition().add(look.scale(0.6)),0,yaw,pitch,slashStrength);
        var target=firstPlayer(player,range).filter(p -> match.faction(p.getUUID())!=EchoFaction.INTERCEPTOR);
        boolean connected=false;
        if(target.isPresent()) {
            ServerPlayer victim=target.get();
            connected=lethal(victim,player,GameConstants.DeathReasons.KNIFE,false,false)!=EchoCombatState.Hit.IGNORED;
            if(connected) {
                if(strike.bleedBonus()>0) match.state(victim.getUUID()).extendBleed(now,strike.bleedBonus());
                // 血花打在受害者胸口高度，而不是脚下：命中反馈要落在看得见的位置。
                io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.BLOOD,
                        victim.getEyePosition().add(0,-0.35,0),0,yaw,slashStrength);
                if(strike.empowered()) io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.IMPACT,
                        new Vec3(victim.getX(),victim.getY()+0.05,victim.getZ()),0,1.4F);
            }
        }
        if(loop!=null) {
            if(connected) { loop.onHit(now,strike.empowered()); org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.strike(match,player,strike.empowered()); }
            else { loop.onMiss(now); org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.miss(match,player); }
        }
        // 擦刀：命中后甩血横抹，挥空只做收刀；两者都在出击结束后接管 0.9 秒。
        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.wipe(match,player,connected);
        if(connected) io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.WIPE,
                player.getEyePosition().add(look.scale(0.9)),0,yaw);
        if(connected) {
            player.playNotifySound(SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,0.9F,strike.empowered()?0.7F:1.1F);
            // 擦刀音：布料摩擦 + 轻微金属，复用原版声音，不新增资源。
            player.playNotifySound(SoundEvents.ARMOR_EQUIP_LEATHER.value(),SoundSource.PLAYERS,0.55F,1.35F);
            for(ServerPlayer nearby:match.onlinePlayers()) if(nearby!=player && nearby.distanceToSqr(player)<=12*12)
                nearby.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,0.7F,1.25F);
            if(strike.empowered()) player.displayClientMessage(Component.translatable("echo.hunt.strike_empowered"),true);
        } else player.playNotifySound(SoundEvents.PLAYER_ATTACK_NODAMAGE,SoundSource.PLAYERS,0.8F,0.9F);
        publishHunt(player);
    }
    public boolean shove(ServerPlayer player) {
        if(!match.standing(player) || match.faction(player.getUUID())!=EchoFaction.CARRIER
                || !match.state(player.getUUID()).shoveReady(match.matchTick())) return false;
        var target=firstPlayer(player,match.rules().combat().shoveRange()).filter(p -> match.faction(p.getUUID())==EchoFaction.INTERCEPTOR);
        if(target.isEmpty()) return false;
        ServerPlayer wolf=target.get();
        if(!match.state(wolf.getUUID()).control(match.matchTick(),match.rules().combat().shoveControlTicks())) {
            player.displayClientMessage(Component.translatable("echo.combat.control_immune"),true); return false;
        }
        match.state(player.getUUID()).consumeShove(match.matchTick());
        match.trades().interrupt(player.getUUID()); match.trades().interrupt(wolf.getUUID());
        anchors.put(wolf.getUUID(),wolf.position()); wolf.stopUsingItem();
        match.objectives().cancelActor(player); match.objectives().cancelActor(wolf);
        project(player,true); project(wolf,true);
        return true;
    }

    /** 限制射线先撞方块/普通可点实体；倒地、旁观与模式展示不遮挡攻击。 */
    private Optional<ServerPlayer> firstPlayer(ServerPlayer source,double range) {
        Vec3 eye=source.getEyePosition(),end=eye.add(source.getLookAngle().scale(range));
        var block=level.clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source));
        double limit=block.getType()==HitResult.Type.MISS?range*range:eye.distanceToSqr(block.getLocation());
        Entity nearest=null; double distance=limit;
        for(Entity entity:level.getEntities(source,source.getBoundingBox().expandTowards(source.getLookAngle().scale(range)).inflate(0.3),e ->
                e.isPickable() && !e.isSpectator() && !e.getTags().contains(EchoDropService.PARCEL_TAG))) {
            if(entity instanceof ServerPlayer player && match.faction(player.getUUID())!=null && !match.life(player.getUUID()).standing()) continue;
            var box=entity.getBoundingBox().inflate(0.05);
            var hit=box.clip(eye,end);
            double current=box.contains(eye)?0:hit.map(eye::distanceToSqr).orElse(Double.POSITIVE_INFINITY);
            if(current<distance) { nearest=entity; distance=current; }
        }
        return nearest instanceof ServerPlayer player && match.present(player) && match.life(player.getUUID()).standing()?Optional.of(player):Optional.empty();
    }

    /** 返回本次命中的结算结果；IGNORED 表示目标处于保护/无敌窗口，调用方不得当成命中。 */
    public EchoCombatState.Hit lethal(ServerPlayer victim,ServerPlayer killer,ResourceLocation reason,boolean force,boolean environmental) {
        if(match.result()!=null || match.faction(victim.getUUID())==null || victim.level()!=level) return EchoCombatState.Hit.IGNORED;
        if(!force && (!match.phase().acceptsActions() || SREGameTimeComponent.KEY.get(level).isTimeFrozen())) return EchoCombatState.Hit.IGNORED;
        EchoCombatState state=match.state(victim.getUUID());
        EchoCombatState.Hit hit=state.hit(match.matchTick(),force,match.faction(victim.getUUID())==EchoFaction.INTERCEPTOR);
        if(hit==EchoCombatState.Hit.IGNORED) return hit;
        match.cards().breakMask(victim.getUUID());
        match.trades().interrupt(victim.getUUID());
        match.objectives().cancelActor(victim); match.bags().interrupt(victim); victim.stopUsingItem();
        if(hit==EchoCombatState.Hit.DOWNED) {
            Vec3 anchor=match.safeAnchor(victim,environmental);
            victim.teleportTo(level,anchor.x,anchor.y,anchor.z,victim.getYRot(),victim.getXRot());
            anchors.put(victim.getUUID(),anchor); prone.add(victim.getUUID());
            downedBy.put(victim.getUUID(),killer==null?null:killer.getUUID());
            dropCargo(victim,anchor); victim.setHealth(victim.getMaxHealth());
            if(killer!=null) match.objectives().grantReward(killer,PackQuality.RARE,"down:"+victim.getUUID());
            announce("echo.combat.downed",victim);
        } else if(hit==EchoCombatState.Hit.RESET_INTERCEPTOR) {
            Vec3 spawn=match.spawn(victim.getUUID()); anchors.put(victim.getUUID(),spawn);
            victim.teleportTo(level,spawn.x,spawn.y,spawn.z,victim.getYRot(),victim.getXRot());
            victim.setHealth(victim.getMaxHealth());
        } else finishElimination(victim,killer,reason);
        project(victim,true);
        return hit;
    }

    public boolean canRescue(ServerPlayer actor,UUID target) {
        return match.standing(actor) && match.faction(actor.getUUID())!=EchoFaction.INTERCEPTOR
                && !actor.getUUID().equals(target) && match.state(target)!=null && match.state(target).canBeRescued(match.matchTick());
    }
    public void rescue(ServerPlayer actor,UUID target) {
        ServerPlayer victim=level.getServer().getPlayerList().getPlayer(target);
        if(victim==null || victim.level()!=level || !canRescue(actor,target)) return;
        if(!match.state(target).rescue(match.matchTick())) return;
        releaseAnchor(victim); downedBy.remove(target);
        match.objectives().grantReward(actor,PackQuality.COMMON,"rescue:"+target);
        project(victim,true); announce("echo.combat.rescued",victim);
    }

    /** 暂停只执行限制，不推进流血、控制或冷却。 */
    public void restrict() {
        for(ServerPlayer player:match.onlinePlayers()) restrict(player,match.state(player.getUUID()));
        match.bags().restrictOpening();
    }
    private void restrict(ServerPlayer player,EchoCombatState state) {
        long now=match.matchTick();
        if(state.life()==EchoLifeState.DOWNED || state.controlled(now)) {
            Vec3 anchor=anchors.computeIfAbsent(player.getUUID(),id -> player.position());
            player.stopUsingItem(); player.setSprinting(false); player.setDeltaMovement(Vec3.ZERO); player.fallDistance=0;
            if(player.position().distanceToSqr(anchor)>0.001) player.teleportTo(level,anchor.x,anchor.y,anchor.z,player.getYRot(),player.getXRot());
            var speed=player.getAttribute(Attributes.MOVEMENT_SPEED);
            if(speed!=null && speed.getModifier(IMMOBILIZED)==null) speed.addTransientModifier(new AttributeModifier(IMMOBILIZED,-1,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
            if(state.life()==EchoLifeState.DOWNED) { player.setPose(Pose.SWIMMING); prone.add(player.getUUID()); }
        } else releaseAnchor(player);
    }
    public void tick() {
        long now=match.matchTick();
        for(UUID owner:match.participants()) if(match.connectedPlayer(owner)==null && match.life(owner)==EchoLifeState.DOWNED && now>=match.state(owner).bleedUntil()) match.exit(owner);
        for(ServerPlayer player:match.onlinePlayers()) {
            EchoCombatState state=match.state(player.getUUID());
            if(state.expireBleed(now)) {
                UUID killerId=downedBy.get(player.getUUID());
                ServerPlayer killer=killerId==null?null:level.getServer().getPlayerList().getPlayer(killerId);
                finishElimination(player,killer,GameConstants.DeathReasons.KNIFE);
            }
            restrict(player,state);
            if(now%5==0) enforceTool(player,state);
            project(player,false);
            huntTick(player,state,now);
            if(state.life()==EchoLifeState.DOWNED && now%10==0) player.displayClientMessage(
                    Component.translatable("echo.combat.bleeding",Math.max(0,(state.bleedUntil()-now+19)/20)),true);
        }
    }
    /**
     * 追捕者的每 tick 循环推进：锁定累积、相位到期、移速倍率、受害者心跳与本人投影。
     * 非追捕者、倒地或受控时把循环清零并撤掉速度修正，避免状态泄漏到下一局。
     */
    private void huntTick(ServerPlayer player,EchoCombatState state,long now) {
        var loop=match.hunt(player.getUUID());
        if(loop==null) return;
        boolean hunting=match.faction(player.getUUID())==EchoFaction.INTERCEPTOR && state.life().standing();
        if(!hunting) {
            if(loop.phase()!=EchoHuntLoop.Phase.STALK || loop.focus()!=0) { loop.clear(); publishHunt(player); }
            removeHuntSpeed(player); nextHeartbeat.remove(player.getUUID()); return;
        }
        // 蓄力被打断（被冲撞、被控制、切槽、松手未达最短蓄力）时把相位拉回 STALK。
        if(loop.phase()==EchoHuntLoop.Phase.CHARGE
                && (!player.isUsingItem() || !player.getUseItem().is(EchoContent.CUTTER))) loop.cancel(now);
        ServerPlayer loudest=null; double bestGain=0;
        for(ServerPlayer other:match.onlinePlayers()) {
            if(other==player || !match.life(other.getUUID()).standing()) continue;
            double distance=Math.sqrt(player.distanceToSqr(other));
            if(distance>EchoHuntLoop.FOCUS_RANGE) continue;
            if(!player.hasLineOfSight(other)) continue;
            var velocity=other.getDeltaMovement();
            double gain=EchoHuntLoop.gain(distance,velocity.horizontalDistanceSqr(),other.isSprinting());
            if(gain>bestGain) { bestGain=gain; loudest=other; }
        }
        loop.tick(now,bestGain);
        applyHuntSpeed(player,loop,now);
        if(loop.consumeLockAnnouncement(now)) player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,0.7F,1.6F);
        int focus=loop.focus();
        if(loudest!=null && focus>=50) {
            long due=nextHeartbeat.getOrDefault(player.getUUID(),0L);
            if(now>=due) {
                loudest.playNotifySound(SoundEvents.WARDEN_HEARTBEAT,SoundSource.PLAYERS,0.55F,1.0F);
                nextHeartbeat.put(player.getUUID(),now+EchoHuntLoop.heartbeatInterval(focus));
            }
        } else nextHeartbeat.remove(player.getUUID());
        publishHunt(player);
    }
    private void applyHuntSpeed(ServerPlayer player,EchoHuntLoop loop,long now) {
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED); if(speed==null) return;
        double factor=loop.speedFactor(now);
        var existing=speed.getModifier(HUNT_SPEED);
        if(Math.abs(factor-1.0D)<0.005D) { if(existing!=null) speed.removeModifier(HUNT_SPEED); return; }
        double amount=factor-1.0D;
        if(existing!=null && Math.abs(existing.amount()-amount)<0.002D) return;
        if(existing!=null) speed.removeModifier(HUNT_SPEED);
        speed.addTransientModifier(new AttributeModifier(HUNT_SPEED,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }
    private void removeHuntSpeed(ServerPlayer player) {
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED); if(speed!=null) speed.removeModifier(HUNT_SPEED);
    }
    private void publishHunt(ServerPlayer player) {
        var loop=match.hunt(player.getUUID()); if(loop==null) return;
        RoleData.getOptional(EchoRoleData.class,player).filter(d -> match.id().equals(d.matchId()))
                .ifPresent(d -> d.projectHunt(loop.phase().name(),loop.focus(),loop.phaseUntil(),loop.empowered(),loop.instinctUntil()));
    }
    /** 三格携带：0 阵营工具、1 卡牌终端、2 专长信物；其余槽位一律清空。 */
    private void enforceTool(ServerPlayer player,EchoCombatState state) {
        boolean wolf=match.faction(player.getUUID())==EchoFaction.INTERCEPTOR && state.life().standing();
        boolean standing=state.life().standing();
        boolean changed=false;
        var inventory=player.getInventory();
        for(int slot=0;slot<inventory.getContainerSize();slot++) {
            var stack=inventory.getItem(slot);
            boolean allowed=slot==0?(wolf && stack.is(EchoContent.CUTTER))
                    :slot==1?(standing && stack.is(EchoContent.CARD_PROXY))
                    :slot==2?(standing && stack.is(EchoContent.SKILL_TOKEN))
                    :false;
            if(!stack.isEmpty() && !allowed) { inventory.setItem(slot,ItemStack.EMPTY); changed=true; }
        }
        if(wolf && !inventory.getItem(0).is(EchoContent.CUTTER)) { inventory.setItem(0,new ItemStack(EchoContent.CUTTER)); changed=true; }
        if(standing && !inventory.getItem(1).is(EchoContent.CARD_PROXY)) { inventory.setItem(1,new ItemStack(EchoContent.CARD_PROXY)); changed=true; }
        if(standing && !inventory.getItem(2).is(EchoContent.SKILL_TOKEN)) { inventory.setItem(2,new ItemStack(EchoContent.SKILL_TOKEN)); changed=true; }
        if(inventory.selected>2) inventory.selected=2;
        if(changed) player.inventoryMenu.broadcastChanges();
    }
    public void exit(ServerPlayer player) { match.exit(player.getUUID()); }
    public void restore(ServerPlayer player) {
        releaseAnchor(player); restrict(player,match.state(player.getUUID())); enforceTool(player,match.state(player.getUUID())); project(player,true);
    }
    /** 只撤销模式实体限制和工具，不触碰成员生命或倒地截止。 */
    public void detach(ServerPlayer player) {
        releaseAnchor(player); player.stopUsingItem();
        for(int slot=0;slot<player.getInventory().getContainerSize();slot++) if(EchoContent.tool(player.getInventory().getItem(slot))) player.getInventory().setItem(slot,ItemStack.EMPTY);
        player.inventoryMenu.broadcastChanges();
    }
    private void finishElimination(ServerPlayer player,ServerPlayer killer,ResourceLocation reason) {
        match.trades().interrupt(player.getUUID());
        match.bags().depart(player);
        match.contracts().depart(player.getUUID());
        match.cards().depart(player);
        dropCargo(player,match.safeAnchor(player,true)); releaseAnchor(player); downedBy.remove(player.getUUID());
        enforceTool(player,match.state(player.getUUID()));
        var mode=io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(level).getGameMode();
        if(mode instanceof EchoGameMode echo) echo.finishElimination(player,killer,reason);
        announce("echo.combat.eliminated",player);
    }
    private void dropCargo(ServerPlayer player,Vec3 anchor) {
        while(!match.cargo(player.getUUID()).items().isEmpty()) if(!match.objectives().dropCarried(player,false,anchor)) throw new IllegalStateException("战斗掉货失败");
        if(match.bags().inventory(player.getUUID()).items().stream().anyMatch(org.agmas.noellesroles.game.modes.echo.inventory.EchoResourceInstance::key)
                && !match.objectives().dropCarried(player,true,anchor)) throw new IllegalStateException("战斗掉钥匙失败");
    }
    private void releaseAnchor(ServerPlayer player) {
        anchors.remove(player.getUUID());
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED); if(speed!=null) speed.removeModifier(IMMOBILIZED);
        if(prone.remove(player.getUUID())) player.setPose(Pose.STANDING);
    }
    private void project(ServerPlayer player,boolean force) {
        RoleData.getOptional(EchoRoleData.class,player).filter(d -> match.id().equals(d.matchId()))
                .ifPresent(data -> data.projectCombat(match.state(player.getUUID()).snapshot(),match.matchTick(),force));
    }
    private void announce(String key,ServerPlayer player) {
        Component message=Component.translatable(key,player.getGameProfile().getName());
        match.onlinePlayers().forEach(p -> p.sendSystemMessage(message)); SRE.REPLAY_MANAGER.recordCustomEvent(message);
    }
    public void cleanup() {
        for(ServerPlayer player:match.onlinePlayers()) { releaseAnchor(player); player.stopUsingItem(); }
        for(ServerPlayer player:match.onlinePlayers()) {
            for(int slot=0;slot<player.getInventory().getContainerSize();slot++)
                if(EchoContent.tool(player.getInventory().getItem(slot))) player.getInventory().setItem(slot,ItemStack.EMPTY);
            player.inventoryMenu.broadcastChanges();
        }
        anchors.clear(); downedBy.clear(); prone.clear();
        nextHeartbeat.clear();
        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.clearAll();
    }
}
