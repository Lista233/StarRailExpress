package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.utils.MCItemsUtils;

import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.disguise.EntityDisguise;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.index.TMMEntities;
import io.wifi.starrailexpress.util.ParticleFx;
import io.wifi.starrailexpress.util.ShopEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 瞄准同化，高维俯瞰，错误自爆剥夺空间。 */
public class Error404Role extends NormalRole {
    public static final int AIM_TICKS = 5 * 20;
    public static final int ASSIMILATE_TICKS = 15 * 20;
    public static final int MAX_ASSIMILATED = 2;
    public static final int WINDUP = 20;
    public static final int HIGH_DIM = 10 * 20;
    public static final int SKILL_COOLDOWN = 20;
    private static final Map<UUID, Aim> AIMS = new HashMap<>();
    private static final Map<UUID, Assimilation> ASSIMILATIONS = new HashMap<>();
    private static final Set<UUID> ASSIMILATED = new HashSet<>();
    private static final Map<UUID, UUID> ASSIMILATED_OWNER = new HashMap<>();
    private static final Map<UUID, Long> HIGH_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> WINDUP_UNTIL = new HashMap<>();
    private static final List<VoidZone> ZONES = new ArrayList<>();

    public Error404Role(net.minecraft.resources.ResourceLocation id, int color, boolean innocent, boolean killer,
            SRERole.MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.ERROR_404, RoleSkill.skill(
                SRE.id("error404_dimension"),
                "skill.noellesroles.error404.dimension",
                context -> beginDimension(context.player())).cooldownSeconds(SKILL_COOLDOWN).announceToSelf().showOnHud(true).build());
    }

    public static void clear() {
        AIMS.clear();
        ASSIMILATIONS.clear();
        ASSIMILATED.clear();
        ASSIMILATED_OWNER.clear();
        HIGH_UNTIL.clear();
        WINDUP_UNTIL.clear();
        ZONES.clear();
    }

    public static boolean isHighDimension(Player player) {
        if (player == null) {
            return false;
        }
        Long until = HIGH_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() < until;
    }

    public static boolean isAssimilated(Player player) {
        return player != null && ASSIMILATED.contains(player.getUUID());
    }

    public static boolean trySelfDestruct(ServerPlayer player) {
        if (!isAssimilated(player) || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        openVoid(player.serverLevel(), player.position(), player);
        GameUtils.killPlayer(player, false, player, Noellesroles.id("error_void"), true);
        forget(player.getUUID());
        return true;
    }

    public static boolean tryControl(ServerPlayer actor, net.minecraft.world.entity.Entity target) {
        if (!(target instanceof ServerPlayer victim) || !isHighDimension(actor) || !isAssimilated(victim)) {
            return false;
        }
        return trySelfDestruct(victim);
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        SREGameWorldComponent game = SREGameWorldComponent.KEY.get(level);
        tickAim(level, game, now);
        tickAssimilation(level, now);
        tickHighDimension(level, game, now);
        tickZones(level, now);
    }

    private static boolean beginDimension(ServerPlayer player) {
        if (player.isSpectator() || !GameUtils.isPlayerAliveAndSurvival(player)) {
            return false;
        }
        WINDUP_UNTIL.put(player.getUUID(), player.level().getGameTime() + WINDUP);
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WINDUP, 4, false, false, true));
        player.displayClientMessage(Component.translatable("message.noellesroles.error404.windup"), true);
        return true;
    }

    private static void tickAim(ServerLevel level, SREGameWorldComponent game, long now) {
        for (ServerPlayer hunter : level.players()) {
            if (!game.isRole(hunter, ModRoles.ERROR_404) || !GameUtils.isPlayerAliveAndSurvival(hunter)) {
                AIMS.remove(hunter.getUUID());
                continue;
            }
            if (countOwned(hunter.getUUID()) >= MAX_ASSIMILATED) {
                AIMS.remove(hunter.getUUID());
                continue;
            }
            List<ServerPlayer> seen = GazeRays.playersAlong(hunter, 24.0D, 0.2D);
            if (seen.isEmpty() || isAssimilated(seen.get(0)) || game.isRole(seen.get(0), ModRoles.ERROR_404)) {
                AIMS.remove(hunter.getUUID());
                continue;
            }
            ServerPlayer target = seen.get(0);
            Aim aim = AIMS.computeIfAbsent(hunter.getUUID(), id -> new Aim(target.getUUID(), now));
            if (!aim.target.equals(target.getUUID())) {
                aim = new Aim(target.getUUID(), now);
                AIMS.put(hunter.getUUID(), aim);
            }
            if (now - aim.started >= AIM_TICKS) {
                AIMS.remove(hunter.getUUID());
                startAssimilation(hunter, target, now);
            }
        }
    }

    private static void startAssimilation(ServerPlayer hunter, ServerPlayer target, long now) {
        if (countOwned(hunter.getUUID()) >= MAX_ASSIMILATED) {
            return;
        }
        ASSIMILATIONS.put(target.getUUID(), new Assimilation(hunter.getUUID(), now + ASSIMILATE_TICKS));
        target.displayClientMessage(Component.translatable("message.noellesroles.error404.becoming"), false);
        hunter.displayClientMessage(Component.translatable("message.noellesroles.error404.locked", target.getName()), true);
    }

    private static void tickAssimilation(ServerLevel level, long now) {
        Iterator<Map.Entry<UUID, Assimilation>> it = ASSIMILATIONS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Assimilation> entry = it.next();
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(entry.getKey());
            if (target == null || !GameUtils.isPlayerAliveAndSurvival(target)) {
                it.remove();
                continue;
            }
            int left = (int) ((entry.getValue().doneAt - now) / 20);
            if (now % 20 == 0) {
                target.displayClientMessage(Component.translatable("message.noellesroles.error404.assimilating", Math.max(0, left))
                        .withStyle(ChatFormatting.DARK_PURPLE), true);
            }
            if (now >= entry.getValue().doneAt) {
                Assimilation done = entry.getValue();
                it.remove();
                finishAssimilation(target, done.hunter);
            }
        }
    }

    private static void finishAssimilation(ServerPlayer target, UUID hunter) {
        if (countOwned(hunter) >= MAX_ASSIMILATED) {
            return;
        }
        ASSIMILATED.add(target.getUUID());
        ASSIMILATED_OWNER.put(target.getUUID(), hunter);
        EntityDisguise.disguise(target, TMMEntities.PURPLE_MONSTER, 20 * 60 * 20);
        target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 20 * 60, 0, false, false, true));
        MCItemsUtils.insertStackInFreeSlot(target, ModItems.ERROR_CORE.getDefaultInstance());
        target.displayClientMessage(Component.translatable("message.noellesroles.error404.assimilated")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
    }

    private static void tickHighDimension(ServerLevel level, SREGameWorldComponent game, long now) {
        for (ServerPlayer player : level.players()) {
            UUID id = player.getUUID();
            Long windup = WINDUP_UNTIL.get(id);
            if (windup != null && now >= windup && game.isRole(player, ModRoles.ERROR_404)) {
                WINDUP_UNTIL.remove(id);
                HIGH_UNTIL.put(id, now + HIGH_DIM);
                player.displayClientMessage(Component.translatable("message.noellesroles.error404.high"), true);
            }
            if (!isHighDimension(player)) {
                if (HIGH_UNTIL.containsKey(id) && now >= HIGH_UNTIL.get(id)) {
                    leaveHigh(player);
                }
                continue;
            }
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
            for (ServerPlayer other : level.players()) {
                if (other != player && GameUtils.isPlayerAliveAndSurvival(other)) {
                    other.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));
                }
            }
        }
    }

    private static void leaveHigh(ServerPlayer player) {
        HIGH_UNTIL.remove(player.getUUID());
        player.getAbilities().flying = false;
        player.getAbilities().mayfly = false;
        player.onUpdateAbilities();
    }

    private static void openVoid(ServerLevel level, Vec3 center, ServerPlayer source) {
        BlockPos origin = BlockPos.containing(center);
        Map<BlockPos, BlockState> saved = new HashMap<>();
        int radius = 3;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    BlockPos pos = origin.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.getDestroySpeed(level, pos) < 0) {
                        continue;
                    }
                    saved.put(pos, state);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
        ZONES.add(new VoidZone(center, level.getGameTime() + 20, level.getGameTime() + 30 * 20, source.getUUID(), saved));
        level.players().forEach(player -> player.displayClientMessage(
                Component.translatable("message.noellesroles.error404.void_open").withStyle(ChatFormatting.DARK_PURPLE), false));
    }

    private static void tickZones(ServerLevel level, long now) {
        Iterator<VoidZone> it = ZONES.iterator();
        while (it.hasNext()) {
            VoidZone zone = it.next();
            ParticleFx.region(level, ParticleTypes.REVERSE_PORTAL, GazeRays.boxAround(zone.center, 3), 8, 0.02D);
            if (!zone.fired && now >= zone.killAt) {
                zone.fired = true;
                for (ServerPlayer player : level.players()) {
                    if (GameUtils.isPlayerAliveAndSurvival(player) && player.position().distanceToSqr(zone.center) <= 12.25D) {
                        ServerPlayer owner = level.getServer().getPlayerList().getPlayer(zone.owner);
                        GameUtils.killPlayer(player, false, owner, Noellesroles.id("error_void"), true);
                    }
                }
            }
            if (now >= zone.recoverAt) {
                for (Map.Entry<BlockPos, BlockState> entry : zone.saved.entrySet()) {
                    level.setBlock(entry.getKey(), entry.getValue(), 3);
                }
                it.remove();
                level.players().forEach(player -> player.displayClientMessage(
                        Component.translatable("message.noellesroles.error404.void_recover"), false));
            }
        }
    }

    private static int countOwned(UUID hunter) {
        if (hunter == null) {
            return MAX_ASSIMILATED;
        }
        int count = 0;
        for (Assimilation assimilation : ASSIMILATIONS.values()) {
            if (hunter.equals(assimilation.hunter)) {
                count++;
            }
        }
        for (UUID owner : ASSIMILATED_OWNER.values()) {
            if (hunter.equals(owner)) {
                count++;
            }
        }
        return count;
    }

    private static void forget(UUID id) {
        ASSIMILATED.remove(id);
        ASSIMILATED_OWNER.remove(id);
        ASSIMILATIONS.remove(id);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of();
    }

    @Override
    public List<ItemStack> getDefaultItems() {
        return List.of();
    }

    private static final class Aim {
        private final UUID target;
        private final long started;

        private Aim(UUID target, long started) {
            this.target = target;
            this.started = started;
        }
    }

    private static final class Assimilation {
        private final UUID hunter;
        private final long doneAt;

        private Assimilation(UUID hunter, long doneAt) {
            this.hunter = hunter;
            this.doneAt = doneAt;
        }
    }

    private static final class VoidZone {
        private final Vec3 center;
        private final long killAt;
        private final long recoverAt;
        private final UUID owner;
        private final Map<BlockPos, BlockState> saved;
        private boolean fired;

        private VoidZone(Vec3 center, long killAt, long recoverAt, UUID owner, Map<BlockPos, BlockState> saved) {
            this.center = center;
            this.killAt = killAt;
            this.recoverAt = recoverAt;
            this.owner = owner;
            this.saved = saved;
        }
    }
}
