package org.agmas.noellesroles.game.modes.echo.map;

import com.google.gson.*;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

/** 仅存模板坐标的内存草稿；先在副本解析成功再替换，失败不会破坏管理员已有编辑。 */
public final class EchoMapDraft {
    private static final Gson GSON=new Gson();
    public static final List<String> KINDS=List.of("carrier_spawn","interceptor_spawn","broker_spawn","spectator","beacon_open","beacon_core","crystal","altar","supply","black_market","black_node","black_dropoff","broker_exit","core_control","free_control","free_safe","evacuation_control","evacuation_safe","drop_anchor");
    public static final List<String> VOLUMES=List.of("core_area","core_door","evacuation_door","evacuation_zone");
    private JsonObject root;
    private long revision;
    public EchoMapDraft(EchoMapConfig source) throws IOException {
        if(!source.coordinateSpace().equals("template")) throw new IOException("编辑草稿必须使用模板坐标");
        EchoMapRepository.requireMapId(source.mapId()); root=GSON.toJsonTree(source).getAsJsonObject();
    }
    public static EchoMapDraft empty(String mapId) throws IOException {
        var p=new EchoMapConfig.Vec(0,64,0); var s=new EchoMapConfig.Spawn(0.5,64,0.5,0,0);
        return new EchoMapDraft(new EchoMapConfig(1,mapId,"template",new EchoMapConfig.Box(-4,64,-4,5,68,5),
                List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),
                new EchoMapConfig.CoreGate(List.of(),new EchoMapConfig.FreeExit("service_exit",p,s)),
                new EchoMapConfig.Evacuation(p,new EchoMapConfig.Box(8,64,8,9,67,9),new EchoMapConfig.Box(10,64,10,11,68,11),s,p),
                List.of(),List.of(),new EchoMapConfig.Point("broker_exit",p),s));
    }
    public long revision() { return revision; }
    public EchoMapConfig snapshot() throws IOException { return EchoMapRepository.parse(root.toString()); }
    public EchoMapConfig exported(String mapId) throws IOException {
        EchoMapRepository.requireMapId(mapId); var copy=root.deepCopy(); copy.addProperty("mapId",mapId); return EchoMapRepository.parse(copy.toString());
    }
    private void update(Consumer<JsonObject> change) throws IOException {
        var copy=root.deepCopy(); change.accept(copy); EchoMapRepository.parse(copy.toString()); root=copy; revision++;
    }
    private static JsonObject vec(EchoMapConfig.Vec p) { return GSON.toJsonTree(p).getAsJsonObject(); }
    private static JsonObject spawn(EchoMapConfig.Vec p,float yaw,float pitch) { return GSON.toJsonTree(new EchoMapConfig.Spawn(p.x(),p.y(),p.z(),yaw,pitch)).getAsJsonObject(); }
    private static void id(String id) { if(id==null || !id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("点位id必须是1–64位小写字母、数字、下划线或连字符"); }
    private static JsonObject entry(JsonArray array,String id) {
        for(var element:array) if(element.getAsJsonObject().get("id").getAsString().equals(id)) return element.getAsJsonObject();
        if(array.size()>=128) throw new IllegalArgumentException("点位数组超过128项");
        var next=new JsonObject(); next.addProperty("id",id); array.add(next); return next;
    }
    private static void putSpawn(JsonObject tree,String field,String id,JsonObject value) {
        int index;
        try { index=Integer.parseInt(id); } catch(NumberFormatException invalid) { throw new IllegalArgumentException("出生点id必须是0起的连续下标"); }
        var array=tree.getAsJsonArray(field);
        if(index<0 || index>array.size() || index>=128) throw new IllegalArgumentException("出生点下标越界；新点使用当前数组长度");
        if(index==array.size()) array.add(value); else array.set(index,value);
    }
    public void mark(String kind,String id,EchoMapConfig.Vec point,float yaw,float pitch,int minPlayers) throws IOException {
        if(!KINDS.contains(kind)) throw new IllegalArgumentException("未知点位kind: "+kind);
        id(id); if(minPlayers<6 || minPlayers>12) throw new IllegalArgumentException("minPlayers必须为6–12");
        update(tree -> {
            JsonObject gate=tree.getAsJsonObject("coreGate"),evacuation=tree.getAsJsonObject("evacuation");
            switch(kind) {
                case "carrier_spawn" -> putSpawn(tree,"carrierSpawns",id,spawn(point,yaw,pitch));
                case "interceptor_spawn" -> putSpawn(tree,"interceptorSpawns",id,spawn(point,yaw,pitch));
                case "broker_spawn" -> putSpawn(tree,"brokerSpawns",id,spawn(point,yaw,pitch));
                case "spectator" -> tree.add("spectatorPos",spawn(point,yaw,pitch));
                case "free_control" -> { gate.getAsJsonObject("freeExit").addProperty("id",id); gate.getAsJsonObject("freeExit").add("controlPos",vec(point)); }
                case "free_safe" -> gate.getAsJsonObject("freeExit").add("outsideSafe",spawn(point,yaw,pitch));
                case "evacuation_control" -> evacuation.add("controlPos",vec(point));
                case "evacuation_safe" -> evacuation.add("outsideSafe",spawn(point,yaw,pitch));
                case "drop_anchor" -> evacuation.add("exitDropAnchor",vec(point));
                case "broker_exit" -> { var e=new JsonObject(); e.addProperty("id",id); e.add("pos",vec(point)); tree.add("brokerExit",e); }
                case "core_control" -> {
                    var e=entry(gate.getAsJsonArray("doors"),id); e.add("controlPos",vec(point));
                    if(!e.has("volume")) e.add("volume",GSON.toJsonTree(new EchoMapConfig.Box(Math.floor(point.x()),Math.floor(point.y()),Math.floor(point.z()),Math.floor(point.x())+1,Math.floor(point.y())+1,Math.floor(point.z())+1)));
                }
                default -> {
                    String field=switch(kind) {
                        case "beacon_open","beacon_core" -> "beacons"; case "crystal" -> "crystalNodes"; case "altar" -> "altars";
                        case "supply","black_market" -> "supplyCrates"; case "black_node" -> "blackCrystalNodes"; case "black_dropoff" -> "blackDropoffs";
                        default -> throw new IllegalArgumentException("未知点位kind");
                    };
                    var e=entry(tree.getAsJsonArray(field),id); e.add("pos",vec(point));
                    if(kind.startsWith("beacon_")) { e.addProperty("region",kind.equals("beacon_core")?"core":"open"); e.addProperty("minPlayers",minPlayers); }
                    if(kind.equals("crystal")) e.addProperty("minPlayers",minPlayers);
                    if(kind.equals("supply") || kind.equals("black_market")) {
                        e.addProperty("minPlayers",minPlayers); e.addProperty("quality",kind.equals("supply")?"COMMON":"BLACK_MARKET"); e.addProperty("repeatable",kind.equals("supply"));
                    }
                }
            }
        });
    }
    public void volume(String kind,String id,EchoMapConfig.Box volume) throws IOException {
        if(!VOLUMES.contains(kind)) throw new IllegalArgumentException("未知区域kind: "+kind);
        if(!volume.integral() || volume.volume()>1_000_000) throw new IllegalArgumentException("区域必须为整数半开框，且体积不超过100万");
        if((kind.equals("core_door") || kind.equals("evacuation_door")) && volume.volume()>1024) throw new IllegalArgumentException("门体体积超过1024");
        update(tree -> {
            var value=GSON.toJsonTree(volume);
            switch(kind) {
                case "core_area" -> tree.add("coreArea",value);
                case "evacuation_door" -> tree.getAsJsonObject("evacuation").add("doorVolume",value);
                case "evacuation_zone" -> tree.getAsJsonObject("evacuation").add("zone",value);
                case "core_door" -> {
                    JsonObject door=null;
                    for(var e:tree.getAsJsonObject("coreGate").getAsJsonArray("doors")) if(e.getAsJsonObject().get("id").getAsString().equals(id)) door=e.getAsJsonObject();
                    if(door==null) throw new IllegalArgumentException("请先mark core_control，设置同id控制点");
                    door.add("volume",value);
                }
                default -> throw new IllegalArgumentException("未知区域kind");
            }
        });
    }
    public void remove(String field,String id) throws IOException {
        if(!List.of("carrierSpawns","interceptorSpawns","brokerSpawns","beacons","crystalNodes","altars","supplyCrates","blackCrystalNodes","blackDropoffs","doors").contains(field)) throw new IllegalArgumentException("只能删除列表内点位");
        update(tree -> {
            var array=field.equals("doors")?tree.getAsJsonObject("coreGate").getAsJsonArray(field):tree.getAsJsonArray(field);
            boolean found=false;
            for(int i=array.size()-1;i>=0;i--) if(field.endsWith("Spawns")?Integer.toString(i).equals(id):array.get(i).getAsJsonObject().get("id").getAsString().equals(id)) { array.remove(i); found=true; }
            if(!found) throw new IllegalArgumentException("不存在该点位");
        });
    }
}
