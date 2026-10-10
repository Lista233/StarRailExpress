package org.agmas.noellesroles.role.qust.roles.aimlabs;

import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Aimlabs 指令：允许 OP 配置练枪方块参数，或通过指令触发/关闭练习。
 * <p>
 * 用法：
 * <pre>
 * /aimlabs start &lt;pos&gt; [player] [facing]  触发已有方块的练习
 * /aimlabs run &lt;pos&gt; [player] [facing] [mode] [countdown] [targethits] ...
 *                                         无需方块，直接用参数启动虚拟练习
 * /aimlabs stop [player]                  关闭练习
 * /aimlabs set &lt;pos&gt; ...                  配置方块参数
 * </pre>
 */
public class AimlabsCommand {

    // ── 默认值（虚拟会话使用） ──
    private static final int DEF_COUNTDOWN = 30;
    private static final int DEF_TARGET_HITS = 15;
    private static final int DEF_AREA_WIDTH = 6;
    private static final int DEF_AREA_HEIGHT = 4;
    private static final float DEF_RADIUS = 0.35f;
    private static final int DEF_MAX_SPHERES = 4;
    private static final int DEF_HEIGHT_OFFSET = 1;

    // ── 游戏进行中的默认值（场景任务使用） ──
    public static final int GAME_AREA_WIDTH = 4;
    public static final int GAME_AREA_HEIGHT = 3;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {

            // ── start 子命令（需要方块） ──
            var startCmd = Commands.literal("start")
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                            .executes(ctx -> startSession(ctx.getSource(),
                                    BlockPos.containing(Vec3Argument.getVec3(ctx, "pos")),
                                    ctx.getSource().getPlayerOrException(), null))
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> startSession(ctx.getSource(),
                                            BlockPos.containing(Vec3Argument.getVec3(ctx, "pos")),
                                            EntityArgument.getPlayer(ctx, "player"), null))
                                    .then(Commands.argument("facing", StringArgumentType.word())
                                            .executes(ctx -> startSession(ctx.getSource(),
                                                    BlockPos.containing(Vec3Argument.getVec3(ctx, "pos")),
                                                    EntityArgument.getPlayer(ctx, "player"),
                                                    parseDirection(StringArgumentType.getString(ctx, "facing")))))));

            // ── run 子命令（无需方块，支持参数覆盖） ──
            var runCmd = Commands.literal("run")
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                            .executes(ctx -> runVirtualSession(ctx.getSource(),
                                    Vec3Argument.getVec3(ctx, "pos"),
                                    ctx.getSource().getPlayerOrException(),
                                    null, null, DEF_COUNTDOWN, DEF_TARGET_HITS,
                                    DEF_AREA_WIDTH, DEF_AREA_HEIGHT, DEF_RADIUS, DEF_MAX_SPHERES, DEF_HEIGHT_OFFSET))
                            .then(Commands.argument("player", EntityArgument.player())
                                    .executes(ctx -> runVirtualSession(ctx.getSource(),
                                            Vec3Argument.getVec3(ctx, "pos"),
                                            EntityArgument.getPlayer(ctx, "player"),
                                            null, null, DEF_COUNTDOWN, DEF_TARGET_HITS,
                                            DEF_AREA_WIDTH, DEF_AREA_HEIGHT, DEF_RADIUS, DEF_MAX_SPHERES, DEF_HEIGHT_OFFSET))
                                    .then(Commands.argument("facing", StringArgumentType.word())
                                            .executes(ctx -> runVirtualSession(ctx.getSource(),
                                                    Vec3Argument.getVec3(ctx, "pos"),
                                                    EntityArgument.getPlayer(ctx, "player"),
                                                    parseDirection(StringArgumentType.getString(ctx, "facing")),
                                                    null, DEF_COUNTDOWN, DEF_TARGET_HITS,
                                                    DEF_AREA_WIDTH, DEF_AREA_HEIGHT, DEF_RADIUS, DEF_MAX_SPHERES, DEF_HEIGHT_OFFSET))
                                            .then(Commands.argument("mode", StringArgumentType.word())
                                                    .executes(ctx -> runVirtualSession(ctx.getSource(),
                                                            Vec3Argument.getVec3(ctx, "pos"),
                                                            EntityArgument.getPlayer(ctx, "player"),
                                                            parseDirection(StringArgumentType.getString(ctx, "facing")),
                                                            StringArgumentType.getString(ctx, "mode"),
                                                            DEF_COUNTDOWN, DEF_TARGET_HITS,
                                                            DEF_AREA_WIDTH, DEF_AREA_HEIGHT, DEF_RADIUS, DEF_MAX_SPHERES, DEF_HEIGHT_OFFSET))
                                                    .then(Commands.argument("countdown", IntegerArgumentType.integer(5, 300))
                                                            .executes(ctx -> runVirtualSession(ctx.getSource(),
                                                                    Vec3Argument.getVec3(ctx, "pos"),
                                                                    EntityArgument.getPlayer(ctx, "player"),
                                                                    parseDirection(StringArgumentType.getString(ctx, "facing")),
                                                                    StringArgumentType.getString(ctx, "mode"),
                                                                    IntegerArgumentType.getInteger(ctx, "countdown"),
                                                                    DEF_TARGET_HITS,
                                                                    DEF_AREA_WIDTH, DEF_AREA_HEIGHT, DEF_RADIUS, DEF_MAX_SPHERES, DEF_HEIGHT_OFFSET))
                                                            .then(Commands.argument("targethits", IntegerArgumentType.integer(1, 100))
                                                                    .executes(ctx -> runVirtualSession(ctx.getSource(),
                                                                            Vec3Argument.getVec3(ctx, "pos"),
                                                                            EntityArgument.getPlayer(ctx, "player"),
                                                                            parseDirection(StringArgumentType.getString(ctx, "facing")),
                                                                            StringArgumentType.getString(ctx, "mode"),
                                                                            IntegerArgumentType.getInteger(ctx, "countdown"),
                                                                            IntegerArgumentType.getInteger(ctx, "targethits"),
                                                                            DEF_AREA_WIDTH, DEF_AREA_HEIGHT, DEF_RADIUS, DEF_MAX_SPHERES, DEF_HEIGHT_OFFSET))
                                                                    .then(Commands.argument("areawidth", IntegerArgumentType.integer(1, 20))
                                                                            .then(Commands.argument("areaheight", IntegerArgumentType.integer(1, 20))
                                                                                    .executes(ctx -> runVirtualSession(ctx.getSource(),
                                                                                            Vec3Argument.getVec3(ctx, "pos"),
                                                                                            EntityArgument.getPlayer(ctx, "player"),
                                                                                            parseDirection(StringArgumentType.getString(ctx, "facing")),
                                                                                            StringArgumentType.getString(ctx, "mode"),
                                                                                            IntegerArgumentType.getInteger(ctx, "countdown"),
                                                                                            IntegerArgumentType.getInteger(ctx, "targethits"),
                                                                                            IntegerArgumentType.getInteger(ctx, "areawidth"),
                                                                                            IntegerArgumentType.getInteger(ctx, "areaheight"),
                                                                                            DEF_RADIUS, DEF_MAX_SPHERES, DEF_HEIGHT_OFFSET))))))))));

            // ── stop 子命令 ──
            var stopCmd = Commands.literal("stop")
                    .executes(ctx -> stopSession(ctx.getSource(),
                            ctx.getSource().getPlayerOrException()))
                    .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> stopSession(ctx.getSource(),
                                    EntityArgument.getPlayer(ctx, "player"))));

            // ── set 子命令（配置方块参数） ──
            var setCmd = Commands.literal("set")
                    .then(Commands.argument("pos", Vec3Argument.vec3())
                            .then(Commands.literal("facing")
                                    .then(Commands.argument("direction", StringArgumentType.word())
                                            .executes(ctx -> setFacing(ctx.getSource(),
                                                    BlockPos.containing(Vec3Argument.getVec3(ctx, "pos")),
                                                    StringArgumentType.getString(ctx, "direction")))))
                            .then(Commands.literal("mode")
                                    .then(Commands.argument("mode", StringArgumentType.word())
                                            .executes(ctx -> setMode(ctx.getSource(),
                                                    BlockPos.containing(Vec3Argument.getVec3(ctx, "pos")),
                                                    StringArgumentType.getString(ctx, "mode")))))
                            .then(Commands.literal("countdown")
                                    .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 300))
                                            .executes(ctx -> setCountdown(ctx.getSource(),
                                                    BlockPos.containing(Vec3Argument.getVec3(ctx, "pos")),
                                                    IntegerArgumentType.getInteger(ctx, "seconds")))))
                            .then(Commands.literal("targethits")
                                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 100))
                                            .executes(ctx -> setTargetHits(ctx.getSource(),
                                                    BlockPos.containing(Vec3Argument.getVec3(ctx, "pos")),
                                                    IntegerArgumentType.getInteger(ctx, "count")))))
                            .then(Commands.literal("area")
                                    .then(Commands.argument("width", IntegerArgumentType.integer(1, 20))
                                            .then(Commands.argument("height", IntegerArgumentType.integer(1, 20))
                                                    .executes(ctx -> setArea(ctx.getSource(),
                                                            BlockPos.containing(Vec3Argument.getVec3(ctx, "pos")),
                                                            IntegerArgumentType.getInteger(ctx, "width"),
                                                            IntegerArgumentType.getInteger(ctx, "height"))))))
                            .then(Commands.literal("radius")
                                    .then(Commands.argument("radius", FloatArgumentType.floatArg(0.1f, 2.0f))
                                            .executes(ctx -> setRadius(ctx.getSource(),
                                                    BlockPos.containing(Vec3Argument.getVec3(ctx, "pos")),
                                                    FloatArgumentType.getFloat(ctx, "radius")))))
                            .then(Commands.literal("maxspheres")
                                    .then(Commands.argument("count", IntegerArgumentType.integer(1, 20))
                                            .executes(ctx -> setMaxSpheres(ctx.getSource(),
                                                    BlockPos.containing(Vec3Argument.getVec3(ctx, "pos")),
                                                    IntegerArgumentType.getInteger(ctx, "count")))))
                            .then(Commands.literal("height")
                                    .then(Commands.argument("offset", IntegerArgumentType.integer(1, 10))
                                            .executes(ctx -> setHeight(ctx.getSource(),
                                                    BlockPos.containing(Vec3Argument.getVec3(ctx, "pos")),
                                                    IntegerArgumentType.getInteger(ctx, "offset"))))));

            dispatcher.register(Commands.literal("aimlabs")
                    .requires(cs -> cs.hasPermission(2))
                    .then(startCmd)
                    .then(runCmd)
                    .then(stopCmd)
                    .then(setCmd));
        });
    }

    // ── 方向解析 ──

    /** 解析方向字符串（支持 north/south/east/west 及 n/s/e/w 缩写），失败返回 null。 */
    private static Direction parseDirection(String str) {
        if (str == null) return null;
        return switch (str.toLowerCase()) {
            case "north", "n" -> Direction.NORTH;
            case "south", "s" -> Direction.SOUTH;
            case "east", "e" -> Direction.EAST;
            case "west", "w" -> Direction.WEST;
            default -> null;
        };
    }

    /** 解析模式字符串，失败返回 null。 */
    private static AimlabsBlockEntity.GameMode parseMode(String str) {
        if (str == null) return null;
        try {
            return AimlabsBlockEntity.GameMode.valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // ── start（需要方块） ──

    private static int startSession(CommandSourceStack source, BlockPos pos, ServerPlayer player, Direction facing) {
        if (!(source.getLevel() instanceof ServerLevel serverLevel)) return 0;

        BlockEntity be = serverLevel.getBlockEntity(pos);
        if (!(be instanceof AimlabsBlockEntity aimBe)) {
            source.sendFailure(Component.literal("该位置没有 Aimlabs 方块"));
            return 0;
        }

        AimlabsSession existing = AimlabsSession.getPlayerSession(player);
        if (existing != null) {
            existing.end();
        }

        Direction actualFacing = (facing != null) ? facing : aimBe.facing;
        AimlabsBlock.startSession(player, pos, serverLevel, aimBe, actualFacing);

        source.sendSuccess(() -> Component.literal(
                "已为 " + player.getName().getString() + " 启动练习（朝向: " + actualFacing.getName() + "）"), true);
        return 1;
    }

    // ── run（无需方块，虚拟会话） ──

    private static int runVirtualSession(CommandSourceStack source,
            net.minecraft.world.phys.Vec3 posVec, ServerPlayer player,
            Direction facing, String modeStr,
            int countdown, int targetHits,
            int areaWidth, int areaHeight,
            float radius, int maxSpheres, int heightOffset) {
        if (!(source.getLevel() instanceof ServerLevel serverLevel)) return 0;

        // 结束已有会话
        AimlabsSession existing = AimlabsSession.getPlayerSession(player);
        if (existing != null) {
            existing.end();
        }

        // 解析模式
        AimlabsBlockEntity.GameMode mode = AimlabsBlockEntity.GameMode.TIMED;
        if (modeStr != null) {
            AimlabsBlockEntity.GameMode parsed = parseMode(modeStr);
            if (parsed == null) {
                source.sendFailure(Component.literal("无效模式，可选: timed, count"));
                return 0;
            }
            mode = parsed;
        }

        // 默认朝向
        Direction actualFacing = (facing != null) ? facing : Direction.SOUTH;
        BlockPos pos = BlockPos.containing(posVec);

        // 创建虚拟会话
        AimlabsSession session = new AimlabsSession(
                player, pos, serverLevel,
                mode, countdown, targetHits,
                areaWidth, areaHeight,
                radius, maxSpheres, heightOffset,
                actualFacing, true);
        session.start();
        AimlabsSession.giveOrRemovePracticeRevolver(player, true);

        String modeName = mode == AimlabsBlockEntity.GameMode.COUNT ? "计次" : "计时";
        source.sendSuccess(() -> Component.literal(
                "已为 " + player.getName().getString() + " 启动虚拟练习（"
                        + modeName + ", 朝向: " + actualFacing.getName() + "）"), true);
        return 1;
    }

    // ── stop ──

    private static int stopSession(CommandSourceStack source, ServerPlayer player) {
        AimlabsSession session = AimlabsSession.getPlayerSession(player);
        if (session == null) {
            source.sendFailure(Component.literal(
                    player.getName().getString() + " 没有活跃的练习会话"));
            return 0;
        }
        session.end();
        source.sendSuccess(() -> Component.literal(
                "已关闭 " + player.getName().getString() + " 的练习"), true);
        return 1;
    }

    // ── set 配置逻辑 ──

    private static AimlabsBlockEntity getBlockEntity(CommandSourceStack source, BlockPos pos) {
        if (!(source.getLevel() instanceof ServerLevel level)) return null;
        if (!level.isLoaded(pos)) return null;
        if (level.getBlockEntity(pos) instanceof AimlabsBlockEntity be) return be;
        return null;
    }

    private static int setFacing(CommandSourceStack source, BlockPos pos, String dirStr) {
        var be = getBlockEntity(source, pos);
        if (be == null) {
            source.sendFailure(Component.literal("该位置没有 Aimlabs 方块"));
            return 0;
        }
        Direction dir = parseDirection(dirStr);
        if (dir == null) {
            source.sendFailure(Component.literal("无效朝向，可选: north, south, east, west (或 n/s/e/w)"));
            return 0;
        }
        be.facing = dir;
        be.setChanged();
        source.sendSuccess(() -> Component.literal("朝向设为 " + dir.getName()), true);
        return 1;
    }

    private static int setMode(CommandSourceStack source, BlockPos pos, String modeStr) {
        var be = getBlockEntity(source, pos);
        if (be == null) {
            source.sendFailure(Component.literal("该位置没有 Aimlabs 方块"));
            return 0;
        }
        AimlabsBlockEntity.GameMode mode = parseMode(modeStr);
        if (mode == null) {
            source.sendFailure(Component.literal("无效模式，可选: timed, count"));
            return 0;
        }
        be.mode = mode;
        be.setChanged();
        String modeName = mode == AimlabsBlockEntity.GameMode.COUNT ? "计次" : "计时";
        source.sendSuccess(() -> Component.literal("模式设为 " + modeName), true);
        return 1;
    }

    private static int setCountdown(CommandSourceStack source, BlockPos pos, int seconds) {
        var be = getBlockEntity(source, pos);
        if (be == null) { source.sendFailure(Component.literal("该位置没有 Aimlabs 方块")); return 0; }
        be.countdownSeconds = seconds;
        be.setChanged();
        source.sendSuccess(() -> Component.literal("倒计时设为 " + seconds + " 秒"), true);
        return 1;
    }

    private static int setTargetHits(CommandSourceStack source, BlockPos pos, int count) {
        var be = getBlockEntity(source, pos);
        if (be == null) { source.sendFailure(Component.literal("该位置没有 Aimlabs 方块")); return 0; }
        be.targetHits = count;
        be.setChanged();
        source.sendSuccess(() -> Component.literal("目标击中次数设为 " + count), true);
        return 1;
    }

    private static int setArea(CommandSourceStack source, BlockPos pos, int width, int height) {
        var be = getBlockEntity(source, pos);
        if (be == null) { source.sendFailure(Component.literal("该位置没有 Aimlabs 方块")); return 0; }
        be.areaWidth = width;
        be.areaHeight = height;
        be.setChanged();
        source.sendSuccess(() -> Component.literal("区域设为 " + width + "x" + height), true);
        return 1;
    }

    private static int setRadius(CommandSourceStack source, BlockPos pos, float radius) {
        var be = getBlockEntity(source, pos);
        if (be == null) { source.sendFailure(Component.literal("该位置没有 Aimlabs 方块")); return 0; }
        be.sphereRadius = radius;
        be.setChanged();
        source.sendSuccess(() -> Component.literal("球体半径设为 " + radius), true);
        return 1;
    }

    private static int setMaxSpheres(CommandSourceStack source, BlockPos pos, int count) {
        var be = getBlockEntity(source, pos);
        if (be == null) { source.sendFailure(Component.literal("该位置没有 Aimlabs 方块")); return 0; }
        be.maxSpheres = count;
        be.setChanged();
        source.sendSuccess(() -> Component.literal("最大球体数设为 " + count), true);
        return 1;
    }

    private static int setHeight(CommandSourceStack source, BlockPos pos, int offset) {
        var be = getBlockEntity(source, pos);
        if (be == null) { source.sendFailure(Component.literal("该位置没有 Aimlabs 方块")); return 0; }
        be.heightOffset = offset;
        be.setChanged();
        source.sendSuccess(() -> Component.literal("靶标高度设为 " + offset + " 格"), true);
        return 1;
    }
}
