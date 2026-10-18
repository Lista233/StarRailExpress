package org.agmas.noellesroles.game.command_block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.game.command_block.Error404Pathfinder.Cell;
import org.agmas.noellesroles.game.command_block.item.GazeRays;
import org.agmas.noellesroles.init.ModEffects;
import org.agmas.noellesroles.init.ModItems;
import org.agmas.noellesroles.packet.Error404ActionC2SPacket;
import org.agmas.noellesroles.packet.Error404StateS2CPacket;
import org.agmas.noellesroles.packet.Error404StateS2CPacket.Contact;
import org.agmas.noellesroles.role.ModRoles;
import org.agmas.noellesroles.role_data.killer.Error404PuppetRoleData;
import org.agmas.noellesroles.role_data.killer.Error404RoleData;
import org.agmas.noellesroles.utils.RoleUtils;

import io.wifi.starrailexpress.SRE;
import io.wifi.starrailexpress.api.NormalRole;
import io.wifi.starrailexpress.api.RoleSkill;
import io.wifi.starrailexpress.api.SRERole;
import io.wifi.starrailexpress.api.data.RoleData;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.game.GameUtils;
import io.wifi.starrailexpress.util.ParticleFx;
import io.wifi.starrailexpress.util.ShopEntry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Clearable;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** 404：地面凝视同化，高维战术指挥，傀儡自爆剥夺空间。规则统一在服务端裁决。 */
public class Error404Role extends NormalRole {
    public static final int AIM_TICKS = 5 * 20;
    public static final int ASSIMILATE_TICKS = 15 * 20;
    public static final int MAX_ASSIMILATED = 2;
    public static final int WINDUP = 20;
    public static final int SKILL_COOLDOWN = 20;
    public static final int VOID_RADIUS = 3;
    public static final ResourceLocation VOID_DEATH = Noellesroles.id("error_void");
    private static final Map<UUID, Assimilation> ASSIMILATIONS = new HashMap<>();
    private static final List<VoidZone> ZONES = new ArrayList<>();
    private static final Map<ServerLevel, Map<BlockPos, SavedBlock>> ERASED = new HashMap<>();

    public Error404Role(ResourceLocation id, int color, boolean innocent, boolean killer,
            SRERole.MoodType mood, int sprint, boolean seeTime) {
        super(id, color, innocent, killer, mood, sprint, seeTime);
        setRoleData(Error404RoleData::new);
    }

    public static void registerSkill() {
        RoleSkill.register(ModRoles.ERROR_404,
                RoleSkill.skill(SRE.id("error404_dimension"), "skill.noellesroles.error404.dimension", context -> {
                    Error404RoleData data = data(context.player());
                    if (data == null) return false;
                    // 退出不能重新消耗冷却；真正的进入时间还会在服务端二次检查。
                    if (data.high || data.windupAt > 0) {
                        leaveHigh(context.player(), data);
                        message(context.player(), "low");
                        return false;
                    }
                    return context.skillReady() && beginDimension(context.player(), data);
                }).cooldownSeconds(SKILL_COOLDOWN).toggleable(true).showOnHud(true).build(),
                RoleSkill.skill(SRE.id("error404_terminal"), "skill.noellesroles.error404.terminal",
                        context -> openTerminal(context.player()))
                        .shifted(true).modeSwitch(true).showOnHud(false).build());
        RoleSkill.register(ModRoles.ERROR_404_PUPPET,
                RoleSkill.skill(SRE.id("error404_detonate"), "skill.noellesroles.error404.detonate",
                        context -> trySelfDestruct(context.player())).showOnHud(true).build());
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(level -> {
            if (SREGameWorldComponent.KEY.get(level).isRunning()) tick(level);
        });
    }

    public static Error404RoleData data(Player player) { return RoleData.getNullable(Error404RoleData.class, player); }
    public static Error404PuppetRoleData puppetData(Player player) {
        return RoleData.getNullable(Error404PuppetRoleData.class, player);
    }
    public static boolean isHighDimension(Player player) {
        if (player == null || !GameUtils.isPlayerAliveAndSurvival(player)) return false;
        Error404RoleData data = data(player);
        return data != null && data.high && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.ERROR_404);
    }
    public static boolean isAssimilated(Player player) {
        return player != null && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.ERROR_404_PUPPET);
    }
    private static boolean isHunter(ServerPlayer player) {
        return player != null && GameUtils.isPlayerAliveAndSurvival(player)
                && SREGameWorldComponent.KEY.get(player.level()).isRole(player, ModRoles.ERROR_404);
    }

    /** 所有世界的快照都带自己的维度；提前结束也必须先恢复地图再丢弃状态。 */
    public static void clear() {
        for (VoidZone zone : ZONES) restore(zone);
        ZONES.clear();
        ERASED.clear();
        ASSIMILATIONS.clear();
    }

    public static void clear(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            Error404RoleData data = data(player);
            if (data != null) leaveHigh(player, data);
            Error404PuppetRoleData puppet = puppetData(player);
            if (puppet != null) releasePuppet(player, puppet);
        }
        clear();
    }

    public static void disconnected(ServerPlayer player) {
        Error404RoleData data = data(player);
        if (data != null) leaveHigh(player, data);
        Error404PuppetRoleData puppet = puppetData(player);
        if (puppet != null) releasePuppet(player, puppet);
        ASSIMILATIONS.entrySet().removeIf(entry -> entry.getKey().equals(player.getUUID())
                || entry.getValue().owner.equals(player.getUUID()));
    }

    private static boolean beginDimension(ServerPlayer player, Error404RoleData data) {
        long now = player.level().getGameTime();
        if (!isHunter(player) || now < data.readyAt || player.hasEffect(ModEffects.SKILL_BANED)) return false;
        data.windupAt = now + WINDUP;
        data.readyAt = now + SKILL_COOLDOWN * 20L;
        data.aimTarget = null;
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, WINDUP, 4, false, false, true));
        message(player, "windup");
        return true;
    }

    public static void tickHunter(ServerPlayer player, Error404RoleData data) {
        if (!isHunter(player)) {
            leaveHigh(player, data);
            data.aimTarget = null;
            return;
        }
        long now = player.level().getGameTime();
        if (data.windupAt > 0 && now >= data.windupAt) {
            data.windupAt = 0;
            data.high = true;
            data.anchor = player.position();
            data.sync();
            applyHigh(player);
            message(player, "high");
            openTerminal(player);
        }
        if (data.high) {
            if (now % 20 == 0) applyHigh(player);
            player.setDeltaMovement(Vec3.ZERO);
            player.fallDistance = 0;
            // 服务器也约束本体，不能只依赖客户端禁移动按键。
            if (data.anchor != null && player.position().distanceToSqr(data.anchor) > 0.01) {
                player.teleportTo(data.anchor.x, data.anchor.y, data.anchor.z);
            }
            if (now % 10 == 0) sendState(player, false);
            return;
        }
        if (data.windupAt == 0) tickAim(player, data, now);
    }

    private static void applyHigh(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 40, 0, false, false, false));
        player.addEffect(new MobEffectInstance(ModEffects.TWO_DIMENSIONAL_CAMERA, 40, 4, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 255, false, false, false));
    }

    public static void leaveHigh(ServerPlayer player, Error404RoleData data) {
        boolean wasHigh = data.high;
        data.high = false;
        data.windupAt = 0;
        data.anchor = null;
        data.selected = null;
        if (!wasHigh) return;
        player.removeEffect(MobEffects.INVISIBILITY);
        player.removeEffect(ModEffects.TWO_DIMENSIONAL_CAMERA);
        player.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
        data.sync();
        ServerPlayNetworking.send(player, new Error404StateS2CPacket(false, false, List.of()));
        for (ServerPlayer puppet : player.serverLevel().players()) {
            Error404PuppetRoleData pd = puppetData(puppet);
            if (pd != null && player.getUUID().equals(pd.owner)) {
                stop(pd);
                puppet.removeEffect(ModEffects.MOVE_BANED);
            }
        }
    }

    private static void tickAim(ServerPlayer hunter, Error404RoleData data, long now) {
        if (countOwned(hunter) >= MAX_ASSIMILATED) {
            data.aimTarget = null;
            return;
        }
        List<ServerPlayer> seen = GazeRays.playersAlong(hunter, 24, 0.2);
        ServerPlayer target = seen.isEmpty() ? null : seen.getFirst();
        if (target == null || !hunter.hasLineOfSight(target) || isAssimilated(target)
                || isHunter(target) || ASSIMILATIONS.containsKey(target.getUUID())) {
            data.aimTarget = null;
            return;
        }
        if (!target.getUUID().equals(data.aimTarget)) {
            data.aimTarget = target.getUUID();
            data.aimStarted = now;
        }
        if (now % 10 == 0) hunter.displayClientMessage(Component.translatable("message.noellesroles.error404.aim",
                target.getName(), Error404Exposure.secondsLeft(data.aimStarted + AIM_TICKS, now)), true);
        if (now - data.aimStarted < AIM_TICKS) return;
        data.aimTarget = null;
        ASSIMILATIONS.put(target.getUUID(), new Assimilation(hunter.getUUID(), hunter.serverLevel(),
                SREGameWorldComponent.KEY.get(target.level()).getRole(target), now + ASSIMILATE_TICKS));
        target.displayClientMessage(Component.translatable("message.noellesroles.error404.becoming")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        hunter.displayClientMessage(Component.translatable("message.noellesroles.error404.locked", target.getName()), true);
        LegendFx.ring(hunter.serverLevel(), target.position(), 1.2F, LegendFx.RIFT);
    }

    public static void tick(ServerLevel level) {
        long now = level.getGameTime();
        Iterator<Map.Entry<UUID, Assimilation>> it = ASSIMILATIONS.entrySet().iterator();
        while (it.hasNext()) {
            var entry = it.next();
            Assimilation assimilation = entry.getValue();
            if (assimilation.level != level) continue;
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(entry.getKey());
            ServerPlayer owner = level.getServer().getPlayerList().getPlayer(assimilation.owner);
            if (!isHunter(owner) || target == null || target.serverLevel() != level || owner.serverLevel() != level
                    || !GameUtils.isPlayerAliveAndSurvival(target)
                    || SREGameWorldComponent.KEY.get(level).getRole(target) != assimilation.originalRole) {
                if (target != null) message(target, "cancelled");
                it.remove();
                continue;
            }
            if (now % 20 == 0) {
                target.displayClientMessage(Component.translatable("message.noellesroles.error404.assimilating",
                        Error404Exposure.secondsLeft(assimilation.doneAt, now)).withStyle(ChatFormatting.DARK_PURPLE), true);
                ParticleFx.sphere(level, ParticleTypes.REVERSE_PORTAL, target.position().add(0, 1, 0), 8, 0.8, 0.02);
            }
            if (now >= assimilation.doneAt) {
                it.remove(); // 先释放预留槽，再转职，期间不能再次覆盖同化计时。
                finishAssimilation(owner, target);
            }
        }
        tickZones(level, now);
    }

    private static void finishAssimilation(ServerPlayer owner, ServerPlayer target) {
        if (countOwned(owner) >= MAX_ASSIMILATED) return;
        // 使用完整转职 API 清理原职业 RoleData、技能和初始道具，且不增加一次职业场次。
        RoleUtils.changeRole(target, ModRoles.ERROR_404_PUPPET, true, false, true, false, true);
        Error404PuppetRoleData puppet = puppetData(target);
        if (puppet == null) return;
        puppet.owner = owner.getUUID();
        puppet.sync();
        giveUnique(target, ModItems.ERROR_CORE);
        target.displayClientMessage(Component.translatable("message.noellesroles.error404.assimilated")
                .withStyle(ChatFormatting.DARK_PURPLE), false);
        owner.displayClientMessage(Component.translatable("message.noellesroles.error404.finished", target.getName()), false);
    }

    public static boolean openTerminal(ServerPlayer player) {
        if (!isHighDimension(player) || findItem(player, ModItems.ERROR_TERMINAL).isEmpty()) {
            message(player, "terminal_locked");
            return false;
        }
        sendState(player, true);
        return true;
    }

    private static void sendState(ServerPlayer owner, boolean open) {
        List<Contact> contacts = new ArrayList<>();
        long now = owner.level().getGameTime();
        for (ServerPlayer player : owner.serverLevel().players()) {
            if (!GameUtils.isPlayerAliveAndSurvival(player)) continue;
            Error404PuppetRoleData puppet = puppetData(player);
            Assimilation assimilation = ASSIMILATIONS.get(player.getUUID());
            int state = puppet != null && owner.getUUID().equals(puppet.owner) ? 1
                    : assimilation != null && owner.getUUID().equals(assimilation.owner) ? 2 : 0;
            contacts.add(new Contact(player.getUUID(), player.getGameProfile().getName(), player.getX(), player.getY(),
                    player.getZ(), state, state == 2 ? Error404Exposure.secondsLeft(assimilation.doneAt, now) : 0,
                    state == 1 ? puppet.order : 0));
        }
        ServerPlayNetworking.send(owner, new Error404StateS2CPacket(true, open, List.copyOf(contacts)));
    }

    public static void handleAction(ServerPlayer actor, Error404ActionC2SPacket packet) {
        Error404RoleData data = data(actor);
        if (!isHighDimension(actor) || data == null || actor.hasEffect(ModEffects.SKILL_BANED)) return;
        long now = actor.level().getGameTime();
        if (packet.action() != Error404ActionC2SPacket.SELECT && packet.action() != Error404ActionC2SPacket.EXIT) {
            if (now < data.nextActionAt) { message(actor, "too_fast"); return; }
            data.nextActionAt = now + 2;
        }
        if (packet.action() == Error404ActionC2SPacket.EXIT) {
            leaveHigh(actor, data);
            message(actor, "low");
            return;
        }
        if (findItem(actor, ModItems.ERROR_TERMINAL).isEmpty()) return;
        if (packet.action() == Error404ActionC2SPacket.OPEN) {
            openTerminal(actor);
            return;
        }
        ServerPlayer puppet = actor.serverLevel().getServer().getPlayerList().getPlayer(packet.puppet());
        Assimilation pending = ASSIMILATIONS.get(packet.puppet());
        boolean ownsPending = pending != null && pending.level == actor.serverLevel() && actor.getUUID().equals(pending.owner);
        Error404PuppetRoleData pd = puppet == null ? null : puppetData(puppet);
        boolean owns = puppet != null && puppet.serverLevel() == actor.serverLevel()
                && GameUtils.isPlayerAliveAndSurvival(puppet) && pd != null && actor.getUUID().equals(pd.owner);
        if (!owns && !ownsPending) {
            message(actor, "not_owned");
            return;
        }
        if (packet.action() == Error404ActionC2SPacket.SELECT) {
            data.selected = packet.puppet();
            return;
        }
        if (packet.action() == Error404ActionC2SPacket.ACCELERATE) {
            if (ownsPending) accelerate(actor, packet.puppet());
            else message(actor, "accelerator_failed");
            sendState(actor, false);
            return;
        }
        if (!owns) return;
        data.selected = puppet.getUUID();
        switch (packet.action()) {
            case Error404ActionC2SPacket.HOLD -> stop(pd);
            case Error404ActionC2SPacket.DETONATE -> { if (!remoteDetonate(actor, puppet)) return; }
            case Error404ActionC2SPacket.ATTACK -> {
                ServerPlayer target = actor.serverLevel().getServer().getPlayerList().getPlayer(packet.target());
                if (target == null || target.serverLevel() != actor.serverLevel() || !GameUtils.isPlayerAliveAndSurvival(target)
                        || target == actor || isHunter(target) || isAssimilated(target) || target == puppet) {
                    message(actor, "invalid_target");
                    return;
                }
                if (!setPath(puppet, pd, target.position())) { message(actor, "no_path"); return; }
                pd.order = 2;
                pd.attackTarget = target.getUUID();
            }
            case Error404ActionC2SPacket.MOVE -> {
                if (!Double.isFinite(packet.x()) || !Double.isFinite(packet.y()) || !Double.isFinite(packet.z())
                        || Math.abs(packet.x()) > 30_000_000 || Math.abs(packet.z()) > 30_000_000
                        || Math.abs(packet.y() - puppet.getY()) > 4) return;
                if (!setPath(puppet, pd, new Vec3(packet.x(), packet.y(), packet.z()))) {
                    message(actor, "no_path");
                    return;
                }
                pd.order = 1;
                pd.attackTarget = null;
            }
            default -> { return; }
        }
        message(actor, "order_sent");
        sendState(actor, false);
    }

    /** 旧实体右键入口现在只选择本人傀儡，不再直接引爆。 */
    public static boolean tryControl(ServerPlayer actor, net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof ServerPlayer target) || !isHighDimension(actor)) return false;
        Error404PuppetRoleData puppet = puppetData(target);
        if (puppet == null || !actor.getUUID().equals(puppet.owner)) return false;
        Error404RoleData data = data(actor);
        data.selected = target.getUUID();
        return openTerminal(actor);
    }

    public static boolean useAccelerator(ServerPlayer actor) {
        Error404RoleData data = data(actor);
        if (!isHunter(actor) || data == null) return false;
        UUID selected = data.selected;
        if (selected == null || !ASSIMILATIONS.containsKey(selected)) {
            selected = ASSIMILATIONS.entrySet().stream().filter(e -> actor.getUUID().equals(e.getValue().owner))
                    .map(Map.Entry::getKey).findFirst().orElse(null);
        }
        if (selected == null) { message(actor, "accelerator_failed"); return false; }
        return accelerate(actor, selected);
    }

    private static boolean accelerate(ServerPlayer actor, UUID id) {
        Assimilation pending = ASSIMILATIONS.get(id);
        ItemStack chip = findItem(actor, ModItems.ERROR_ACCELERATOR);
        long now = actor.level().getGameTime();
        if (!isHunter(actor) || pending == null || pending.level != actor.serverLevel()
                || !actor.getUUID().equals(pending.owner) || pending.doneAt <= now || chip.isEmpty()) {
            message(actor, "accelerator_failed");
            return false;
        }
        pending.doneAt = Math.max(now, pending.doneAt - 5 * 20);
        chip.shrink(1);
        actor.getInventory().setChanged();
        message(actor, "accelerated");
        return true;
    }

    public static boolean useDetonator(ServerPlayer actor) {
        Error404RoleData data = data(actor);
        if (!isHighDimension(actor) || data == null || data.selected == null) {
            message(actor, "select_first");
            return false;
        }
        ServerPlayer target = actor.serverLevel().getServer().getPlayerList().getPlayer(data.selected);
        return target != null && remoteDetonate(actor, target);
    }

    private static boolean remoteDetonate(ServerPlayer actor, ServerPlayer target) {
        Error404PuppetRoleData puppet = puppetData(target);
        ItemStack detonator = findItem(actor, ModItems.ERROR_DETONATOR);
        if (!isHighDimension(actor) || target.serverLevel() != actor.serverLevel() || puppet == null
                || !actor.getUUID().equals(puppet.owner) || detonator.isEmpty()) {
            message(actor, "detonator_failed");
            return false;
        }
        if (!trySelfDestruct(target)) return false;
        detonator.shrink(1);
        actor.getInventory().setChanged();
        return true;
    }

    public static boolean trySelfDestruct(ServerPlayer player) {
        if (!isAssimilated(player) || !GameUtils.isPlayerAliveAndSurvival(player)) return false;
        Error404PuppetRoleData puppet = puppetData(player);
        UUID owner = puppet == null || puppet.owner == null ? player.getUUID() : puppet.owner;
        Vec3 center = player.position().add(0, 0.9, 0);
        // 先使傀儡死亡再放置区域，重复按键和旧核心都不能再次引爆。
        ServerPlayer killer = player.serverLevel().getServer().getPlayerList().getPlayer(owner);
        GameUtils.killPlayer(player, false, killer, VOID_DEATH, true);
        if (GameUtils.isPlayerAliveAndSurvival(player)) return false;
        if (puppet != null) releasePuppet(player, puppet);
        ZONES.add(new VoidZone(player.serverLevel(), center, owner, player.level().getGameTime()));
        LegendFx.ring(player.serverLevel(), center, VOID_RADIUS, LegendFx.RIFT);
        player.serverLevel().playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 0.8F, 0.6F);
        for (ServerPlayer nearby : player.serverLevel().players()) {
            if (nearby.position().distanceToSqr(center) <= 32 * 32) message(nearby, "void_open");
        }
        return true;
    }

    public static void tickPuppet(ServerPlayer player, Error404PuppetRoleData data) {
        if (!GameUtils.isPlayerAliveAndSurvival(player)) { releasePuppet(player, data); return; }
        ServerPlayer owner = data.owner == null ? null : player.serverLevel().getServer().getPlayerList().getPlayer(data.owner);
        if (!isHunter(owner) || owner.serverLevel() != player.serverLevel() || !isHighDimension(owner)) {
            stop(data);
            player.removeEffect(ModEffects.MOVE_BANED);
            return;
        }
        long now = player.level().getGameTime();
        if (now % 20 == 0) player.addEffect(new MobEffectInstance(ModEffects.MOVE_BANED, 40, 0, false, false, false));
        if (data.order == 0 || now % 2 != 0) return;
        if (data.order == 2) {
            ServerPlayer target = player.serverLevel().getServer().getPlayerList().getPlayer(data.attackTarget);
            if (target == null || target.serverLevel() != player.serverLevel() || !GameUtils.isPlayerAliveAndSurvival(target)
                    || isHunter(target) || isAssimilated(target)) { stop(data); return; }
            if (player.distanceToSqr(target) <= 2.4 * 2.4 && player.hasLineOfSight(target)) {
                if (now >= data.attackAt) {
                    data.attackAt = now + 20;
                    player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    CommandBlockCombat.tryKill(owner, target, Noellesroles.id("error404_puppet"), false);
                }
                return;
            }
            if (now >= data.repathAt && !setPath(player, data, target.position())) {
                stop(data);
                message(owner, "no_path");
                return;
            }
        }
        movePuppet(player, data, owner);
    }

    private static void stop(Error404PuppetRoleData data) {
        data.order = 0;
        data.attackTarget = null;
        data.destination = null;
        data.path = List.of();
        data.pathIndex = 0;
    }

    public static void releasePuppet(ServerPlayer player, Error404PuppetRoleData data) {
        stop(data);
        data.owner = null;
        player.removeEffect(ModEffects.MOVE_BANED);
    }

    private static Error404Pathfinder.Grid grid(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        return new Error404Pathfinder.Grid() {
            private boolean inside(Cell cell) {
                BlockPos pos = new BlockPos(cell.x(), cell.y(), cell.z());
                return level.hasChunkAt(pos) && level.getWorldBorder().isWithinBounds(pos)
                        && cell.y() >= level.getMinBuildHeight() && cell.y() < level.getMaxBuildHeight();
            }
            @Override public boolean clear(Cell cell) { return inside(cell); }
            @Override public boolean standable(Cell cell) { return inside(cell); }
        };
    }

    private static boolean setPath(ServerPlayer player, Error404PuppetRoleData data, Vec3 destination) {
        if (!player.level().isInWorldBounds(BlockPos.containing(destination))) return false;
        Error404Pathfinder.Grid grid = grid(player);
        BlockPos startPos = player.blockPosition();
        Cell start = new Cell(startPos.getX(), startPos.getY(), startPos.getZ());
        BlockPos end = BlockPos.containing(destination);
        Cell goal = new Cell(end.getX(), end.getY(), end.getZ());
        List<Cell> path = Error404Pathfinder.find(grid, start, goal);
        if (path.isEmpty()) return false;
        data.path = path;
        data.pathIndex = 0;
        data.destination = new Vec3(goal.x() + 0.5, goal.y(), goal.z() + 0.5);
        data.repathAt = player.level().getGameTime() + 20;
        return true;
    }
    private static void movePuppet(ServerPlayer player, Error404PuppetRoleData data, ServerPlayer owner) {
        if (data.pathIndex >= data.path.size()) {
            if (data.order == 1) stop(data);
            return;
        }
        Cell node = data.path.get(data.pathIndex);
        Vec3 destination = new Vec3(node.x() + 0.5, node.y(), node.z() + 0.5);
        Vec3 current = player.position();
        if (current.distanceToSqr(destination) < 0.015) { data.pathIndex++; return; }
        Vec3 delta = destination.subtract(current);
        if (delta.length() > 0.36) delta = delta.normalize().scale(0.36);
        Vec3 next = current.add(delta);
        float[] rotation = GazeRays.lookAt(player, destination.add(0, player.getEyeHeight(), 0));
        player.setYRot(rotation[0]);
        player.setYHeadRot(rotation[0]);
        player.setDeltaMovement(Vec3.ZERO);
        player.fallDistance = 0;
        player.teleportTo(next.x, next.y, next.z);
    }

    private static int countOwned(ServerPlayer owner) {
        int count = 0;
        for (Assimilation pending : ASSIMILATIONS.values()) if (owner.getUUID().equals(pending.owner)) count++;
        for (ServerPlayer player : owner.serverLevel().getServer().getPlayerList().getPlayers()) {
            Error404PuppetRoleData data = puppetData(player);
            if (data != null && owner.getUUID().equals(data.owner) && GameUtils.isPlayerAliveAndSurvival(player)) count++;
        }
        return count;
    }

    public static ItemStack findItem(Player player, Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) return stack;
        }
        return ItemStack.EMPTY;
    }

    private static void giveUnique(ServerPlayer player, Item item) {
        if (!findItem(player, item).isEmpty()) return;
        ItemStack stack = item.getDefaultInstance();
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private static void message(ServerPlayer player, String suffix) {
        player.displayClientMessage(Component.translatable("message.noellesroles.error404." + suffix), true);
        if (isHighDimension(player)) ServerPlayNetworking.send(player,
                new org.agmas.noellesroles.packet.Error404FeedbackS2CPacket(suffix));
    }

    private static void tickZones(ServerLevel level, long now) {
        for (VoidZone zone : List.copyOf(ZONES)) {
            if (zone.level != level || !ZONES.contains(zone)) continue;
            if (now >= zone.created + 30 * 20) {
                restore(zone);
                ZONES.remove(zone);
                continue;
            }
            if (!zone.carved && now >= zone.created + 20) carve(zone);
            if (now % 10 == 0) {
                ParticleFx.region(level, ParticleTypes.REVERSE_PORTAL, zone.bounds, 24, 0.02);
                LegendFx.ring(level, zone.center, VOID_RADIUS, LegendFx.RIFT);
            }
            for (ServerPlayer player : level.players()) {
                boolean inside = GameUtils.isPlayerAliveAndSurvival(player) && !isHighDimension(player)
                        && player.getBoundingBox().intersects(zone.bounds);
                boolean newEntry = inside && !zone.exposure.contains(player.getUUID());
                boolean lethal = zone.exposure.sample(player.getUUID(), inside, now);
                if (newEntry) message(player, "void_warning");
                if (lethal) {
                    ServerPlayer owner = level.getServer().getPlayerList().getPlayer(zone.owner);
                    GameUtils.killPlayer(player, false, owner, VOID_DEATH, true);
                    zone.exposure.forget(player.getUUID());
                    if (!ZONES.contains(zone)) return;
                }
            }
        }
    }

    private static void carve(VoidZone zone) {
        zone.carved = true;
        ServerLevel level = zone.level;
        Map<BlockPos, SavedBlock> saved = ERASED.computeIfAbsent(level, key -> new HashMap<>());
        BlockPos origin = BlockPos.containing(zone.center);
        // 方块和玩家使用同一个立方体边界，不再出现方块删成方形、伤害却按球形的死角。
        for (BlockPos mutable : BlockPos.betweenClosed(origin.offset(-VOID_RADIUS, -VOID_RADIUS, -VOID_RADIUS),
                origin.offset(VOID_RADIUS, VOID_RADIUS, VOID_RADIUS))) {
            BlockPos pos = mutable.immutable();
            if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
            SavedBlock existing = saved.get(pos);
            if (existing != null) { existing.references++; zone.blocks.add(pos); continue; }
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.getDestroySpeed(level, pos) < 0) continue;
            BlockEntity entity = level.getBlockEntity(pos);
            CompoundTag tag = entity == null ? null : entity.saveWithFullMetadata(level.registryAccess());
            saved.put(pos, new SavedBlock(state, tag));
            zone.blocks.add(pos);
            // 清空容器后移除，禁止掉落并暂不更新邻居，防止恢复时复制物品/门和红石连锁损坏。
            Clearable.tryClear(entity);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16 | 32);
        }
    }

    private static void restore(VoidZone zone) {
        Map<BlockPos, SavedBlock> saved = ERASED.get(zone.level);
        if (saved == null) return;
        List<Map.Entry<BlockPos, SavedBlock>> restoring = new ArrayList<>();
        for (BlockPos pos : zone.blocks) {
            SavedBlock block = saved.get(pos);
            if (block == null || --block.references > 0) continue;
            saved.remove(pos);
            restoring.add(Map.entry(pos, block));
        }
        // 先恢复整片方块，再装载方块实体，保证多格容器的邻接状态已经存在。
        for (var entry : restoring) zone.level.setBlock(entry.getKey(), entry.getValue().state, 2 | 16 | 32);
        for (var entry : restoring) {
            SavedBlock block = entry.getValue();
            BlockEntity entity = zone.level.getBlockEntity(entry.getKey());
            if (block.tag != null && entity != null) {
                entity.loadWithComponents(block.tag.copy(), zone.level.registryAccess());
                entity.setChanged();
                zone.level.sendBlockUpdated(entry.getKey(), block.state, block.state, 3);
            }
        }
        zone.blocks.clear();
        if (saved.isEmpty()) ERASED.remove(zone.level);
    }

    @Override
    public List<ShopEntry> getShopEntries() {
        return List.of(new ShopEntry(ModItems.ERROR_TERMINAL.getDefaultInstance(), 0, ShopEntry.Type.TOOL) {
            @Override public boolean canBuy(Player player) {
                return super.canBuy(player) && findItem(player, ModItems.ERROR_TERMINAL).isEmpty();
            }
        }, new ShopEntry(ModItems.ERROR_DETONATOR.getDefaultInstance(), 100, ShopEntry.Type.TOOL),
                new ShopEntry(ModItems.ERROR_ACCELERATOR.getDefaultInstance(), 80, ShopEntry.Type.TOOL));
    }

    @Override public List<ItemStack> getDefaultItems() { return List.of(ModItems.ERROR_TERMINAL.getDefaultInstance()); }

    private static final class Assimilation {
        final UUID owner;
        final ServerLevel level;
        final SRERole originalRole;
        long doneAt;
        Assimilation(UUID owner, ServerLevel level, SRERole originalRole, long doneAt) {
            this.owner = owner; this.level = level; this.originalRole = originalRole; this.doneAt = doneAt;
        }
    }
    private static final class SavedBlock {
        final BlockState state;
        final CompoundTag tag;
        int references = 1;
        SavedBlock(BlockState state, CompoundTag tag) { this.state = state; this.tag = tag; }
    }
    private static final class VoidZone {
        final ServerLevel level;
        final Vec3 center;
        final UUID owner;
        final long created;
        final AABB bounds;
        final Error404Exposure exposure = new Error404Exposure();
        final List<BlockPos> blocks = new ArrayList<>();
        boolean carved;
        VoidZone(ServerLevel level, Vec3 center, UUID owner, long created) {
            this.level = level; this.center = center; this.owner = owner; this.created = created;
            BlockPos origin = BlockPos.containing(center);
            this.bounds = new AABB(origin.getX() - VOID_RADIUS, origin.getY() - VOID_RADIUS, origin.getZ() - VOID_RADIUS,
                    origin.getX() + VOID_RADIUS + 1, origin.getY() + VOID_RADIUS + 1, origin.getZ() + VOID_RADIUS + 1);
        }
    }
}
