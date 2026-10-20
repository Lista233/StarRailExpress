package org.agmas.noellesroles.game.modes.echo.command;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import org.agmas.noellesroles.game.modes.echo.result.EchoResultSnapshot;
import org.agmas.noellesroles.game.modes.echo.runtime.EchoSessions;

import java.util.Locale;

/** 固定管理入口；当前地图预检不接收自由路径，避免命令参数截断与目录越界。 */
public final class EchoCommand {
    private EchoCommand() { }

    public static void register() {
        EchoMapCommand.initialize();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> dispatcher.register(
                Commands.literal("sre:echo").requires(source -> source.hasPermission(2))
                        .then(Commands.literal("status").executes(ctx -> status(ctx.getSource())))
                        .then(Commands.literal("abort").executes(ctx -> finish(ctx.getSource(), EchoResultSnapshot.Outcome.VOID)))
                        .then(EchoMapCommand.tree())
                        .then(Commands.literal("reload").executes(ctx -> EchoMapTools.reload(ctx.getSource())))
                        .then(Commands.literal("debug").requires(source -> Boolean.getBoolean("sre.echo.development"))
                                .then(Commands.literal("evacuation").executes(ctx -> evacuation(ctx.getSource())))
                                .then(Commands.literal("carriers_win").executes(ctx -> finish(ctx.getSource(), EchoResultSnapshot.Outcome.CARRIERS)))
                                .then(Commands.literal("interceptors_win").executes(ctx -> finish(ctx.getSource(), EchoResultSnapshot.Outcome.INTERCEPTORS))))));
        CommandRegistrationCallback.EVENT.register((dispatcher,registry,environment) -> dispatcher.register(
                Commands.literal("sre:echo_drop").requires(source -> source.getEntity() instanceof net.minecraft.server.level.ServerPlayer)
                        .then(Commands.literal("cargo").executes(ctx -> drop(ctx.getSource(),false)))
                        .then(Commands.literal("key").executes(ctx -> drop(ctx.getSource(),true)))));
    }

    private static int status(CommandSourceStack source) {
        var match = EchoSessions.get(source.getLevel());
        if (match == null) return missing(source);
        source.sendSuccess(() -> Component.translatable("echo.command.status", match.id().toString(),
                Component.translatable("echo.phase." + match.phase().name().toLowerCase(Locale.ROOT)),
                match.matchTick(), match.playerCount()), false);
        return 1;
    }

    private static int finish(CommandSourceStack source, EchoResultSnapshot.Outcome outcome) {
        var match = EchoSessions.get(source.getLevel());
        if (match == null || match.result() != null) return missing(source);
        match.settle(outcome, outcome == EchoResultSnapshot.Outcome.VOID ? "ADMIN_ABORT" : "DEBUG_RESULT", true);
        source.sendSuccess(() -> Component.translatable("echo.command.completed"), true);
        return 1;
    }

    private static int evacuation(CommandSourceStack source) {
        var match = EchoSessions.get(source.getLevel());
        if (match == null || !match.requestDevelopmentEvacuation()) return missing(source);
        source.sendSuccess(() -> Component.translatable("echo.command.completed"), false);
        return 1;
    }

    private static int drop(CommandSourceStack source,boolean key) {
        if(!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player)) return missing(source);
        var match=EchoSessions.get(source.getLevel());
        if(match==null || !match.drop(player,key)) return missing(source);
        return 1;
    }
    private static int missing(CommandSourceStack source) {
        source.sendFailure(Component.translatable("echo.error.no_active_session"));
        return 0;
    }
}
