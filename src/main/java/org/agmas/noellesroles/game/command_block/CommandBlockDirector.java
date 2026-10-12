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
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.math.Transformation;

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
import io.wifi.starrailexpress.event.OnKillPlayerTriggered;
import io.wifi.starrailexpress.game.GameConstants;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.SRENetworkMessageUtils;
import io.wifi.starrailexpress.util.TrueFalseResult;
import io.wifi.utils.RandomSelector;
import net.exmo.sre.planecrash.PlaneCrashIntroPayload;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

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
        CommandBlockCracks.registerAim();
        GameInitializeEvent.EVENT.register(CommandBlockDirector::prepare);
        OnGameTrueStarted.EVENT.register(CommandBlockDirector::onTrueStart);
        OnGameEnd.EVENT.register((level, game) -> clear(level));
        ServerTickEvents.END_WORLD_TICK.register(CommandBlockDirector::tick);
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
        occupySlots = false;
        suppressOfficialRoll = false;
        session = null;
        CommandBlockCombat.clear();
        Error404Role.clear();
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
        scheduleNextConversion(level, level.getGameTime());
        session.nextEventAt = level.getGameTime() + EVENT_INTERVAL;
        session.pendingEvent = pickEvent(level);
        armRewind(level.getGameTime());
        tellAbnormals(level, Component.translatable("message.noellesroles.command_block.event_soon",
                Component.translatable(session.pendingEvent.key())));
        spawnMarker(level);
        session.nextCrackAt = level.getGameTime() + CommandBlockCracks.rollDelay(level);
        syncProgress(level);
        session.introAt = level.getGameTime() + 10;
        announce(level);
    }

    private static void tick(ServerLevel level) {
        if (!SREGameWorldComponent.KEY.get(level).isRunning()) {
            return;
        }
        if (session == null || !session.active || !session.levelKey.equals(level.dimension().location())) {
            return;
        }
        long now = level.getGameTime();
        if (!session.introPlayed && now >= session.introAt) {
            session.introPlayed = true;
            focus(level);
        }
        spinMarker(level);
        if (session.bossBar != null) {
            for (ServerPlayer player : level.players()) {
                session.bossBar.addPlayer(player);
            }
        }
        tickConversions(level, now);
        tickEvents(level, now);
        tickPendingKills(level, now);
        CommandBlockCombat.tick(level);
        if (now >= session.nextCrackAt) {
            CommandBlockCracks.spawnOne(level);
            session.nextCrackAt = now + CommandBlockCracks.rollDelay(level);
        }
        if (now % 40L == 0L) {
            syncProgress(level);
        }
        Error404Role.tick(level);
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
        SRENetworkMessageUtils.sendCODSubtitleToPlayer(player,
                Component.translatable("message.noellesroles.command_block.preview", role.getName()),
                Component.empty(), 80, 0xFFE7D4FF, false, SubtitleS2CPayload.POS_CENTER);
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
        if (player == null || !GameUtils.isPlayerAliveAndSurvival(player)) {
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
            SRENetworkMessageUtils.sendCODSubtitleToPlayer(player, text, Component.empty(), 80, 0xFFB44DFF, false,
                    SubtitleS2CPayload.POS_CENTER);
            player.playNotifySound(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.MASTER, 1.0F, 1.0F);
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
            case LEVITY -> {
                for (ServerPlayer player : level.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player)) {
                        player.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 40, 0));
                        player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0));
                    }
                }
            }
            case HUSH -> {
                for (ServerPlayer player : level.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player)) {
                        player.addEffect(new MobEffectInstance(ModEffects.VOICE_SILENCE, 8 * 20, 0));
                        player.addEffect(new MobEffectInstance(ModEffects.CHAT_BAN, 8 * 20, 0));
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
            if (player == null) {
                continue;
            }
            if (!GameUtils.isPlayerAliveAndSurvival(player)) {
                GameUtils.revivePlayer(player, player.getX(), player.getY(), player.getZ());
            }
            TimeRewind.smoothRestore(player, entry.getValue(), 40);
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
        updateBossBar();
        syncProgress(level);
        if (session.killerDeaths >= session.quota) {
            endByDestruction(level);
        }
    }

    private static void syncProgress(ServerLevel level) {
        if (level == null) {
            return;
        }
        int progress = session == null ? 0 : session.killerDeaths;
        int goal = session == null || !session.active || session.ended ? 0 : session.quota;
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
        Vec3 pos = markerPos(level);
        Display.BlockDisplay display = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level);
        display.setPos(pos.x, pos.y, pos.z);
        display.setBlockState(Blocks.COMMAND_BLOCK.defaultBlockState());
        level.addFreshEntity(display);
        session.markerId = display.getId();
        session.bossBar = new ServerBossEvent(
                Component.translatable("message.noellesroles.command_block.bossbar", 0, session.quota),
                BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
        session.bossBar.setProgress(0);
        for (ServerPlayer player : level.players()) {
            session.bossBar.addPlayer(player);
        }
    }

    private static void focus(ServerLevel level) {
        if (session.markerId < 0) {
            return;
        }
        PlaneCrashIntroPayload payload = new PlaneCrashIntroPayload(session.markerId, 90);
        for (ServerPlayer player : level.players()) {
            ServerPlayNetworking.send(player, payload);
        }
    }

    private static void spinMarker(ServerLevel level) {
        if (session.markerId < 0) {
            return;
        }
        if (!(level.getEntity(session.markerId) instanceof Display.BlockDisplay display)) {
            return;
        }
        session.angle += 0.03F;
        int stage = CommandBlockRules.destructionStage(session.killerDeaths, session.quota);
        display.setBlockState(stageBlock(stage));
        float size = 8.0F - stage;
        display.setTransformationInterpolationDuration(2);
        display.setTransformationInterpolationDelay(0);
        display.setTransformation(new Transformation(
                new Vector3f(-size / 2.0F, -size / 2.0F, -size / 2.0F),
                new Quaternionf().rotateY(session.angle),
                new Vector3f(size, size, size),
                new Quaternionf()));
        updateBossBar();
    }

    private static BlockState stageBlock(int stage) {
        return switch (stage) {
            case 1 -> Blocks.CHAIN_COMMAND_BLOCK.defaultBlockState();
            case 2 -> Blocks.REPEATING_COMMAND_BLOCK.defaultBlockState();
            case 3 -> Blocks.CRACKED_DEEPSLATE_BRICKS.defaultBlockState();
            default -> Blocks.COMMAND_BLOCK.defaultBlockState();
        };
    }

    private static void updateBossBar() {
        if (session == null || session.bossBar == null || session.quota <= 0) {
            return;
        }
        session.bossBar.setName(Component.translatable("message.noellesroles.command_block.bossbar",
                session.killerDeaths, session.quota));
        session.bossBar.setProgress(Math.min(1.0F, session.killerDeaths / (float) session.quota));
    }

    private static Vec3 markerPos(ServerLevel level) {
        AreasWorldComponent areas = AreasWorldComponent.KEY.get(level);
        if (areas.getSpawnPos() != null && areas.getSpawnPos().pos != null) {
            return areas.getSpawnPos().pos.add(0.0D, 28.0D, 0.0D);
        }
        ServerPlayer any = level.players().isEmpty() ? null : level.players().get(0);
        if (any == null) {
            return new Vec3(0, 80, 0);
        }
        return any.position().add(0, 28, 0);
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
        }
        if (session != null && session.bossBar != null) {
            session.bossBar.removeAllPlayers();
        }
        if (session != null && level != null && session.markerId >= 0) {
            var entity = level.getEntity(session.markerId);
            if (entity != null) {
                entity.discard();
            }
        }
        session = null;
        CommandBlockCombat.clear();
        Error404Role.clear();
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
        LEVITY("message.noellesroles.command_block.event.levity",
                "message.noellesroles.command_block.event.levity_fired"),
        HUSH("message.noellesroles.command_block.event.hush",
                "message.noellesroles.command_block.event.hush_fired"),
        ANCHOR("message.noellesroles.command_block.event.anchor",
                "message.noellesroles.command_block.event.anchor_fired");

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
        private float angle;
        private long introAt;
        private long nextConvertAt;
        private long nextWarnAt;
        private long nextEventAt;
        private long nextCrackAt;
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
        private ServerBossEvent bossBar;

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
