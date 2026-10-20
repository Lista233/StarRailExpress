package org.agmas.noellesroles.game.modes.echo.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import io.wifi.utils.client.betterrender.FakeGuiGraphics;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.EchoGameMode;
import org.agmas.noellesroles.game.modes.echo.cards.EchoCardRegistry;
import org.agmas.noellesroles.game.modes.echo.objective.EchoPublicPayload;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;

/** 公开目标与本人状态分区；旁观者不读取其他人的私有RoleData。 */
public final class EchoHud {
    private static EchoPublicPayload view;
    private static int age;
    private static int lastCargo = -1, lastKey = -1;
    private static long cargoChangedAt, keyChangedAt;
    private static EchoLifeState lastLife;
    private static long lifeChangedAt;
    private EchoHud() { }
    static EchoPublicPayload view() { return view; }
    static int age() { return age; }
    public static void initialize() {
        ClientPlayNetworking.registerGlobalReceiver(EchoPublicPayload.ID,(p,c) -> c.client().execute(() -> { view=p; age=0; }));
        EchoObjectiveClient.initialize();
        EchoScreenFx.initialize();
        EchoSkillHud.initialize();
        EchoAtmosphereClient.initialize();
        org.agmas.noellesroles.game.modes.echo.anim.EchoHuntClient.initialize();
        EchoHuntHud.initialize();
        EchoHuntFxClient.initialize();
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if(view==null) return;
            if(client.level==null || client.player==null || !client.level.dimension().location().equals(view.dimension()) || ++age>100
                    || !(io.wifi.starrailexpress.cca.SREGameWorldComponent.KEY.get(client.level).getGameMode() instanceof EchoGameMode)
                    || view.state().phase()==EchoPhase.FINISHED || view.state().phase()==EchoPhase.SETTLING) view=null;
        });
        org.agmas.noellesroles.client.event.CommonHudRenderCallback.EVENT.register((g,dt) -> render(g));
    }
    private static void render(FakeGuiGraphics g) {
        var client=Minecraft.getInstance();
        if(client.player==null || client.level==null || client.options.hideGui || view==null || !client.level.dimension().location().equals(view.dimension())) return;
        int width=client.getWindow().getGuiScaledWidth(),height=client.getWindow().getGuiScaledHeight();
        var s=view.state(); long tick=s.displayTick(age);
        int pw=Math.min(360,width-16),x=(width-pw)/2,y=4;
        int rows=3+(s.phase()==EchoPhase.OPERATIONS?s.beacons().size():s.phase()==EchoPhase.EVACUATION?1:0);
        g.fillGradient(x,y,x+pw,y+rows*11+8,0xD81A1008,0xD820140A);
        g.renderOutline(x,y,pw,rows*11+8,0xFF8B6914);
        g.fill(x+1,y+1,x+pw-1,y+2,0x33FFE8C0);
        long seconds=(s.displayRemaining(age)+19)/20;
        row(g,Component.translatable("echo.hud.phase_time",Component.translatable("echo.phase."+s.phase().name().toLowerCase(Locale.ROOT)),String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60),s.paused()?Component.translatable("echo.hud.paused"):Component.empty()),x+5,y+5,pw-10,0xFFD4AF37);
        row(g,Component.translatable("echo.hud.gates",Component.translatable(s.coreOpen()?"echo.hud.open":"echo.hud.closed"),s.phase()==EchoPhase.EVACUATION?Component.translatable("echo.hud.permanent"):EchoPublicState.seconds(s.coreUntil(),tick),Component.translatable(s.evacuationOpen()?"echo.hud.open":"echo.hud.closed")),x+5,y+16,pw-10,0xFFFFF4DC);
        row(g,Component.translatable("echo.hud.overload",s.overloadUsed()?0:1,EchoPublicState.seconds(s.closedUntil(),tick)),x+5,y+27,pw-10,0xFFE0AD5B);
        int by=y+38;
        if(s.phase()==EchoPhase.OPERATIONS) for(var b:s.beacons()) {
            var status=b.active()?Component.translatable("echo.hud.active"):Component.translatable("echo.hud.beacon_timers",EchoPublicState.seconds(b.lockedUntil(),tick),EchoPublicState.seconds(b.pausedUntil(),tick),b.stable()?1:0);
            row(g,Component.translatable("echo.hud.beacon_full",client.font.plainSubstrByWidth(b.id(),40),b.delivered(),b.required(),b.progress()/10,status),x+5,by,pw-10,b.active()?0xFF72C17B:0xFF5EB7D8); by+=11;
        }
        else if(s.phase()==EchoPhase.EVACUATION) row(g,Component.translatable("echo.hud.evacuation",s.escaped(),s.quota()),x+5,by,pw-10,0xFF72C17B);
        var d=EchoBagClient.data();
        if(d==null || d.faction()==null || !view.match().equals(d.matchId()) || client.player.isSpectator()) return;
        int half=(width-24)/2;
        if(d.contract()!=null && d.contractProgress()!=null) {
            var p=d.contractProgress();
            row(g,Component.translatable("echo.contract.hud",Component.translatable("echo.contract."+d.contract().name().toLowerCase(Locale.ROOT)+".name"),Component.translatable("echo.contract.state."+p.status().toLowerCase(Locale.ROOT)),p.primary(),p.secondary()),8,y+rows*11+12,width-16,0xFFD4AF37);
        }
        int cargoNow=d.cargo().size(), keyNow=d.key()==null?0:1;
        if(cargoNow!=lastCargo) { lastCargo=cargoNow; cargoChangedAt=System.currentTimeMillis(); }
        if(keyNow!=lastKey) { lastKey=keyNow; keyChangedAt=System.currentTimeMillis(); }
        float cargoBump=Math.max(EchoAnim.bump(cargoChangedAt,900),EchoAnim.bump(keyChangedAt,900));
        row(g,Component.translatable("echo.hud.cargo",cargoNow,d.capacity(),d.blackCargoCount(),keyNow),8,height-82,half,EchoAnim.blend(0xFFFFF4DC,0xFFD4AF37,cargoBump));
        if(d.life()!=lastLife) { lastLife=d.life(); lifeChangedAt=System.currentTimeMillis(); }
        float lifeBump=EchoAnim.bump(lifeChangedAt,1200);
        int lifeColor=d.life()==EchoLifeState.DOWNED?0xFFE06B65:0xFF72C17B;
        row(g,Component.translatable("echo.hud.life",Component.translatable("echo.life."+d.life().name().toLowerCase(Locale.ROOT)),(d.bleedRemaining()+19)/20),8,height-71,half,EchoAnim.blend(lifeColor,0xFFFFF4DC,lifeBump));
        if(d.key()!=null && d.keyPulseAt()>0) row(g,Component.translatable("echo.hud.key_pulse",EchoPublicState.seconds(d.keyPulseAt(),tick)),8,height-60,half,0xFFE0AD5B);
        Component skill=switch(d.faction()) {
            case CARRIER -> Component.translatable("echo.hud.shove",(d.shoveRemaining()+19)/20);
            case INTERCEPTOR -> Component.translatable("echo.hud.overload_skill",s.overloadUsed()?0:1);
            case BROKER -> Component.translatable("echo.hud.trade_skill");
        };
        row(g,skill,width/2+4,height-71,half,0xFFD4AF37);
        if(d.selectedCard()!=null) d.items().stream().filter(i -> i.instanceId().equals(d.selectedCard())).findFirst().ifPresent(i -> {
            var c=EchoCardRegistry.require(i.definitionId());
            if(c.shortId().equals("forged_signal")) row(g,Component.translatable("echo.ui.forgery_choice"),width/2+4,height-60,half,0xFF9E8B6E);
            row(g,Component.translatable("echo.hud.equipped",Component.translatable(c.nameKey())),width/2+4,height-82,half,0xFFD4AF37);
        });
        int n=0;
        for(var signal:d.signals()) if(signal.target()==null && d.projectedTick()<signal.until() && n<2) {
            String key=switch(signal.kind()) { case "salvage" -> "echo.signal.salvage"; case "key_contract" -> "echo.signal.key_contract"; case "cargo_pulse" -> "echo.signal.cargo"; case "key_pulse" -> "echo.signal.key"; case "hearing" -> "echo.signal.hearing"; default -> "echo.signal.tracking"; };
            row(g,Component.translatable(key,Component.translatable("echo.direction."+signal.sector())),8,height-98-11*n++,width-16,0xFFFFA080);
        }
        if(EchoCardClient.selectingSecond()) row(g,Component.translatable("echo.ui.second_point"),8,height-124,width-16,0xFFD4AF37);
        else if(!d.requestError().isEmpty()) row(g,Component.translatable("echo.error."+d.requestError().toLowerCase(Locale.ROOT)),8,height-124,width-16,0xFFE06B65);
        if(client.screen==null) {
            var action=d.actionView(); int aw=Math.min(240,width-24),ax=(width-aw)/2,ay=height/2+28;
            if(action!=null) {
                int progress=action.progress(tick);
                row(g,Component.translatable("echo.hud.action_progress",Component.translatable("echo.action."+action.kind().toLowerCase(Locale.ROOT)),progress/10,EchoPublicState.seconds(action.startedAt()+action.duration(),tick)),ax,ay,aw,0xFFFFF4DC);
                g.fill(ax,ay+12,ax+aw,ay+16,0xD8382A1B); g.fill(ax,ay+12,ax+aw*progress/1000,ay+16,0xFFD4AF37);
            } else if(!d.interactionError().isEmpty()) row(g,Component.translatable("echo.error."+d.interactionError().toLowerCase(Locale.ROOT)),ax,ay,aw,0xFFE06B65);
        }
    }
    private static void row(FakeGuiGraphics g,Component text,int x,int y,int width,int color) {
        var font=Minecraft.getInstance().font;
        // 不跨栏或覆盖相邻目标；长文可在背包/契约面板阅读。
        g.enableScissor(x,y,x+width,y+10); g.drawString(font,text,x,y,color,false); g.disableScissor();
    }
}
