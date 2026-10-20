package org.agmas.noellesroles.game.modes.echo.client;

import io.wifi.starrailexpress.client.gui.widget.SreButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.agmas.noellesroles.game.modes.echo.cards.*;
import org.agmas.noellesroles.game.modes.echo.inventory.*;
import org.agmas.noellesroles.game.modes.echo.state.*;
import java.util.*;

/** 固定六格与同屏三选一；所有写操作等待服务器投影，面板不拥有可写资源。 */
public final class EchoInventoryScreen extends Screen {
    private final Screen parent;
    private final List<UUID> selected=new ArrayList<>();
    private UUID match;
    private int x,y,w,h;
    private String signature="";
    private final int[] choiceScroll=new int[3];
    private int detailScroll;
    private UUID visibleOffer;
    private long openedAt;
    private long offerShownAt;
    private Component preview=Component.empty();
    public EchoInventoryScreen(Screen parent) { super(Component.translatable("echo.ui.bag")); this.parent=parent; }
    private EchoRoleData data() { return EchoBagClient.data(); }
    @Override protected void init() {
        clearWidgets(); if(openedAt==0) { openedAt=System.currentTimeMillis(); minecraft.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.UI_BUTTON_CLICK,1F)); } w=Math.min(700,width-12); h=Math.min(360,height-12); x=(width-w)/2; y=(height-h)/2;
        var data=data(); if(data==null || data.bag()==null) return;
        if(match==null) match=data.matchId();
        selected.removeIf(id -> data.items().stream().noneMatch(i -> i.instanceId().equals(id)));
        boolean waiting=EchoBagClient.waiting(); var draft=data.draft();
        boolean offered=draft!=null && draft.state().equals("OFFERED");
        UUID nextOffer=offered?draft.offerId():null;
        if(!Objects.equals(visibleOffer,nextOffer)) { Arrays.fill(choiceScroll,0); visibleOffer=nextOffer; offerShownAt=System.currentTimeMillis(); }
        boolean channel=draft!=null && draft.state().equals("CHANNELING");
        boolean allowed=data.phase().acceptsActions() && data.life().standing() && !data.controlled()
                && !io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(minecraft.level).isTimeFrozen() && !channel && !offered && !waiting;
        button(Component.translatable(data.trade()==null?"echo.trade.title":"echo.trade.active"),x+w-118,y+5,110,18,() -> minecraft.setScreen(new EchoTradeScreen(this))).active=!waiting && !channel && !offered;
        if(data.contract()!=null) button(Component.translatable("echo.contract.title"),x+w-202,y+5,78,18,() -> minecraft.setScreen(new EchoContractScreen(this)));
        if(EchoSpecializationClient.view()!=null && !EchoSpecializationClient.view().locked())
            button(Component.translatable("echo.spec.title"),x+w-282,y+5,76,18,() -> EchoSpecializationClient.open(minecraft));
        if(offered) {
            int cw=(w-24)/3;
            for(int n=0;n<3;n++) {
                final int choice=n;
                var button=button(Component.translatable("echo.ui.choose",n+1),x+8+n*(cw+4),y+h-48,cw-2,
                        () -> EchoBagClient.send(EchoInventoryPayload.Operation.CHOOSE,draft.offerId(),null,choice));
                button.active=!waiting && data.life().potentiallyEscapable() && !io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(minecraft.level).isTimeFrozen();
            }
        } else {
            int gridW=(w-24)/2; int cw=(gridW-8)/3;
            for(int n=0;n<6;n++) {
                final int at=n; var slot=data.bag().slots().get(n);
                Component label=Component.translatable("echo.ui.slot",n+1,slot.reservation()!=null?Component.translatable("echo.ui.reserved"):
                        slot.items().isEmpty()?Component.translatable("echo.ui.empty"):name(slot.items().getFirst()).copy().append(slot.items().size()==2?" ×2":""));
                var b=button(label,x+8+(n%3)*(cw+3),y+48+(n/3)*28,cw,26,() -> selectSlot(at));
                b.setTooltip(Tooltip.create(slot.items().isEmpty()?label:detail(slot.items().getFirst())));
                b.active=!waiting && slot.reservation()==null;
                if(slot.items().size()==2) {
                    UUID id=slot.items().get(1).instanceId();
                    button(Component.literal("2"),x+8+(n%3)*(cw+3)+cw-14,y+48+(n/3)*28+12,14,14,() -> select(id)).active=!waiting;
                }
            }
            var primary=selected.isEmpty()?null:data.items().stream().filter(i -> i.instanceId().equals(selected.getFirst())).findFirst().orElse(null);
            boolean ownBound=primary!=null && draft!=null && draft.packId().equals(primary.instanceId()) && draft.state().equals("BOUND");
            boolean unlocked=primary!=null && !data.bag().locks().containsKey(primary.instanceId());
            button(Component.translatable("echo.ui.open"),x+8,y+h-70,(w-20)/3,20,() -> EchoBagClient.send(EchoInventoryPayload.Operation.BEGIN_PACK,selected.getFirst(),null,0)).active=
                    allowed && data.trade()==null && primary!=null && primary.kind()==EchoResourceInstance.Kind.PACK && (unlocked || ownBound);
            button(Component.translatable("echo.ui.drop"),x+10+(w-20)/3,y+h-70,(w-20)/3,20,() -> {
                UUID id=selected.getFirst();
                if(primary.key()) EchoBagClient.send(EchoInventoryPayload.Operation.DROP,id,null,0);
                else minecraft.setScreen(new ConfirmScreen(confirm -> {
                    minecraft.setScreen(this); if(confirm) EchoBagClient.send(EchoInventoryPayload.Operation.DROP,id,null,0);
                },Component.translatable("echo.ui.confirm_drop"),name(primary)));
            }).active=allowed && unlocked;
            Optional<EchoCardDefinition> output=Optional.empty();
            if(selected.size()==2) {
                var second=data.items().stream().filter(i -> i.instanceId().equals(selected.get(1))).findFirst();
                if(primary!=null && second.isPresent()) output=EchoCardRegistry.craftOutput(primary.instanceId(),primary.definitionId(),second.get().instanceId(),second.get().definitionId(),data.faction());
            }
            preview=output.<Component>map(c -> Component.translatable("echo.ui.craft_preview",Component.translatable(c.nameKey()))).orElse(Component.translatable("echo.ui.select_two"));
            button(Component.translatable("echo.ui.craft"),x+12+2*((w-20)/3),y+h-70,(w-20)/3,20,() -> {
                UUID a=selected.get(0),b=selected.get(1);
                minecraft.setScreen(new ConfirmScreen(confirm -> { minecraft.setScreen(this); if(confirm) EchoBagClient.send(EchoInventoryPayload.Operation.CRAFT,a,b,0); },Component.translatable("echo.ui.craft"),preview));
            }).active=allowed && output.isPresent() && selected.stream().noneMatch(data.bag().locks()::containsKey);
            var card=primary==null || primary.kind()==EchoResourceInstance.Kind.PACK?null:EchoCardRegistry.require(primary.definitionId());
            boolean self=card!=null && (card.target()==EchoCardDefinition.Target.SELF || card.target()==EchoCardDefinition.Target.DIRECTION);
            var use=button(Component.translatable(self?"echo.ui.use":"echo.ui.equip"),x+8,y+108,Math.max(84,gridW),20,() -> {
                if(EchoBagClient.send(self?EchoInventoryPayload.Operation.USE_CARD:EchoInventoryPayload.Operation.EQUIP_CARD,primary.instanceId(),null,0) && !self) minecraft.setScreen(parent);
            });
            use.active=allowed && data.trade()==null && unlocked && card!=null && card.activation()!=EchoCardDefinition.Activation.PASSIVE && card.usableBy(data.faction());
            use.setTooltip(Tooltip.create(Component.translatable(self?"echo.ui.use_help":"echo.ui.equip_help")));
            if(channel) button(Component.translatable("echo.ui.cancel_open"),x+8,y+h-46,w-16,20,() -> EchoBagClient.send(EchoInventoryPayload.Operation.CANCEL_PACK,draft.offerId(),null,0)).active=!waiting;
        }
        button(Component.translatable("echo.ui.back"),x+w-64,y+h-24,56,18,this::onClose);
        signature=signature(data);
    }
    private SreButton button(Component label,int x,int y,int width,Runnable click) { return button(label,x,y,width,20,click); }
    private SreButton button(Component label,int x,int y,int width,int height,Runnable click) {
        var b=new SreButton(label,ignored -> click.run()); b.setX(x); b.setY(y); b.setWidth(width); b.setHeight(height); return addRenderableWidget(b);
    }
    private void select(UUID id) { if(!selected.remove(id)) { if(selected.size()==2) selected.removeFirst(); selected.add(id); } detailScroll=0; init(); }
    private void selectSlot(int slot) {
        var value=data().bag().slots().get(slot);
        if(value.items().isEmpty()) {
            var d=data(); boolean busy=d.draft()!=null && (d.draft().state().equals("CHANNELING") || d.draft().state().equals("OFFERED"));
            if(selected.size()==1 && d.life().standing() && !d.controlled() && d.phase().acceptsActions() && !busy
                    && !io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(minecraft.level).isTimeFrozen())
                EchoBagClient.send(EchoInventoryPayload.Operation.MOVE,selected.getFirst(),null,slot);
        }
        else select(value.items().getFirst().instanceId());
    }
    private String signature(EchoRoleData d) { return d.bag().revision()+":"+d.draft()+":"+d.trade()+":"+d.acknowledged()+":"+EchoBagClient.waiting()+":"+d.life()+":"+d.controlled()+":"+d.phase()+":"+io.wifi.starrailexpress.cca.SREGameTimeComponent.KEY.get(minecraft.level).isTimeFrozen(); }
    @Override public void tick() {
        var d=data(); if(d==null || d.matchId()==null || match!=null && !match.equals(d.matchId())) { onClose(); return; }
        if(d.bag()!=null && !signature(d).equals(signature)) init();
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { EchoBagClient.close(); minecraft.setScreen(parent); }
    @Override public void renderBackground(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        float p=EchoAnim.openProgress(openedAt,180);
        g.fillGradient(0,0,width,height,EchoAnim.fade(0xD018120A,p),EchoAnim.fade(0xD0061018,p));
        g.fillGradient(x,y,x+w,y+h,EchoAnim.fade(0xF01A1008,p),EchoAnim.fade(0xF020140A,p));
        g.renderOutline(x,y,w,h,EchoAnim.fade(0xFF8B6914,p)); g.fill(x+1,y+1,x+w-1,y+2,EchoAnim.fade(0x33FFE8C0,p));
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        super.render(g,mouseX,mouseY,partialTick);
        float p=EchoAnim.openProgress(openedAt,180);
        g.drawString(font,title,x+8,y+8,EchoAnim.fade(0xFFD4AF37,p),false);
        var d=data(); if(d==null || d.bag()==null) { g.drawString(font,Component.translatable("echo.ui.syncing"),x+8,y+30,0xFF9E8B6E,false); return; }
        text(g,Component.translatable("echo.ui.summary",d.cargo().size(),d.capacity(),d.key()==null?0:1,Component.translatable("echo.phase."+d.phase().name().toLowerCase(Locale.ROOT))),x+8,y+24,w-16,10,0xFFFFF4DC);
        var draft=d.draft(); Component hovered=null;
        if(draft!=null && draft.state().equals("OFFERED")) {
            int cw=(w-24)/3;
            for(int n=0;n<Math.min(3,draft.candidates().size());n++) {
                var card=EchoCardRegistry.require(draft.candidates().get(n)); int cx=x+8+n*(cw+4);
                float appear=EchoAnim.openProgress(offerShownAt+n*90L,220);
                g.fillGradient(cx,y+44,cx+cw-2,y+h-52,EchoAnim.fade(0xFF241A0E,appear),EchoAnim.fade(0xFF15110B,appear));
                g.renderOutline(cx,y+44,cw-2,h-96,EchoAnim.fade(0xFF8B6914,appear));
                icon(g,card,cx+4,y+48,24);
                text(g,Component.translatable(card.nameKey()),cx+32,y+49,cw-38,26,EchoAnim.fade(0xFFD4AF37,appear));
                text(g,Component.translatable("echo.ui.card_meta",Component.translatable("echo.rarity."+card.rarity().name().toLowerCase(Locale.ROOT)),Component.translatable("echo.series."+card.series().name().toLowerCase(Locale.ROOT))),cx+4,y+78,cw-10,24,0xFF9E8B6E);
                var body=cardBody(card);
                scrollText(g,body,cx+4,y+104,cw-10,Math.max(10,h-160),choiceScroll[n],0xFFFFF4DC);
                if(mouseX>=cx && mouseX<cx+cw && mouseY>=y+44 && mouseY<y+76) hovered=Component.translatable(card.nameKey());
            }
            // 截止提示与错误/等待状态共享底栏，避免窄屏互相覆盖。
        } else {
            int cell=((w-24)/2-8)/3;
            for(int n=0;n<6;n++) if(d.bag().slots().get(n).items().stream().anyMatch(i -> selected.contains(i.instanceId())))
                g.renderOutline(x+8+(n%3)*(cell+3),y+48+(n/3)*28,cell,26,0xFFD4AF37);
            if(!selected.isEmpty()) d.items().stream().filter(i -> i.instanceId().equals(selected.getFirst())).findFirst().ifPresent(item -> {
                int dx=x+w/2+4;
                if(item.kind()!=EchoResourceInstance.Kind.PACK) icon(g,EchoCardRegistry.require(item.definitionId()),dx,y+48,28);
                text(g,name(item),dx+32,y+48,w/2-46,28,0xFFD4AF37);
                scrollText(g,detail(item),dx,y+80,w/2-14,Math.max(20,h-176),detailScroll,0xFFFFF4DC);
            });
            text(g,preview,x+8,y+h-94,w-16,20,0xFFD4AF37);
            if(draft!=null) text(g,Component.translatable("echo.ui.draft_status",Component.translatable("echo.draft."+draft.state().toLowerCase(Locale.ROOT)),Math.max(0,((draft.state().equals("CHANNELING")?draft.deadline():draft.readyAt())-d.projectedTick()+19)/20)),x+8,y+36,w-16,10,0xFF5EB7D8);
        }
        Component status=EchoBagClient.waiting()?Component.translatable("echo.ui.waiting"):!d.requestError().isEmpty()?
                Component.translatable("echo.error."+d.requestError().toLowerCase(Locale.ROOT)):
                draft!=null && draft.state().equals("OFFERED")?Component.translatable("echo.ui.choice_timeout",Math.max(0,(draft.deadline()-d.projectedTick()+19)/20)):
                Component.translatable("echo.ui.server_authority");
        text(g,status,x+8,y+h-22,w-80,18,d.requestError().isEmpty()?0xFF9E8B6E:0xFFE06B65);
        if(hovered!=null) g.renderTooltip(font,font.split(hovered,Math.min(260,w-20)),mouseX,mouseY);
    }
    private void icon(GuiGraphics g,EchoCardDefinition card,int x,int y,int size) {
        g.blit(ResourceLocation.parse(card.icon()),x,y,size,size,0F,0F,64,64,64,64);
    }
    private Component cardBody(EchoCardDefinition card) {
        var body=EchoCardText.description(card);
        long owned=data().items().stream().filter(i -> i.kind()!=EchoResourceInstance.Kind.PACK)
                .map(i -> EchoCardRegistry.require(i.definitionId())).filter(c -> c.recipeGroup().equals(card.recipeGroup())).count();
        body.append("\n\n").append(Component.translatable("echo.ui.series_owned",owned));
        var output=EchoCardRegistry.recipes().get(card.recipeGroup());
        if(card.tier()==EchoCardDefinition.Tier.BASE && output!=null)
            body.append("\n").append(Component.translatable("echo.ui.craft_preview",Component.translatable(output.nameKey())));
        if(!card.usableBy(data().faction())) body.append("\n").append(Component.translatable("echo.ui.wrong_faction"));
        return body;
    }
    private int maxScroll(Component body,int width,int height) { return Math.max(0,font.split(body,Math.max(1,width-5)).size()*10-height); }
    private void scrollText(GuiGraphics g,Component body,int x,int y,int width,int height,int scroll,int color) {
        int max=maxScroll(body,width,height),offset=Math.min(scroll,max);
        g.enableScissor(x,y,x+width,y+height); int line=0;
        for(var part:font.split(body,Math.max(1,width-5))) { g.drawString(font,part,x,y+line++*10-offset,color,false); }
        g.disableScissor();
        if(max>0) {
            g.fill(x+width-2,y,x+width,y+height,0xFF483722);
            int thumb=Math.max(6,height*height/(height+max));
            int top=y+(height-thumb)*offset/max;
            g.fill(x+width-2,top,x+width,top+thumb,0xFFD4AF37);
        }
    }
    @Override public boolean mouseScrolled(double mx,double my,double horizontal,double vertical) {
        var d=data(); if(d==null || d.bag()==null) return super.mouseScrolled(mx,my,horizontal,vertical);
        if(d.draft()!=null && d.draft().state().equals("OFFERED")) {
            int cw=(w-24)/3;
            for(int n=0;n<Math.min(3,d.draft().candidates().size());n++) {
                int cx=x+8+n*(cw+4);
                if(mx>=cx && mx<cx+cw && my>=y+44 && my<y+h-52) {
                    var body=cardBody(EchoCardRegistry.require(d.draft().candidates().get(n)));
                    choiceScroll[n]=Math.max(0,Math.min(maxScroll(body,cw-10,Math.max(10,h-160)),choiceScroll[n]-(int)(vertical*20)));
                    return true;
                }
            }
        } else if(!selected.isEmpty() && mx>=x+w/2 && mx<x+w-8 && my>=y+48 && my<y+h-94) {
            var item=d.items().stream().filter(i -> i.instanceId().equals(selected.getFirst())).findFirst();
            if(item.isPresent()) detailScroll=Math.max(0,Math.min(maxScroll(detail(item.get()),w/2-14,Math.max(20,h-176)),detailScroll-(int)(vertical*20)));
            return true;
        }
        return super.mouseScrolled(mx,my,horizontal,vertical);
    }
    private void text(GuiGraphics g,Component text,int x,int y,int width,int height,int color) {
        g.enableScissor(x,y,x+width,y+height); int line=0;
        for(var part:font.split(text,width)) { g.drawString(font,part,x,y+line*10,color,false); line++; }
        g.disableScissor();
    }
    private Component name(EchoResourceInstance item) { return item.kind()==EchoResourceInstance.Kind.PACK?Component.translatable("echo.pack."+item.quality().name().toLowerCase(Locale.ROOT)):Component.translatable(EchoCardRegistry.require(item.definitionId()).nameKey()); }
    private Component detail(EchoResourceInstance item) {
        var text=name(item).copy();
        if(data().bag().locks().containsKey(item.instanceId())) text.append("\n").append(Component.translatable("echo.ui.locked"));
        if(item.kind()==EchoResourceInstance.Kind.PACK) return text.append("\n").append(Component.translatable("echo.ui.pack_help"));
        var card=EchoCardRegistry.require(item.definitionId());
        text.append("\n").append(EchoCardText.description(card));
        if(!card.usableBy(data().faction())) text.append("\n").append(Component.translatable("echo.ui.wrong_faction"));
        else if(card.activation()==EchoCardDefinition.Activation.PASSIVE) text.append("\n").append(Component.translatable("echo.ui.passive"));
        return text;
    }
}
