package org.agmas.noellesroles.game.modes.echo.runtime;

import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.modes.echo.EchoBootstrap;
import org.agmas.noellesroles.game.modes.echo.map.*;
import java.io.IOException;
import java.util.*;

/** 地图重置后、身份分配前分帧准入；与手动诊断分离，不复用重置前的地形证明。 */
public final class EchoRouteAdmission {
    private record Context(String mapId,EchoMapConfig.Vec offset,EchoMapConfig.Box area,boolean jumping,
                           double borderMinX,double borderMaxX,double borderMinZ,double borderMaxZ,Set<UUID> roster) { }
    private static final class Job {
        final Context context;
        final EchoMapConfig map;
        final String files;
        final EchoRoutePermit permit;
        final EchoRouteCheck check;
        Job(Context context,EchoMapConfig map,String files,EchoRoutePermit permit,EchoRouteCheck check) {
            this.context=context; this.map=map; this.files=files; this.permit=permit; this.check=check;
        }
    }
    private static final Map<ServerLevel,Job> JOBS=new LinkedHashMap<>();
    private static long tick,rotation;
    private EchoRouteAdmission() { }
    private static boolean starting(ServerLevel level) {
        var game=SREGameWorldComponent.KEY.get(level);
        return game.getGameStatus()==SREGameWorldComponent.GameStatus.STARTING && game.getGameMode().identifier.equals(EchoBootstrap.ID);
    }
    private static Context context(ServerLevel level,Set<UUID> roster) throws IOException {
        var areas=AreasWorldComponent.KEY.get(level); var a=areas.getPlayArea(); var border=level.getWorldBorder();
        // 与开局预检同一套生效地图：绑定优先，没有绑定按 Areas 地图名。
        String mapId=EchoActiveMap.effective(level.getServer().getWorldPath(LevelResource.ROOT),areas.mapName);
        return new Context(mapId,EchoPreflight.offset(areas),new EchoMapConfig.Box(a.minX,a.minY,a.minZ,a.maxX,a.maxY,a.maxZ),areas.areasSettings.canJump,
                border.getMinX(),border.getMaxX(),border.getMinZ(),border.getMaxZ(),roster);
    }
    private static String files(ServerLevel level,String mapId) throws IOException {
        var root=level.getServer().getWorldPath(LevelResource.ROOT).toRealPath();
        return EchoMapRepository.digest(root,mapId)+":"+org.agmas.noellesroles.game.modes.echo.rules.EchoRuleRepository.digest(root);
    }
    private static EchoRoutePermit.Stamp stamp(ServerLevel level,Job job) {
        return new EchoRoutePermit.Stamp(job.context.toString()+job.files,EchoTerrainRevision.current(level));
    }
    private static void message(ServerLevel level,String key,Object... args) { level.players().forEach(p -> p.sendSystemMessage(Component.translatable(key,args))); }
    private static void fail(ServerLevel level,Job job,String reason) {
        if(job.permit.status()!=EchoRoutePermit.Status.FAILED) {
            job.permit.fail(); message(level,"echo.map.admission_failed",reason);
        }
    }
    private static boolean current(ServerLevel level,Job job,boolean checkFiles) throws IOException {
        if(!starting(level) || !job.context.equals(context(level,job.context.roster()))) { fail(level,job,"地图、偏移、设置或边界已改变"); return false; }
        if(!job.permit.valid(stamp(level,job),tick)) { fail(level,job,"地形/区块发生变化或许可已过期"); return false; }
        if(checkFiles && !job.files.equals(files(level,job.context.mapId()))) { fail(level,job,"地图或规则文件已改变"); return false; }
        return true;
    }
    /** 由公共异步闸门在重置完成后调用；失败时放行到原有否决分支，由其统一撤销准备态。 */
    public static boolean defer(ServerLevel level,List<ServerPlayer> players) {
        if(!starting(level)) return false;
        Job job=JOBS.get(level);
        try {
            var roster=Set.copyOf(players.stream().map(ServerPlayer::getUUID).toList());
            if(job!=null && job.context!=null && !roster.equals(job.context.roster())) { fail(level,job,"参赛名单已改变"); return false; }
            if(job==null) {
                var c=context(level,roster);
                var check=EchoPreflight.check(level,c.roster().size(),true);
                if(!check.valid() || JOBS.size()>=2) {
                    var permit=new EchoRoutePermit(new EchoRoutePermit.Stamp("invalid",0),tick); permit.fail();
                    JOBS.put(level,new Job(c,null,"",permit,null));
                    check.errors().stream().limit(8).forEach(error -> level.players().forEach(p -> p.sendSystemMessage(error)));
                    message(level,"echo.map.admission_failed",JOBS.size()>2?"开局路线任务已达上限":"预检未通过"); return false;
                }
                EchoTerrainRevision.watch(level,c.area());
                String files=files(level,c.mapId());
                var permit=new EchoRoutePermit(new EchoRoutePermit.Stamp(c.toString()+files,EchoTerrainRevision.current(level)),tick);
                job=new Job(c,check.prepared().map(),files,permit,new EchoRouteCheck(level,check.prepared().map())); JOBS.put(level,job);
                message(level,"echo.map.admission_started");
            }
            if(job.permit.status()==EchoRoutePermit.Status.FAILED || !current(level,job,false)) return false;
            return job.permit.status()==EchoRoutePermit.Status.SCANNING || job.permit.status()==EchoRoutePermit.Status.MANUAL;
        } catch(IOException | RuntimeException failure) {
            Noellesroles.LOGGER.warn("回声开局路线准备失败",failure);
            if(job!=null) fail(level,job,"路线准备异常");
            else { var permit=new EchoRoutePermit(new EchoRoutePermit.Stamp("invalid",0),tick); permit.fail(); JOBS.put(level,new Job(null,null,"",permit,null)); }
            return false;
        }
    }
    public static boolean consume(ServerLevel level,EchoMapConfig map,List<ServerPlayer> players) {
        Job job=JOBS.get(level);
        try {
            if(job==null || job.permit.status()!=EchoRoutePermit.Status.AUTHORIZED || !map.equals(job.map)
                    || !job.context.roster().equals(Set.copyOf(players.stream().map(ServerPlayer::getUUID).toList())) || !current(level,job,true)) return false;
            return job.permit.consume(stamp(level,job),tick);
        } catch(IOException | RuntimeException failure) { Noellesroles.LOGGER.warn("回声路线许可校验失败",failure); return false; }
        finally { JOBS.remove(level); }
    }
    public static int confirm(CommandSourceStack source,String nonce) throws IOException {
        if(!source.hasPermission(2)) throw new IOException("需要OP2权限");
        var level=source.getLevel(); var job=JOBS.get(level);
        if(job==null || job.permit.status()!=EchoRoutePermit.Status.MANUAL) throw new IOException("没有待人工验收的开局路线；已知失败不能放行");
        UUID supplied;
        try { supplied=UUID.fromString(nonce); } catch(IllegalArgumentException invalid) { fail(level,job,"确认令牌格式错误"); throw new IOException("确认令牌无效"); }
        if(!current(level,job,true) || !job.permit.confirm(supplied,stamp(level,job),tick)) throw new IOException("确认令牌过期、失效或已使用");
        Noellesroles.LOGGER.info("回声路线人工验收: operator={} dimension={} map={} files={} revision={}",source.getTextName(),level.dimension().location(),job.context.mapId(),job.files,EchoTerrainRevision.current(level));
        message(level,"echo.map.admission_confirmed"); return 1;
    }
    public static void cancel(ServerLevel level) { var job=JOBS.get(level); if(job!=null) fail(level,job,"管理员取消路线准备"); }
    public static void tick(MinecraftServer server) {
        tick++; JOBS.entrySet().removeIf(e -> !starting(e.getKey()));
        if(JOBS.isEmpty()) return;
        var levels=List.copyOf(JOBS.keySet()); var level=levels.get((int)(rotation++%levels.size())); var job=JOBS.get(level);
        if(job.permit.status()==EchoRoutePermit.Status.FAILED) return;
        try {
            if(!current(level,job,tick%20==0) || job.permit.status()!=EchoRoutePermit.Status.SCANNING) return;
            long end=System.nanoTime()+2_000_000L;
            do { job.check.advance(16); } while(job.check.status()==EchoRouteCheck.Status.RUNNING && System.nanoTime()<end);
            if(job.check.status()!=EchoRouteCheck.Status.RUNNING && current(level,job,true)) {
                var result=switch(job.check.status()) { case PASSED -> EchoRoutePermit.Result.PASSED; case MANUAL -> EchoRoutePermit.Result.MANUAL; default -> EchoRoutePermit.Result.FAILED; };
                job.permit.complete(result,stamp(level,job),tick);
                job.check.issues().stream().limit(12).forEach(issue -> message(level,"echo.map.error",issue));
                if(result==EchoRoutePermit.Result.MANUAL) {
                    message(level,"echo.map.admission_manual");
                    level.players().stream().filter(p -> p.hasPermissions(2)).forEach(p -> p.sendSystemMessage(Component.translatable("echo.map.admission_token","/sre:echo map routes confirm "+job.permit.nonce())));
                } else message(level,result==EchoRoutePermit.Result.PASSED?"echo.map.admission_passed":"echo.map.admission_failed",job.check.visited());
            }
        } catch(IOException | RuntimeException failure) { Noellesroles.LOGGER.warn("回声开局路线扫描失败",failure); fail(level,job,"路线扫描异常；不允许人工绕过异常"); }
    }
    public static void unload(ServerLevel level) { JOBS.remove(level); EchoTerrainRevision.unload(level); }
    public static void stop() { JOBS.clear(); EchoTerrainRevision.stop(); tick=0; rotation=0; }
}
