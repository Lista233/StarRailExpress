package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;

import org.agmas.harpymodloader.Harpymodloader;
import org.agmas.harpymodloader.SREDisableManager;
import org.agmas.harpymodloader.commands.RoleCountManager;
import org.agmas.harpymodloader.events.GameInitializeEvent;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.config.NoellesRolesConfig;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.innocence.DisabledPatientRoleData;
import org.agmas.noellesroles.game.command_block.DreadlordRole;
import org.agmas.noellesroles.utils.RoleUtils;
import org.agmas.noellesroles.game.modifier.NRModifiers;
import org.agmas.noellesroles.packet.CommandBlockProgressS2CPacket;
import net.exmo.sre.subtitle.SubtitleS2CPayload;
import io.wifi.starrailexpress.cca.SREGameTimeComponent;
import io.wifi.starrailexpress.cca.SRERoleDataPlayerComponent;
import org.agmas.noellesroles.api.time.TimeRewind;
import org.agmas.noellesroles.api.time.TimeRewindAreaSnapshot;
import org.agmas.noellesroles.api.time.TimeRewindOptions;
import org.agmas.noellesroles.api.time.TimeRewindSnapshot;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.component.DeathPenaltyComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import io.wifi.starrailexpress.api.GameMode;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.AreasWorldComponent;
import io.wifi.starrailexpress.cca.SREGameRoundEndComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.cca.SREPlayerShopComponent;
import io.wifi.starrailexpress.cca.SREWorldBlackoutComponent;
import io.wifi.starrailexpress.event.AllowPlayerDeath;
import io.wifi.starrailexpress.event.AllowPlayerDeathWithKiller;
import io.wifi.starrailexpress.event.OnGameEnd;
import io.wifi.starrailexpress.event.OnGameTrueStarted;
import io.wifi.starrailexpress.event.OnGameStarted;
import io.wifi.starrailexpress.game.modes.funny.SRERoleRotationGameMode;
import io.wifi.starrailexpress.game.modes.funny.SRERoleRotationSingleSelectGameMode;
import io.wifi.starrailexpress.game.modes.funny.SREVolunteerOpenSelectGameMode;
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.util.Scheduler;
import io.wifi.starrailexpress.util.TrueFalseResult;
import io.wifi.utils.RandomSelector;
import io.wifi.starrailexpress.network.PacketTracker;
import net.exmo.sre.camera.AdvancedCameraNode;
import net.exmo.sre.camera.AdvancedCameraPayload;
import net.exmo.sre.camera.AdvancedCameraSequence;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.exmo.sre.camera.AdvancedCameraCommand;
import org.agmas.noellesroles.content.entity.CommandBlockMarkerEntity;
import org.agmas.noellesroles.init.ModEntities;

/**
 * 命令方块不是玩家职业。它占满杀手名额，把平民标成异常，再按分钟改写成池内杀手。
 */
public final class CommandBlockDirector {
    private static final int CONVERT_INTERVAL = 60 * 20;
    private static final int EVENT_INTERVAL = 120 * 20;
    private static final int ANCHOR_LEAD = 30 * 20;
    private static final double ANCHOR_HOLD = 6.0D;
    private static boolean registered;
    private static boolean occupySlots;
    private static boolean suppressOfficialRoll;
    private static Session session;

    private CommandBlockDirector() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;
        HimRole.registerSkill();
        SteveRole.registerSkill();
        Error404Role.registerSkill();
        Entity303Role.registerSkill();
        EntityNullRole.registerSkill();
        FarlandsRole.registerSkill();
        WhiteEyesRole.registerSkill();
        StringerRole.registerSkill();
        PaperDollRole.registerSkill();
        FogGuestRole.registerSkill();
        PendulumRole.registerSkill();
        CandleShadeRole.registerSkill();
        DreadlordRole.registerSkill();
        CommandBlockCracks.registerAim();
        GameInitializeEvent.EVENT.register(CommandBlockDirector::prepare);
        OnGameTrueStarted.EVENT.register(CommandBlockDirector::onTrueStart);
        OnGameStarted.EVENT.register(CommandBlockDirector::onGameStarted);
        OnGameEnd.EVENT.register((level, game) -> clear(level));
        ServerTickEvents.END_WORLD_TICK.register(CommandBlockDirector::tick);
        net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register(
                (handler, server) -> Error404Role.disconnected(handler.player));
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) ->
                Error404Role.isHighDimension(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hit) ->
                Error404Role.isHighDimension(player) ? InteractionResult.FAIL : InteractionResult.PASS);
        net.fabricmc.fabric.api.event.player.UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (Error404Role.isHighDimension(player) && !stack.is(ModItems.ERROR_TERMINAL)
                    && !stack.is(ModItems.ERROR_DETONATOR) && !stack.is(ModItems.ERROR_ACCELERATOR)) {
                return net.minecraft.world.InteractionResultHolder.fail(stack);
            }
            return net.minecraft.world.InteractionResultHolder.pass(stack);
        });
        AllowPlayerDeath.EVENT.register((player, reason) -> CommandBlockCombat.allowScriptDeath(player, null, reason,
                GameUtils.isForcedDeathInProgress()));
        AllowPlayerDeathWithKiller.EVENT.register((player, killer, reason) -> CommandBlockCombat.allowScriptDeath(player,
                killer, reason, GameUtils.isForcedDeathInProgress()));
        OnKillPlayerTriggered.EVENT.register((victim, spawnBody, killer, reason, force) -> {
            if (killer instanceof ServerPlayer attacker && victim instanceof ServerPlayer dead
                    && CommandBlockRules.isPoolRole(SREGameWorldComponent.KEY.get(attacker.level()).getRole(attacker))) {
                CommandBlockCombat.onKillerDeath(attacker, dead);
            }
            if (victim instanceof ServerPlayer dead && CommandBlockRules.isPoolRole(
                    SREGameWorldComponent.KEY.get(dead.level()).getRole(dead))) {
                addDestruction(dead.serverLevel());
            }
            return TrueFalseResult.PASS;
        });
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(CommandBlockCombat::allowDamage);
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (player instanceof ServerPlayer serverPlayer && Error404Role.tryControl(serverPlayer, entity)) {
                return InteractionResult.SUCCESS;
            }
            return InteractionResult.PASS;
        });
    }

    public static int chance() {
        if (suppressOfficialRoll) {
            return 0;
        }
        return Math.max(0, NoellesRolesConfig.instance().commandBlockEnableChance);
    }

    public static boolean occupiesKillerSlots() {
        return occupySlots;
    }

    public static boolean isActive(ServerLevel level) {
        return session != null && session.active && session.levelKey.equals(level.dimension().location());
    }

    public static boolean holdsTheRound(ServerLevel level) {
        return occupySlots && session != null && !session.ended
                && session.levelKey.equals(level.dimension().location());
    }

    public static GameUtils.WinStatus filterEnd(ServerLevel level, GameUtils.WinStatus proposed,
            GameUtils.WinStatus result) {
        if (!holdsTheRound(level)) {
            return result;
        }
        GameUtils.WinStatus effective = result == null || result == GameUtils.WinStatus.NOT_MODIFY ? proposed : result;
        if (effective == GameUtils.WinStatus.KILLERS || effective == GameUtils.WinStatus.PASSENGERS) {
            return GameUtils.WinStatus.NONE;
        }
        // 平滑回溯会临时把玩家切成旁观者，不能把这段保护期算作全员离场。
        if (effective == GameUtils.WinStatus.NO_PLAYER
                && level.players().stream().anyMatch(TimeRewind::isSmoothRewinding)) {
            return GameUtils.WinStatus.NONE;
        }
        return result;
    }

    public static boolean forceNextRound(ServerLevel level) {
        if (level != null && SREGameWorldComponent.KEY.get(level).isRunning()
                && !supports(SREGameWorldComponent.KEY.get(level).getGameMode())) {
            return false;
        }
        return ModRoles.COMMAND_BLOCK.forceEventEnableNextRound();
    }

    public static void onEventRollResult(ServerLevel level, boolean enabled) {
        suppressOfficialRoll = false;
        if (!supports(SREGameWorldComponent.KEY.get(level).getGameMode())) {
            if (ModRoles.COMMAND_BLOCK.wasEventForceEnabled()) {
                ModRoles.COMMAND_BLOCK.forceEventEnableNextRound();
            }
            occupySlots = false;
            session = null;
            return;
        }
        if (session != null && session.levelKey.equals(level.dimension().location())) {
            if (!session.active) {
                start(level);
            }
            return;
        }
        if (enabled) {
            start(level);
        }
    }

    public static void onEventRoundEnd(ServerLevel level) {
        clear(level);
    }

    public static void scheduleKill(ServerPlayer victim, ServerPlayer killer, ResourceLocation reason, int delay) {
        if (session == null || victim == null || killer == null) {
            return;
        }
        session.pendingKills.add(new PendingKill(victim.getUUID(), killer.getUUID(), reason,
                victim.level().getGameTime() + delay));
    }

    private static void onTrueStart(ServerLevel level) {
        if (session != null && !session.active && session.levelKey.equals(level.dimension().location())) {
            start(level);
        }
    }

    private static void prepare(ServerLevel level, SREGameWorldComponent game, List<ServerPlayer> players) {
        discardMarkers(level);
        occupySlots = false;
        suppressOfficialRoll = false;
        session = null;
        CommandBlockCombat.clear();
        Error404Role.clear(level);
        if (!supports(game.getGameMode()) || SREDisableManager.isRoleDisabled(ModRoles.COMMAND_BLOCK)
                || Noellesroles.isRoleDisabled(ModRoles.COMMAND_BLOCK)) {
            suppressOfficialRoll = true;
            return;
        }
        int original = RoleCountManager.getKillerCount(players.size());
        boolean forced = ModRoles.COMMAND_BLOCK.isEventForcePending();
        boolean rolled = forced || RandomSelector.tryChance(chance(), 10000);
        if (!rolled) {
            suppressOfficialRoll = true;
            return;
        }
        occupySlots = true;
        session = new Session(level.dimension().location(), Math.max(1, original));
        if (!forced) {
            ModRoles.COMMAND_BLOCK.forceEventEnableNextRound();
        }
        Harpymodloader.FORCED_MODDED_ROLE.entrySet().removeIf(entry -> CommandBlockRules.isExcluded(entry.getValue()));
    }

    public static boolean replacesOpeningIntro(ServerLevel level) {
        return session != null && !session.ended && session.levelKey.equals(level.dimension().location());
    }

    private static void onGameStarted(ServerLevel level) {
        if (!replacesOpeningIntro(level) || defersIntro(level)) {
            return;
        }
        beginIntro(level);
    }

    private static boolean defersIntro(ServerLevel level) {
        GameMode mode = SREGameWorldComponent.KEY.get(level).getGameMode();
        return mode instanceof SRERoleRotationGameMode
                || mode instanceof SRERoleRotationSingleSelectGameMode
                || mode instanceof SREVolunteerOpenSelectGameMode;
    }

    private static void beginIntro(ServerLevel level) {
        if (session == null || session.introPlayed || session.ended) {
            return;
        }
        spawnMarker(level);
        session.introPlayed = true;
        syncProgress(level);
        // 和飞机开场一样，先让区块及实体生成包同步到客户端，再切入远景。
        Session introSession = session;
        Scheduler.schedule(() -> {
            if (session == introSession && replacesOpeningIntro(level)
                    && SREGameWorldComponent.KEY.get(level).isRunning()) {
                sendIntro(level);
            }
        }, 6);
    }

    private static void sendIntro(ServerLevel level) {
        if (session == null || session.ended || !session.levelKey.equals(level.dimension().location())) {
            return;
        }
        announce(level);
        var entity = session.markerId < 0 ? null : level.getEntity(session.markerId);
        if (!(entity instanceof CommandBlockMarkerEntity marker) || !marker.isAlive()) {
            for (ServerPlayer player : level.players()) {
                if (player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE) {
                    AdvancedCameraCommand.sendIntro(player, AdvancedCameraCommand.DEFAULT_INTRO_DURATION,
                            AdvancedCameraCommand.DEFAULT_INTRO_DISTANCE, AdvancedCameraCommand.DEFAULT_INTRO_HEIGHT);
                }
            }
            return;
        }
        Vec3 focus = marker.position();
        for (ServerPlayer player : level.players()) {
            if (player.gameMode.getGameModeForPlayer() != GameType.ADVENTURE) {
                continue;
            }
            PacketTracker.sendToClient(player, AdvancedCameraPayload.play(markerIntro(player, focus).toJson()));
            player.playNotifySound(SoundEvents.END_PORTAL_SPAWN, SoundSource.MASTER, 0.55F, 0.75F);
        }
    }

    /** 替代默认开场：先看天上的命令方块，再回到玩家眼睛。 */
    private static AdvancedCameraSequence markerIntro(ServerPlayer player, Vec3 focus) {
        Vec3 eye = player.getEyePosition();
        Vec3 flat = new Vec3(eye.x - focus.x, 0.0D, eye.z - focus.z);
        if (flat.lengthSqr() < 1.0D) {
            flat = new Vec3(0.0D, 0.0D, 1.0D);
        }
        flat = flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0.0D, flat.x);
        Vec3 far = focus.add(flat.scale(-28.0D)).add(0.0D, 16.0D, 0.0D);
        Vec3 near = focus.add(side.scale(18.0D)).add(flat.scale(-10.0D)).add(0.0D, 6.0D, 0.0D);
        double yawRad = Math.toRadians(player.getYRot());
        Vec3 overlook = eye.add(Math.sin(yawRad) * 36.0D, 22.0D, -Math.cos(yawRad) * 36.0D);
        List<AdvancedCameraNode> nodes = new ArrayList<>();
        nodes.add(new AdvancedCameraNode(0, 16, far, focus, null, null, 52.0F));
        nodes.add(new AdvancedCameraNode(44, 12, near, focus, null, null, 60.0F));
        nodes.add(new AdvancedCameraNode(28, 0, overlook, focus, null, null, 70.0F));
        nodes.add(new AdvancedCameraNode(20, 0, eye, null, player.getYRot(), player.getXRot(), 0.0F));
        return new AdvancedCameraSequence(nodes, true, true, false);
    }

    private static void start(ServerLevel level) {
        if (session != null && session.active && session.levelKey.equals(level.dimension().location())) {
            return;
        }
        if (session == null || !session.levelKey.equals(level.dimension().location())) {
            int original = RoleCountManager.getKillerCount(level.players().size());
            session = new Session(level.dimension().location(), Math.max(1, original));
        }
        session.active = true;
        occupySlots = true;
        session.quota = CommandBlockRules.markedCount(session.originalKillers);
        markAbnormals(level);
        assignSteve(level);
        scheduleNextConversion(level, level.getGameTime());
        session.nextEventAt = level.getGameTime() + EVENT_INTERVAL;
        session.pendingEvent = pickEvent(level);
        armRewind(level.getGameTime());
        tellAbnormals(level, Component.translatable("message.noellesroles.command_block.event_soon",
                Component.translatable(session.pendingEvent.key())));
        spawnMarker(level);
        session.nextCrackAt = level.getGameTime() + CommandBlockCracks.rollDelay(level);
        syncProgress(level);
        if (!session.introPlayed) {
            beginIntro(level);
        }

    }

    private static void tick(ServerLevel level) {
        boolean held = SREGameWorldComponent.KEY.get(level).isRunning() && holdsTheRound(level);
        if (!held) {
            if (level.getGameTime() % 40L == 0L) {
                discardMarkers(level);
            }
            return;
        }
        if (!session.active) {
            return;
        }
        long now = level.getGameTime();
        spinMarker(level);
        tickConversions(level, now);
        tickEvents(level, now);
        tickPendingKills(level, now);
        SteveBuilder.tick(level);
        CommandBlockCombat.tick(level);
        if (session.jumpRestoreAt > 0 && now >= session.jumpRestoreAt) {
            session.jumpRestoreAt = 0;
            SREGameWorldComponent.KEY.get(level)
                    .setJumpAvailable(AreasWorldComponent.KEY.get(level).areasSettings.canJump);
        }
        if (now >= session.nextCrackAt) {
            CommandBlockCracks.spawnOne(level);
            session.nextCrackAt = now + CommandBlockCracks.rollDelay(level);
        }
        if (now % 40L == 0L) {
            syncProgress(level);
        }
    }

    private static void tickConversions(ServerLevel level, long now) {
        if (session.previewed && session.nextConvert != null) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.nextConvert);
            if (player == null || !GameUtils.isPlayerAliveAndSurvival(player)) {
                session.queue.remove(session.nextConvert);
                session.nextConvert = null;
                session.nextRole = null;
                session.previewed = false;
                session.nextWarnAt = 0;
                session.nextConvertAt = 0;
                scheduleNextConversion(level, now);
                return;
            }
        }
        if (session.nextConvertAt > 0 && now >= session.nextConvertAt) {
            convertNext(level);
            scheduleNextConversion(level, now);
        }
    }

    private static void scheduleNextConversion(ServerLevel level, long now) {
        session.nextConvert = null;
        session.nextConvertAt = 0;
        session.nextWarnAt = 0;
        session.nextRole = null;
        session.previewed = false;
        while (!session.queue.isEmpty()) {
            UUID id = session.queue.get(0);
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null && GameUtils.isPlayerAliveAndSurvival(player)
                    && !CommandBlockRules.isPoolRole(SREGameWorldComponent.KEY.get(level).getRole(player))) {
                session.nextConvert = id;
                session.nextConvertAt = now + CONVERT_INTERVAL;
                beginPreview(level, player);
                return;
            }
            session.queue.remove(0);
        }
    }

    /** 命令方块局开局把一名平民改成 Steve。已经有人是 Steve 时不重复改。 */
    private static void assignSteve(ServerLevel level) {
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        List<ServerPlayer> civilians = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            if (game.isRole(player, ModRoles.STEVE)) {
                org.agmas.noellesroles.init.RoleInitialItems.addInitialItemsForRole(player, ModRoles.STEVE);
                return;
            }
            SRERole role = game.getRole(player);
            if (role != null && role.isInnocent() && !role.isVigilanteTeam()
                    && !CommandBlockRules.isPoolRole(role) && !session.abnormals.contains(player.getUUID())) {
                civilians.add(player);
            }
        }
        if (civilians.isEmpty()) {
            return;
        }
        ServerPlayer chosen = civilians.get(level.random.nextInt(civilians.size()));
        RoleUtils.changeRoleAndSendWelcome(chosen, ModRoles.STEVE);
    }

    /** 拉杆：把下一次改写换成另一个还没变成杀手的异常，并重新抽职业。 */
    public static boolean rerollNext(ServerLevel level) {
        if (session == null || !session.active || session.nextConvert == null) {
            return false;
        }
        List<ServerPlayer> marked = new ArrayList<>();
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player) || !session.abnormals.contains(player.getUUID())) {
                continue;
            }
            SRERole role = game.getRole(player);
            if (role == null || CommandBlockRules.isPoolRole(role)) {
                continue;
            }
            marked.add(player);
        }
        if (marked.size() < 2) {
            return false;
        }
        marked.removeIf(player -> player.getUUID().equals(session.nextConvert));
        if (marked.isEmpty()) {
            return false;
        }
        ServerPlayer chosen = marked.get(level.random.nextInt(marked.size()));
        SRERole next = CommandBlockRules.pool().get(level.random.nextInt(CommandBlockRules.pool().size()));
        session.queue.remove(chosen.getUUID());
        session.queue.add(0, chosen.getUUID());
        session.nextConvert = chosen.getUUID();
        session.nextRole = next;
        session.previewed = true;
        if (!game.isRole(chosen, ModRoles.ABNORMAL)) {
            RoleUtils.changeRole(chosen, ModRoles.ABNORMAL, false, false, false, false, true);
            RoleUtils.addModifier(chosen, NRModifiers.ABNORMAL);
        }
        PacketTracker.sendToClient(chosen,
                new SubtitleS2CPayload(Component.translatable("message.noellesroles.command_block.preview",
                        next.getName()), Component.empty(), 80, 0xFFE7D4FF, false,
                        SubtitleS2CPayload.POS_TOP, false));
        return true;
    }

    private static void beginPreview(ServerLevel level, ServerPlayer player) {
        List<SRERole> pool = CommandBlockRules.pool();
        if (pool.isEmpty() || player == null) {
            return;
        }
        SRERole role = pool.get(level.random.nextInt(pool.size()));
        session.nextRole = role;
        session.previewed = true;
        session.nextWarnAt = 0;
        RoleUtils.changeRole(player, ModRoles.ABNORMAL, false, false, false, false, true);
        RoleUtils.addModifier(player, NRModifiers.ABNORMAL);
        PacketTracker.sendToClient(player,
                new SubtitleS2CPayload(Component.translatable("message.noellesroles.command_block.preview",
                        role.getName()), Component.empty(), 80, 0xFFE7D4FF, false,
                        SubtitleS2CPayload.POS_TOP, false));
    }

    private static void convertNext(ServerLevel level) {
        if (session.nextConvert == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(session.nextConvert);
        session.queue.remove(session.nextConvert);
        session.nextConvert = null;
        session.previewed = false;
        SRERole role = session.nextRole;
        session.nextRole = null;
        if (player == null || !GameUtils.isPlayerAliveAndSurvival(player) || Error404Role.isAssimilated(player)) {
            return;
        }
        if (role == null || !CommandBlockRules.isPoolRole(role)) {
            List<SRERole> pool = CommandBlockRules.pool();
            role = pool.get(level.random.nextInt(pool.size()));
        }
        RoleUtils.removeModifier(player, NRModifiers.ABNORMAL, true, true);
        RoleUtils.changeRoleAndSendWelcome(player, role);
        SREPlayerShopComponent shop = SREPlayerShopComponent.KEY.get(player);
        if (shop.balance < GameConstants.getMoneyStart()) {
            shop.setBalance(GameConstants.getMoneyStart());
        }
        player.displayClientMessage(Component.translatable("message.noellesroles.command_block.converted",
                role.getName()), false);
        announceBirth(level, role);
    }

    private static void announceBirth(ServerLevel level, SRERole role) {
        Component text = Component.translatable("message.noellesroles.command_block.born", role.getName());
        for (ServerPlayer player : level.players()) {
            PacketTracker.sendToClient(player, new SubtitleS2CPayload(text, Component.empty(), 80, 0xFFB44DFF,
                    false, SubtitleS2CPayload.POS_TOP, false));
            player.playNotifySound(SoundEvents.EVOKER_CAST_SPELL, SoundSource.MASTER, 0.8F, 0.65F);
        }
    }

    private static void tickEvents(ServerLevel level, long now) {
        maybeSaveRewind(level, now);
        maybeSaveAnchor(level, now);
        if (now < session.nextEventAt || session.pendingEvent == null) {
            return;
        }
        fire(level, session.pendingEvent);
        session.pendingEvent = pickEvent(level);
        session.nextEventAt = now + EVENT_INTERVAL;
        armRewind(now);
        tellAbnormals(level, Component.translatable("message.noellesroles.command_block.event_soon",
                Component.translatable(session.pendingEvent.key())));
    }

    private static void fire(ServerLevel level, FieldEvent event) {
        switch (event) {
            case BLACKOUT -> SREWorldBlackoutComponent.KEY.get(level).triggerBlackout(true, 15 * 20);
            case CHAOS -> shuffle(level);
            case STATIC -> {
                for (ServerPlayer player : level.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player)) {
                        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 8 * 20, 0));
                        player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 8 * 20, 0));
                    }
                }
            }
            case REWIND -> applyRewind(level);
            case HUSH -> {
                for (ServerPlayer player : level.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player)) {
                        player.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, 20 * 20, 0));
                        player.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, 20 * 20, 0));
                    }
                }
            }
            case TRACE -> {
                for (ServerPlayer player : level.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player)) {
                        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 12 * 20, 0));
                    }
                }
            }
            case JAM -> {
                SREGameWorldComponent.KEY.get(level).setJumpAvailable(false);
                session.jumpRestoreAt = level.getGameTime() + 15 * 20;
            }
            case INJECT -> {
                for (ServerPlayer player : level.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player)) {
                        DisabledPatientRoleData.applyRandomTemporary(player, 12 * 20);
                    }
                }
            }
            case ANCHOR -> applyAnchor(level);
        }
        Component text = Component.translatable(event.firedKey()).withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer player : level.players()) {
            SRENetworkMessageUtils.sendBroadcast(player, text);
        }
    }
    private static void armRewind(long now) {
        session.rewindNodes.clear();
        session.rewindArea = null;
        session.rewindSaved = false;
        session.rewindSaveAt = 0;
        session.rewindTime = 0;
        session.anchorSpots.clear();
        session.anchorSaved = false;
        session.anchorSaveAt = 0;
        if (session.pendingEvent == FieldEvent.REWIND) {
            session.rewindSaveAt = Math.max(now, session.nextEventAt - 60 * 20);
        } else if (session.pendingEvent == FieldEvent.ANCHOR) {
            session.anchorSaveAt = Math.max(now, session.nextEventAt - ANCHOR_LEAD);
        }
    }

    private static void maybeSaveRewind(ServerLevel level, long now) {
        if (session.pendingEvent != FieldEvent.REWIND || session.rewindSaved || now < session.rewindSaveAt) {
            return;
        }
        session.rewindSaved = true;
        session.rewindNodes.clear();
        TimeRewindOptions options = TimeRewindOptions.builder()
                .excludeComponent(SRERoleDataPlayerComponent.KEY)
                .build();
        AABB box = null;
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            session.rewindNodes.put(player.getUUID(), TimeRewind.capture(player, options));
            AABB one = player.getBoundingBox().inflate(24.0D);
            box = box == null ? one
                    : new AABB(Math.min(box.minX, one.minX), Math.min(box.minY, one.minY), Math.min(box.minZ, one.minZ),
                            Math.max(box.maxX, one.maxX), Math.max(box.maxY, one.maxY), Math.max(box.maxZ, one.maxZ));
        }
        session.rewindArea = box == null ? null : TimeRewind.captureArea(level, box);
        session.rewindTime = SREGameTimeComponent.KEY.get(level).getTime();
        Component text = Component.translatable("message.noellesroles.command_block.event.rewind_saved")
                .withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer player : level.players()) {
            SRENetworkMessageUtils.sendBroadcast(player, text);
        }
    }

    private static void applyRewind(ServerLevel level) {
        if (!session.rewindSaved) {
            maybeSaveRewind(level, level.getGameTime());
        }
        for (Map.Entry<UUID, TimeRewindSnapshot> entry : session.rewindNodes.entrySet()) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(entry.getKey());
            // 节点之后死亡的人留在死亡状态，不因回溯复活。
            if (player == null || !GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            TimeRewind.smoothRestore(player, entry.getValue(), 40);
            // 回溯会把旁观者切回存活，先挂上死亡惩罚，避免这段时间看见职业。
            DeathPenaltyComponent.KEY.get(player).setPenalty(80, true);
        }
        if (session.rewindArea != null) {
            TimeRewind.restoreArea(level, session.rewindArea);
        }
        if (session.rewindTime > 0) {
            SREGameTimeComponent.KEY.get(level).setTime(session.rewindTime);
        }
        session.rewindNodes.clear();
        session.rewindArea = null;
        session.rewindSaved = false;
    }

    private static void maybeSaveAnchor(ServerLevel level, long now) {
        if (session.pendingEvent != FieldEvent.ANCHOR || session.anchorSaved || now < session.anchorSaveAt) {
            return;
        }
        session.anchorSaved = true;
        session.anchorSpots.clear();
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            session.anchorSpots.put(player.getUUID(), player.position());
        }
        Component text = Component.translatable("message.noellesroles.command_block.event.anchor_saved")
                .withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer player : level.players()) {
            SRENetworkMessageUtils.sendBroadcast(player, text);
        }
    }

    private static void applyAnchor(ServerLevel level) {
        if (!session.anchorSaved) {
            maybeSaveAnchor(level, level.getGameTime());
        }
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            Vec3 spot = session.anchorSpots.get(player.getUUID());
            if (spot == null) {
                continue;
            }
            if (player.distanceToSqr(spot) <= ANCHOR_HOLD * ANCHOR_HOLD) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 0));
                continue;
            }
            StandSpots.place(player, spot);
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 0));
            player.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20, 0));
        }
        session.anchorSpots.clear();
        session.anchorSaved = false;
    }

    private static void shuffle(ServerLevel level) {
        List<ServerPlayer> living = new ArrayList<>();
        for (ServerPlayer player : level.players()) {
            if (GameUtils.isPlayerAliveAndSurvival(player)) {
                living.add(player);
            }
        }
        if (living.size() < 2) {
            return;
        }
        List<Vec3> spots = new ArrayList<>();
        for (ServerPlayer player : living) {
            spots.add(player.position());
        }
        Collections.rotate(spots, 1 + level.random.nextInt(living.size() - 1));
        for (int i = 0; i < living.size(); i++) {
            Vec3 spot = spots.get(i);
            living.get(i).teleportTo(spot.x, spot.y, spot.z);
            living.get(i).addEffect(new MobEffectInstance(MobEffects.CONFUSION, 6 * 20, 0));
        }
    }

    private static FieldEvent pickEvent(ServerLevel level) {
        FieldEvent[] values = FieldEvent.values();
        return values[level.random.nextInt(values.length)];
    }

    private static void markAbnormals(ServerLevel level) {
        List<ServerPlayer> civilians = new ArrayList<>();
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        for (ServerPlayer player : level.players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                continue;
            }
            SRERole role = game.getRole(player);
            if (role != null && role.isInnocent() && !role.isVigilanteTeam() && !CommandBlockRules.isPoolRole(role)) {
                civilians.add(player);
            }
        }
        Collections.shuffle(civilians, new java.util.Random(level.getGameTime()));
        int n = Math.min(session.quota, civilians.size());
        for (int i = 0; i < n; i++) {
            session.abnormals.add(civilians.get(i).getUUID());
            session.queue.add(civilians.get(i).getUUID());
            RoleUtils.addModifier(civilians.get(i), NRModifiers.ABNORMAL);
        }
    }

    private static void tellAbnormals(ServerLevel level, Component message) {
        for (UUID id : session.abnormals) {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(id);
            if (player != null) {
                player.displayClientMessage(message, false);
            }
        }
    }

    public static void addDestruction(ServerLevel level) {
        if (!isActive(level) || session.ended) {
            return;
        }
        session.killerDeaths++;
        syncProgress(level);
        if (session.killerDeaths >= session.quota) {
            endByDestruction(level);
        }
    }

    private static void syncProgress(ServerLevel level) {
        if (level == null) {
            return;
        }
        boolean show = session != null && !session.ended
                && session.levelKey.equals(level.dimension().location())
                && (session.active || session.introPlayed);
        int progress = session == null ? 0 : session.killerDeaths;
        int goal = show ? Math.max(1, session.quota) : 0;
        CommandBlockProgressS2CPacket packet = new CommandBlockProgressS2CPacket(progress, goal);
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, packet);
        }
    }

    private static void endByDestruction(ServerLevel level) {
        if (session.ended) {
            return;
        }
        session.ended = true;
        Component text = Component.translatable("message.noellesroles.command_block.destroyed")
                .withStyle(ChatFormatting.DARK_PURPLE);
        for (ServerPlayer player : level.players()) {
            SRENetworkMessageUtils.sendBroadcast(player, text);
        }
        SREGameRoundEndComponent.KEY.get(level).setRoundEndData(new ArrayList<>(level.players()),
                GameUtils.WinStatus.PASSENGERS);
        GameUtils.stopGame(level);
    }

    private static void announce(ServerLevel level) {
        Component title = Component.translatable("message.noellesroles.command_block.title")
                .withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD);
        Component subtitle = Component.translatable("message.noellesroles.command_block.subtitle")
                .withStyle(ChatFormatting.GRAY);
        Component broadcast = Component.translatable("message.noellesroles.command_block.broadcast")
                .withStyle(ChatFormatting.LIGHT_PURPLE);
        for (ServerPlayer player : level.players()) {
            SRENetworkMessageUtils.sendTitleTime(player, 10, 80, 20);
            SRENetworkMessageUtils.sendTitle(player, title);
            SRENetworkMessageUtils.sendSubtitle(player, subtitle);
            SRENetworkMessageUtils.sendBroadcast(player, broadcast);
            player.playNotifySound(SoundEvents.BEACON_ACTIVATE, SoundSource.MASTER, 0.8F, 0.6F);
        }
    }

    private static void spawnMarker(ServerLevel level) {
        if (session.markerId >= 0 && level.getEntity(session.markerId) instanceof CommandBlockMarkerEntity existing
                && existing.isAlive()) {

            return;
        }
        Vec3 center = markerPos(level);
        level.getChunk(BlockPos.containing(center));
        CommandBlockMarkerEntity marker = new CommandBlockMarkerEntity(ModEntities.COMMAND_BLOCK_MARKER, level);
        marker.setPos(center.x, center.y, center.z);
        marker.setStage(CommandBlockRules.destructionStage(session.killerDeaths, session.quota));
        level.addFreshEntity(marker);
        session.markerId = marker.getId();
    }


    private static void spinMarker(ServerLevel level) {
        if (session.markerId < 0) {
            return;
        }
        if (!(level.getEntity(session.markerId) instanceof CommandBlockMarkerEntity marker)) {
            return;
        }
        int stage = CommandBlockRules.destructionStage(session.killerDeaths, session.quota);
        if (marker.getStage() != stage) {
            marker.setStage(stage);
        }
    }


    private static Vec3 markerPos(ServerLevel level) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        AABB play = areas.getPlayArea();
        // getSpawnPos() 是世界重生点，可能在大厅；和飞机事件一样按本局游玩区域定位。
        if (play != null && (play.getXsize() >= 2.0D || play.getZsize() >= 2.0D)) {
            Vec3 center = play.getCenter();
            return openSky(level, center.x, center.z, Mth.floor(play.minY));
        }
        Vec3 anchor = level.players().stream()
                .filter(player -> player.gameMode.getGameModeForPlayer() == GameType.ADVENTURE)
                .map(ServerPlayer::position)
                .findFirst()
                .orElseGet(() -> Vec3.atCenterOf(level.getSharedSpawnPos()));
        return openSky(level, anchor.x, anchor.z, Mth.floor(anchor.y));
    }

    /** 沿游玩区域中心那一列爬过屋顶，把方块中心放在最高实心方块上方。 */
    private static Vec3 openSky(ServerLevel level, double x, double z, int startY) {
        int bx = Mth.floor(x);
        int bz = Mth.floor(z);
        int minY = Math.max(level.getMinBuildHeight(), startY);
        int maxY = level.getMaxBuildHeight() - 8;
        int lastSolid = minY - 1;
        int limit = Math.min(maxY, minY + 128);
        for (int y = minY; y <= limit; y++) {
            var state = level.getBlockState(new BlockPos(bx, y, bz));
            if (!state.isAir() && !state.canBeReplaced()) {
                lastSolid = y;
            }
        }
        int place = lastSolid >= minY ? lastSolid + 18 : minY + 28;
        place = Mth.clamp(place, minY + 12, Math.max(minY + 12, maxY));
        return new Vec3(bx + 0.5D, place, bz + 0.5D);
    }

    private static void discardMarkers(ServerLevel level) {
        if (level == null) {
            return;
        }
        List<CommandBlockMarkerEntity> found = new ArrayList<>();
        for (var entity : level.getAllEntities()) {
            if (entity instanceof CommandBlockMarkerEntity marker) {
                found.add(marker);
            }
        }
        for (CommandBlockMarkerEntity marker : found) {
            if (!marker.isRemoved()) {
                marker.discard();
            }
        }
    }


    private static void tickPendingKills(ServerLevel level, long now) {
        Iterator<PendingKill> it = session.pendingKills.iterator();
        while (it.hasNext()) {
            PendingKill kill = it.next();
            if (now < kill.at) {
                continue;
            }
            it.remove();
            ServerPlayer victim = level.getServer().getPlayerList().getPlayer(kill.victim);
            ServerPlayer killer = level.getServer().getPlayerList().getPlayer(kill.killer);
            if (victim != null && GameUtils.isPlayerAliveAndSurvival(victim)) {
                CommandBlockCombat.tryKill(killer == null ? victim : killer, victim, kill.reason, false);
            }
        }
    }

    private static void clear(ServerLevel level) {
        occupySlots = false;
        suppressOfficialRoll = false;
        if (level != null) {
            for (ServerPlayer player : level.players()) {
                CommandBlockCombat.strip(player);
                RoleUtils.removeModifier(player, NRModifiers.ABNORMAL, true, true);
            }
            CommandBlockCracks.discard(level);
            CommandBlockProgressS2CPacket hide = new CommandBlockProgressS2CPacket(0, 0);
            for (ServerPlayer player : level.players()) {
                ServerPlayNetworking.send(player, hide);
            }
            // 封锁事件可能没到时间就结束对局，结束时无条件还原跳跃开关。
            SREGameWorldComponent.KEY.get(level)
                    .setJumpAvailable(AreasWorldComponent.KEY.get(level).areasSettings.canJump);
        }
        discardMarkers(level);
        session = null;
        CommandBlockCombat.clear();
        if (level != null) {
            Error404Role.clear(level);
        } else {
            Error404Role.clear();
        }
        SteveBuilder.clear();
    }

    private static boolean supports(GameMode mode) {
        if (!(mode instanceof io.wifi.starrailexpress.game.modes.SREMurderGameMode)) {
            return false;
        }
        String name = mode.getClass().getSimpleName();
        return !name.contains("HideAndSeek") && !name.contains("EvilWar") && !name.contains("TNT")
                && !name.contains("Gambler") && !name.contains("Discovery") && !name.contains("Goose");
    }

    private enum FieldEvent {
        BLACKOUT("message.noellesroles.command_block.event.blackout",
                "message.noellesroles.command_block.event.blackout_fired"),
        CHAOS("message.noellesroles.command_block.event.chaos",
                "message.noellesroles.command_block.event.chaos_fired"),
        STATIC("message.noellesroles.command_block.event.static",
                "message.noellesroles.command_block.event.static_fired"),
        REWIND("message.noellesroles.command_block.event.rewind",
                "message.noellesroles.command_block.event.rewind_fired"),
        HUSH("message.noellesroles.command_block.event.hush",
                "message.noellesroles.command_block.event.hush_fired"),
        ANCHOR("message.noellesroles.command_block.event.anchor",
                "message.noellesroles.command_block.event.anchor_fired"),
        TRACE("message.noellesroles.command_block.event.trace",
                "message.noellesroles.command_block.event.trace_fired"),
        JAM("message.noellesroles.command_block.event.jam",
                "message.noellesroles.command_block.event.jam_fired"),
        INJECT("message.noellesroles.command_block.event.inject",
                "message.noellesroles.command_block.event.inject_fired");

        private final String key;
        private final String fired;

        FieldEvent(String key, String fired) {
            this.key = key;
            this.fired = fired;
        }

        private String key() {
            return key;
        }

        private String firedKey() {
            return fired;
        }
    }

    private static final class Session {
        private final ResourceLocation levelKey;
        private final int originalKillers;
        private final List<UUID> abnormals = new ArrayList<>();
        private final List<UUID> queue = new ArrayList<>();
        private final List<PendingKill> pendingKills = new ArrayList<>();
        private boolean active;
        private boolean ended;
        private boolean introPlayed;
        private int quota;
        private int killerDeaths;
        private int markerId = -1;
        private long nextConvertAt;
        private long nextWarnAt;
        private long nextEventAt;
        private long nextCrackAt;
        private long jumpRestoreAt;
        private UUID nextConvert;
        private SRERole nextRole;
        private boolean previewed;
        private FieldEvent pendingEvent;
        private final Map<UUID, TimeRewindSnapshot> rewindNodes = new HashMap<>();
        private TimeRewindAreaSnapshot rewindArea;
        private int rewindTime;
        private long rewindSaveAt;
        private boolean rewindSaved;
        private final Map<UUID, Vec3> anchorSpots = new HashMap<>();
        private long anchorSaveAt;
        private boolean anchorSaved;

        private Session(ResourceLocation levelKey, int originalKillers) {
            this.levelKey = levelKey;
            this.originalKillers = originalKillers;
        }
    }

    private static final class PendingKill {
        private final UUID victim;
        private final UUID killer;
        private final ResourceLocation reason;
        private final long at;

        private PendingKill(UUID victim, UUID killer, ResourceLocation reason, long at) {
            this.victim = victim;
            this.killer = killer;
            this.reason = reason;
            this.at = at;
        }
    }
}
