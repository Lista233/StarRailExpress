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
import org.agmas.noellesroles.game.modes.echo.hunter.EchoHunterClass;
import org.agmas.noellesroles.game.modes.echo.hunter.EchoRendCombo;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoMatch;
import org.agmas.noellesroles.game.modes.echo.state.*;

import java.util.*;
import java.util.function.Predicate;

/** 世界战斗适配：命中、冻结与最终死亡；机会数的唯一权威仍是成员的纯状态。 */
public final class EchoCombatService {
    private static final ResourceLocation IMMOBILIZED=ResourceLocation.fromNamespaceAndPath("noellesroles","echo_immobilized");
    private static final ResourceLocation HUNT_SPEED=ResourceLocation.fromNamespaceAndPath("noellesroles","echo_hunt_speed");
    private static final ResourceLocation WEB_SLOW=ResourceLocation.fromNamespaceAndPath("noellesroles","echo_web_slow");
    /** 织网贴脸横甩的半径（格）：近距离不做抛物线，避免「明明贴脸却因为弹道而失手」。 */
    private static final double WEB_SWEEP_RANGE=2.5D;
    /** 横甩扇形的半角余弦（60°，即 120° 扇形）。 */
    private static final double WEB_SWEEP_COS=0.5D;
    /** 裂爪突进距离（格）与撞墙前留出的余量。 */
    private static final double REND_DASH_RANGE=4.0D, REND_DASH_PADDING=0.4D;
    /** 每个追捕者的受害者心跳节流：只按锁定值决定间隔，不额外发提醒文本。 */
    private final Map<UUID,Long> nextHeartbeat=new HashMap<>();
    private final EchoMatch match;
    private final ServerLevel level;
    private final Map<UUID,Vec3> anchors=new HashMap<>();
    private final Map<UUID,UUID> downedBy=new HashMap<>();
    private final Set<UUID> prone=new HashSet<>();
    /** 同一追猎者最多同时保留的黏网块数；超出时先丢最早的一块。 */
    private static final int MAX_FIELDS=4;
    /** 织网被动「丝线感知」用的静止追踪：上一 tick 位置与连续静止 tick 数。 */
    private final Map<UUID,Vec3> lastPositions=new HashMap<>();
    private final Map<UUID,Integer> stillTicks=new HashMap<>();
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
     *
     * <p>职业分支只改「怎么打」：裂爪走三段连击，织网走蛛丝弹；SABOTEUR 与其它情况
     * 保持原版一刀，判定距离、后摇与表现都与加入职业之前完全一致。
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
        var hunter=match.hunterState(player.getUUID());
        var hunterClass=match.hunterClass(player.getUUID());
        Vec3 look=player.getLookAngle();
        float yaw=(float)(Math.toDegrees(Math.atan2(-look.x,look.z)));
        float pitch=(float)Math.toDegrees(Math.asin(-Math.max(-1,Math.min(1,look.y))));
        // 连段参数必须在结算之前取：命中会推进段数。
        var swing=hunterClass==EchoHunterClass.REND && hunter!=null?hunter.combo().swing(now):null;
        boolean connected, deferred=false;
        if(hunterClass==EchoHunterClass.WEAVER) {
            var thrown=weaverRelease(player,now,hunter,yaw,pitch);
            connected=thrown.hit(); deferred=thrown.deferred();
        } else connected=meleeRelease(player,now,strike,swing,yaw,pitch);
        if(hunter!=null && hunterClass==EchoHunterClass.REND) {
            if(connected) hunter.combo().onHit(now,swing); else hunter.combo().onMiss(now);
        }
        if(loop!=null) {
            if(deferred) loop.onHit(now,true,EchoHuntLoop.RECOVER_HIT_TICKS);
            else if(connected) loop.onHit(now,strike.empowered(),swing==null?EchoHuntLoop.RECOVER_HIT_TICKS:swing.recoverTicks());
            else loop.onMiss(now);
            if(connected) org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.strike(match,player,strike.empowered());
            else if(!deferred) org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.miss(match,player);
        }
        // 擦刀：命中后甩血横抹，挥空只做收刀；织网没有刀可擦，改由蛛丝弹的落点反馈承担。
        if(hunterClass!=EchoHunterClass.WEAVER) {
            org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.wipe(match,player,connected);
            if(connected) io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                    org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.WIPE,
                    player.getEyePosition().add(look.scale(0.9)),0,yaw);
        }
        if(connected) {
            player.playNotifySound(SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,0.9F,strike.empowered()?0.7F:1.1F);
            // 擦刀音：布料摩擦 + 轻微金属，复用原版声音，不新增资源。
            player.playNotifySound(SoundEvents.ARMOR_EQUIP_LEATHER.value(),SoundSource.PLAYERS,0.55F,1.35F);
            for(ServerPlayer nearby:match.onlinePlayers()) if(nearby!=player && nearby.distanceToSqr(player)<=12*12)
                nearby.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,0.7F,1.25F);
            if(strike.empowered()) player.displayClientMessage(Component.translatable("echo.hunt.strike_empowered"),true);
        } else if(!deferred) player.playNotifySound(SoundEvents.PLAYER_ATTACK_NODAMAGE,SoundSource.PLAYERS,0.8F,0.9F);
        publishHunt(player);
    }

    // ───────────────────────── 追猎者职业：裂爪 / 织网 ─────────────────────────

    /** 一次织网松手的结果：{@code hit} 表示当场命中，{@code deferred} 表示弹道已出手、结果稍后结算。 */
    private record ThrowResult(boolean hit, boolean deferred) { }

    /**
     * 裂爪近战结算：与原来的一刀完全一致，额外把连段的判定距离与出血层数叠加上去。
     *
     * @param swing 连段参数；{@code null} 表示不参与连段（SABOTEUR / 未分配职业）
     */
    private boolean meleeRelease(ServerPlayer player,long now,EchoHuntLoop.Strike strike,
                                 EchoRendCombo.Swing swing,float yaw,float pitch) {
        double range=match.rules().combat().cutterRange()+strike.rangeBonus()+(swing==null?0D:swing.reachBonus());
        float slashStrength=strike.empowered()?1.7F:1F;
        // 刀光：从眼睛前方开始，沿视线方向铺开；参数只有 yaw/pitch/强度，不含实体位置。
        Vec3 look=player.getLookAngle();
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.SLASH,
                player.getEyePosition().add(look.scale(0.6)),0,yaw,pitch,slashStrength);
        // 第二、三段额外补一层爪痕，让「连到了第几下」在画面上一眼可辨。
        if(swing!=null && swing.stage()>1) io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.REND_CLAW,
                player.getEyePosition().add(look.scale(0.5)),0,yaw,pitch,swing.stage());
        var target=firstPlayer(player,range).filter(p -> match.faction(p.getUUID())!=EchoFaction.INTERCEPTOR);
        if(target.isEmpty()) return false;
        ServerPlayer victim=target.get();
        boolean connected=lethal(victim,player,GameConstants.DeathReasons.KNIFE,false,false)!=EchoCombatState.Hit.IGNORED;
        if(!connected) return false;
        int bleed=strike.bleedBonus()+(swing==null?0:swing.bleedTicks());
        if(bleed>0) match.state(victim.getUUID()).extendBleed(now,bleed);
        // 血花打在受害者胸口高度，而不是脚下：命中反馈要落在看得见的位置。
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.BLOOD,
                victim.getEyePosition().add(0,-0.35,0),0,yaw,slashStrength);
        if(strike.empowered()) io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.IMPACT,
                new Vec3(victim.getX(),victim.getY()+0.05,victim.getZ()),0,1.4F);
        return true;
    }

    /**
     * 织网松手：贴脸改横甩一片黏网，远距抛出蛛丝弹。
     *
     * <p>与裂爪的关键差别是「脱靶也有收益」——弹道落地会留下一块黏网，
     * 所以织网的惩罚是位移压力而不是空白后摇。
     */
    private ThrowResult weaverRelease(ServerPlayer player,long now,
                                      org.agmas.noellesroles.game.modes.echo.hunter.EchoHunterState hunter,
                                      float yaw,float pitch) {
        Vec3 look=player.getLookAngle();
        if(firstPlayer(player,WEB_SWEEP_RANGE).filter(p -> match.faction(p.getUUID())!=EchoFaction.INTERCEPTOR).isPresent()) {
            boolean hit=false;
            for(ServerPlayer victim:match.onlinePlayers()) {
                if(victim==player || match.faction(victim.getUUID())==EchoFaction.INTERCEPTOR) continue;
                if(!match.life(victim.getUUID()).standing()) continue;
                Vec3 to=victim.position().subtract(player.position());
                double distance=to.length();
                if(distance>WEB_SWEEP_RANGE+0.6D) continue;
                if(distance>1.0E-3D && to.normalize().dot(look)<WEB_SWEEP_COS) continue;
                if(lethal(victim,player,GameConstants.DeathReasons.KNIFE,false,false)==EchoCombatState.Hit.IGNORED) continue;
                hit=true;
                io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.WEB_SPLAT,
                        victim.getEyePosition().add(0,-0.4,0),0,0F,0F);
            }
            splat(player.getUUID(),new Vec3(player.getX(),player.getY()+0.1D,player.getZ()),now);
            io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                    org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.WEB_CAST,
                    player.getEyePosition().add(look.scale(0.5)),0,yaw,pitch);
            return new ThrowResult(hit,false);
        }
        if(hunter==null || !hunter.canThrow()) return new ThrowResult(false,false);
        Vec3 eye=player.getEyePosition();
        hunter.addBolt(org.agmas.noellesroles.game.modes.echo.hunter.EchoWebBolt.aim(player.getUUID(),
                eye.x,eye.y,eye.z,look.x,look.y,look.z));
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.WEB_CAST,
                eye.add(look.scale(0.5)),0,yaw,pitch);
        player.playNotifySound(SoundEvents.ARROW_SHOOT,SoundSource.PLAYERS,0.7F,1.45F);
        return new ThrowResult(false,true);
    }

    /** 每 tick 推进所有在飞的蛛丝弹、清掉过期场地，并重算黏网减速。 */
    private void tickHunterProjectiles(long now) {
        for(UUID owner:match.participants()) {
            var hunter=match.hunterState(owner);
            if(hunter==null) continue;
            if(!hunter.bolts().isEmpty()) {
                var iterator=hunter.bolts().iterator();
                while(iterator.hasNext()) {
                    var bolt=iterator.next();
                    resolveBolt(owner,bolt,now);
                    if(bolt.finished() || bolt.expired()) iterator.remove();
                }
            }
            hunter.fields().removeIf(field -> field.expired(now));
        }
        applyWebSlow(now);
    }

    /** 逐子步推进一颗弹：先判人再判墙，避免擦着墙角的命中被方块吃掉。 */
    private void resolveBolt(UUID owner,org.agmas.noellesroles.game.modes.echo.hunter.EchoWebBolt bolt,long now) {
        for(int step=0;step<org.agmas.noellesroles.game.modes.echo.hunter.EchoWebBolt.SUBSTEPS && !bolt.finished();step++) {
            var point=bolt.step();
            for(ServerPlayer candidate:match.onlinePlayers()) {
                if(candidate.getUUID().equals(owner)) continue;
                if(match.faction(candidate.getUUID())==EchoFaction.INTERCEPTOR) continue;
                if(!match.life(candidate.getUUID()).standing()) continue;
                if(!bolt.reaches(candidate.getX(),candidate.getY()+candidate.getBbHeight()*0.5D,
                        candidate.getZ(),candidate.getBbWidth()*0.5D)) continue;
                webHit(owner,candidate,now);
                bolt.finish();
                return;
            }
            Vec3 at=new Vec3(point.x(),point.y(),point.z());
            if(solidAt(at)) { splat(owner,at,now); bolt.finish(); return; }
        }
        // 服务端自己走子步，必须显式记账；否则弹永远不会过期。
        bolt.countTick();
        if(bolt.expired()) splat(owner,new Vec3(bolt.x(),bolt.y(),bolt.z()),now);
    }

    /** 蛛丝弹命中：照常走机会数结算，并在目标脚下留一块黏网。 */
    private void webHit(UUID owner,ServerPlayer victim,long now) {
        ServerPlayer shooter=level.getServer().getPlayerList().getPlayer(owner);
        var hit=lethal(victim,shooter,GameConstants.DeathReasons.KNIFE,false,false);
        if(hit!=EchoCombatState.Hit.IGNORED) {
            var hunter=match.hunterState(owner);
            boolean inWeb=hunter!=null && hunter.bindTicksAt(now,victim.getX(),victim.getZ())>0;
            int extra=org.agmas.noellesroles.game.modes.echo.hunter.EchoHunterState.boundBleed(inWeb);
            if(extra>0) match.state(victim.getUUID()).extendBleed(now,extra);
            io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                    org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.WEB_SPLAT,
                    victim.getEyePosition().add(0,-0.35,0),0,0F,0F);
            if(shooter!=null) shooter.playNotifySound(SoundEvents.PLAYER_ATTACK_CRIT,SoundSource.PLAYERS,0.8F,1.3F);
        }
        splat(owner,new Vec3(victim.getX(),victim.getY()+0.1D,victim.getZ()),now);
    }

    /** 在落点铺一块黏网；同一追猎者最多保留 {@code MAX_FIELDS} 块，避免长期堆积。 */
    private void splat(UUID owner,Vec3 at,long now) {
        var hunter=match.hunterState(owner);
        if(hunter!=null) {
            while(hunter.fields().size()>=MAX_FIELDS) hunter.fields().remove(0);
            hunter.addField(new org.agmas.noellesroles.game.modes.echo.hunter.EchoWebField(owner,
                    org.agmas.noellesroles.game.modes.echo.hunter.EchoWebField.Kind.SPLAT,at.x,at.y,at.z,now));
        }
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.WEB_SPLAT,at,0,0F,0F);
    }

    /** 黏网只困猎物：追猎者自己踩进阵里不受影响。 */
    private void applyWebSlow(long now) {
        for(ServerPlayer player:match.onlinePlayers()) {
            if(match.faction(player.getUUID())==EchoFaction.INTERCEPTOR) { applyWebSlow(player,1.0D); continue; }
            double factor=1.0D;
            for(UUID owner:match.participants()) {
                var hunter=match.hunterState(owner);
                if(hunter==null) continue;
                for(var field:hunter.fields()) if(field.covers(now,player.getX(),player.getZ())) factor=Math.min(factor,field.slow());
            }
            applyWebSlow(player,factor);
        }
    }
    private void applyWebSlow(ServerPlayer player,double factor) {
        var speed=player.getAttribute(Attributes.MOVEMENT_SPEED); if(speed==null) return;
        var existing=speed.getModifier(WEB_SLOW);
        if(Math.abs(factor-1.0D)<0.005D) { if(existing!=null) speed.removeModifier(WEB_SLOW); return; }
        double amount=factor-1.0D;
        if(existing!=null && Math.abs(existing.amount()-amount)<0.002D) return;
        if(existing!=null) speed.removeModifier(WEB_SLOW);
        speed.addTransientModifier(new AttributeModifier(WEB_SLOW,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    /** 裂爪主动技能「撕裂突进」：沿视线突进一小段，并把连击直接推到终结技。 */
    public void rendDash(ServerPlayer player) {
        var hunter=match.hunterState(player.getUUID());
        if(hunter==null) return;
        Vec3 look=player.getLookAngle();
        Vec3 eye=player.getEyePosition();
        var block=level.clip(new ClipContext(eye,eye.add(look.scale(REND_DASH_RANGE+REND_DASH_PADDING)),
                ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        double allowed=REND_DASH_RANGE;
        if(block.getType()!=HitResult.Type.MISS)
            allowed=Math.max(0.5D,Math.sqrt(eye.distanceToSqr(block.getLocation()))-REND_DASH_PADDING);
        Vec3 flat=new Vec3(look.x,0,look.z);
        flat=flat.lengthSqr()<1.0E-4D?new Vec3(0,0,1):flat.normalize().scale(allowed);
        if(level.noCollision(player,player.getBoundingBox().move(flat)))
            player.teleportTo(level,player.getX()+flat.x,player.getY(),player.getZ()+flat.z,player.getYRot(),player.getXRot());
        hunter.combo().forceFinisher(match.matchTick());
        player.playNotifySound(SoundEvents.PLAYER_ATTACK_SWEEP,SoundSource.PLAYERS,0.9F,0.75F);
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.REND_CLAW,
                player.getEyePosition(),0,(float)(Math.toDegrees(Math.atan2(-look.x,look.z))),
                (float)Math.toDegrees(Math.asin(-Math.max(-1,Math.min(1,look.y)))),3F);
    }

    /** 织网主动技能「蛛网阵」：在脚下布下一片黏网，阵内锁定值不衰减。 */
    public void placeWebNest(ServerPlayer player) {
        var hunter=match.hunterState(player.getUUID());
        if(hunter==null) return;
        long now=match.matchTick();
        while(hunter.fields().size()>=MAX_FIELDS) hunter.fields().remove(0);
        hunter.addField(new org.agmas.noellesroles.game.modes.echo.hunter.EchoWebField(player.getUUID(),
                org.agmas.noellesroles.game.modes.echo.hunter.EchoWebField.Kind.NEST,
                player.getX(),player.getY(),player.getZ(),now));
        io.wifi.starrailexpress.util.ParticleFx.sendCustom(level,
                org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFxIds.WEB_FIELD,
                new Vec3(player.getX(),player.getY()+0.1D,player.getZ()),0,
                (float)org.agmas.noellesroles.game.modes.echo.hunter.EchoWebField.NEST_RADIUS);
        player.playNotifySound(SoundEvents.AMETHYST_BLOCK_CHIME,SoundSource.PLAYERS,0.8F,0.7F);
    }

    /** 裂爪被动「血腥味」：{@link EchoHuntLoop#FOCUS_RANGE} 内是否有正在流血的猎物。 */
    private boolean bleedingNearby(ServerPlayer hunter,long now) {
        for(ServerPlayer other:match.onlinePlayers()) {
            if(other==hunter || match.faction(other.getUUID())==EchoFaction.INTERCEPTOR) continue;
            if(other.distanceToSqr(hunter)>EchoHuntLoop.FOCUS_RANGE*EchoHuntLoop.FOCUS_RANGE) continue;
            var state=match.state(other.getUUID());
            if(state!=null && state.life()==EchoLifeState.DOWNED && now<state.bleedUntil()) return true;
        }
        return false;
    }

    /** 织网被动「丝线感知」：目标连续静止的 tick 数。 */
    private int stillTicks(ServerPlayer player) { return stillTicks.getOrDefault(player.getUUID(),0); }
    private void trackStillness(ServerPlayer player) {
        Vec3 previous=lastPositions.put(player.getUUID(),player.position());
        if(previous!=null && previous.distanceToSqr(player.position())<0.0025D) stillTicks.merge(player.getUUID(),1,Integer::sum);
        else stillTicks.put(player.getUUID(),0);
    }
    private boolean solidAt(Vec3 at) {
        BlockPos pos=BlockPos.containing(at);
        var state=level.getBlockState(pos);
        return !state.getCollisionShape(level,pos).isEmpty();
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
        // 蛛丝弹与黏网先推进：本 tick 的命中要在移动限制之前生效，否则目标会先被钉在原地再挨打。
        tickHunterProjectiles(now);
        for(ServerPlayer player:match.onlinePlayers()) {
            EchoCombatState state=match.state(player.getUUID());
            if(state.expireBleed(now)) {
                UUID killerId=downedBy.get(player.getUUID());
                ServerPlayer killer=killerId==null?null:level.getServer().getPlayerList().getPlayer(killerId);
                finishElimination(player,killer,GameConstants.DeathReasons.KNIFE);
            }
            trackStillness(player);
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
        var hunterClass=match.hunterClass(player.getUUID());
        var hunterState=match.hunterState(player.getUUID());
        if(bestGain>0) {
            if(hunterClass==EchoHunterClass.REND) bestGain=org.agmas.noellesroles.game.modes.echo.hunter.EchoHunterState.rendGain(
                    bestGain,bleedingNearby(player,now));
            else if(hunterClass==EchoHunterClass.WEAVER && loudest!=null) bestGain=org.agmas.noellesroles.game.modes.echo.hunter.EchoHunterState.weaverGain(
                    bestGain,stillTicks(loudest),Math.sqrt(player.distanceToSqr(loudest)));
        }
        // 站在自己的蛛网阵里锁定不衰减：这是织网唯一能「蹲点」的收益。
        boolean holdFocus=hunterState!=null && hunterState.holdsFocusAt(now,player.getX(),player.getZ());
        loop.tick(now,bestGain,holdFocus);
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
        nextHeartbeat.clear(); lastPositions.clear(); stillTicks.clear();
        for(UUID owner:match.participants()) {
            var hunter=match.hunterState(owner);
            if(hunter!=null) hunter.clear();
        }
        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntFx.clearAll();
    }
}
