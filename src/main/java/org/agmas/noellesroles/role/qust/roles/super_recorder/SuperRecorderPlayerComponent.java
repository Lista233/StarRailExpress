package org.agmas.noellesroles.role.qust.roles.super_recorder;

import io.wifi.starrailexpress.api.RoleComponent;
import io.wifi.starrailexpress.cca.SREGameWorldComponent;
import io.wifi.starrailexpress.index.TMMItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import org.agmas.noellesroles.role.qust.QUSTComponentKeys;
import net.minecraft.world.phys.Vec3;
import org.ladysnake.cca.api.v3.component.tick.ServerTickingComponent;

import java.util.*;

/**
 * 超级记录员玩家组件
 * <ul>
 *   <li>管理标记进度（记录员笔记猜对 = 标记）</li>
 *   <li>追踪死亡感知位置（15 格半径）</li>
 *   <li>管理超级亡命徒时刻状态</li>
 * </ul>
 */
public class SuperRecorderPlayerComponent implements RoleComponent, ServerTickingComponent {

    private final Player player;

    /** 开局时的玩家列表（UUID → 名称） */
    private final Map<UUID, String> startPlayers = new HashMap<>();

    /** 已标记的玩家（UUID → 角色 ID） */
    private final Map<UUID, String> markedPlayers = new HashMap<>();

    /** 最近死亡位置列表（客户端渲染用，最多保留 10 个） */
    private final List<double[]> deathLocations = new ArrayList<>();

    /** 是否进入亡命徒时刻 */
    private boolean outlawMode = false;

    /** 亡命徒时刻击杀数（用于叠加速度等级） */
    private int outlawKillCount = 0;

    /** 亡命徒增益效果持续时间（tick） */
    private static final int OUTLAW_EFFECT_DURATION = 30 * 20;

    /** 开局玩家总数（用于计算 2/3 阈值） */
    private int totalPlayerCount = 0;

    /** 是否已初始化开局数据 */
    private boolean initialized = false;

    private static final int MAX_DEATH_LOCATIONS = 10;
    /** 死亡位置过期 tick（5 分钟后自动移除） */
    private static final int DEATH_LOCATION_EXPIRE_TICKS = 5 * 60 * 20;
    private int deathLocationTimer = 0;

    public SuperRecorderPlayerComponent(Player player) {
        this.player = player;
    }

    @Override
    public void init() {
        startPlayers.clear();
        markedPlayers.clear();
        deathLocations.clear();
        outlawMode = false;
        totalPlayerCount = 0;
        initialized = false;
    }

    @Override
    public void serverTick() {
        // 延迟初始化：等待所有玩家加载
        if (!initialized && player instanceof ServerPlayer sp && sp.getServer() != null) {
            SREGameWorldComponent gameWorld = SREGameWorldComponent.KEY.get(sp.level());
            List<UUID> playerUuids = new ArrayList<>();
            for (Player p : sp.level().players()) {
                if (p.getUUID().equals(sp.getUUID())) continue;
                startPlayers.put(p.getUUID(), p.getName().getString());
                playerUuids.add(p.getUUID());
            }
            totalPlayerCount = playerUuids.size();
            initialized = true;
        }

        // 亡命徒时刻：持续刷新增益效果（参照原版亡命徒 serverTick）
        if (outlawMode && player instanceof ServerPlayer sp
                && io.wifi.starrailexpress.game.GameUtils.isPlayerAliveAndSurvival(sp)) {
            if (sp.level().getGameTime() % 20 == 0) {
                if (!sp.hasEffect(MobEffects.MOVEMENT_SPEED)
                        || (sp.getEffect(MobEffects.MOVEMENT_SPEED) != null
                            && sp.getEffect(MobEffects.MOVEMENT_SPEED).getDuration() <= 21)) {
                    applyOutlawEffects(sp);
                }
            }
        }

        // 死亡位置过期清理
        if (!deathLocations.isEmpty()) {
            deathLocationTimer++;
            if (deathLocationTimer >= DEATH_LOCATION_EXPIRE_TICKS) {
                deathLocationTimer = 0;
                if (!deathLocations.isEmpty()) {
                    deathLocations.remove(0);
                }
            }
        }
    }

    // ==================== 标记系统 ====================

    public void addMark(UUID targetUuid, ResourceLocation roleId) {
        markedPlayers.put(targetUuid, roleId.toString());
        checkOutlawTransition();
    }

    public boolean isMarked(UUID targetUuid) {
        return markedPlayers.containsKey(targetUuid);
    }

    public int getMarkCount() {
        return markedPlayers.size();
    }

    public Map<UUID, String> getMarkedPlayers() {
        return markedPlayers;
    }

    /** 计算需要标记的玩家数（三分之二的总玩家数，上限 13） */
    public int getRequiredMarkCount() {
        return Math.min(13, Math.max(2, (int) Math.ceil(totalPlayerCount * 2.0 / 3.0)));
    }

    /** 检查是否进入亡命徒时刻 */
    private void checkOutlawTransition() {
        if (outlawMode) return;
        if (getMarkCount() >= getRequiredMarkCount()) {
            outlawMode = true;
            if (player instanceof ServerPlayer sp) {
                // 给予亡命徒增益效果（参照原版亡命徒）
                applyOutlawEffects(sp);
                sp.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.super_recorder.outlaw_mode_activated")
                                .withStyle(net.minecraft.ChatFormatting.DARK_RED,
                                        net.minecraft.ChatFormatting.BOLD),
                        false);
            }
        }
    }

    /** 给予/刷新亡命徒增益效果 */
    private void applyOutlawEffects(ServerPlayer sp) {
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                OUTLAW_EFFECT_DURATION, 1, true, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING,
                OUTLAW_EFFECT_DURATION, 2, true, false, true));
        sp.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE,
                OUTLAW_EFFECT_DURATION, 1, true, false, true));
    }

    /** 亡命徒时刻击杀特效（参照原版亡命徒 + 超级亡命徒） */
    public void onOutlawKill(ServerPlayer victim) {
        if (!(player instanceof ServerPlayer sp)) return;
        if (!(sp.level() instanceof ServerLevel sl)) return;

        outlawKillCount++;

        // 1. 叠加速度等级（每击杀 +1，最高 Speed X）
        var existing = sp.getEffect(MobEffects.MOVEMENT_SPEED);
        int newAmp = existing != null ? Math.min(existing.getAmplifier() + 1, 10) : 2;
        sp.removeEffect(MobEffects.MOVEMENT_SPEED);
        sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED,
                OUTLAW_EFFECT_DURATION, newAmp, false, false, true));

        // 2. 击杀奖励：时停钟或防御药剂（50% 概率）
        int r = sp.level().getRandom().nextInt(100);
        if (r < 50) {
            var inv = sp.getInventory();
            boolean hasRecovery = false;
            for (var item : inv.items) {
                if (item.isEmpty()) continue;
                if (item.is(org.agmas.noellesroles.init.ModItems.TIME_STOP_CLOCK)
                        && item.getDamageValue() > 0) {
                    item.setDamageValue(item.getDamageValue() - 1);
                    hasRecovery = true;
                    break;
                }
            }
            if (!hasRecovery) {
                sp.addItem(new net.minecraft.world.item.ItemStack(
                        org.agmas.noellesroles.init.ModItems.TIME_STOP_CLOCK));
            }
        } else {
            sp.addItem(TMMItems.DEFENSE_VIAL.getDefaultInstance());
        }

        // 3. 击杀金币
        io.wifi.starrailexpress.cca.SREPlayerShopComponent.KEY.get(sp).addToBalance(50);

        // 4. 粒子特效（参照原版亡命徒 specialEffect）
        Vec3 victimPos = victim.position();
        Vec3 killerPos = sp.position();

        // 击杀者周围红色粒子环
        for (int i = 0; i < 20; i++) {
            double angle = (Math.PI * 2 * i) / 20;
            sl.sendParticles(ParticleTypes.CRIMSON_SPORE,
                    killerPos.x() + Math.cos(angle) * 1.5,
                    killerPos.y() + 1.5,
                    killerPos.z() + Math.sin(angle) * 1.5,
                    1, 0.1, 0.1, 0.1, 0.0);
        }

        // 受害者位置暴击粒子
        sl.sendParticles(ParticleTypes.CRIT,
                victimPos.x(), victimPos.y() + 1, victimPos.z(),
                15, 0.5, 0.5, 0.5, 0.3);

        // 受害者位置灵魂火焰
        sl.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                victimPos.x(), victimPos.y() + 0.5, victimPos.z(),
                15, 0.4, 0.6, 0.4, 0.05);

        // 受害者位置大烟雾
        sl.sendParticles(ParticleTypes.LARGE_SMOKE,
                victimPos.x(), victimPos.y() + 0.8, victimPos.z(),
                10, 0.3, 0.4, 0.3, 0.02);

        // 5. 音效叠加
        sl.playSound(null, victimPos.x(), victimPos.y(), victimPos.z(),
                io.wifi.starrailexpress.index.TMMSounds.ITEM_KNIFE_STAB,
                SoundSource.PLAYERS, 1.5f, 0.8f);
        sl.playSound(null, victimPos.x(), victimPos.y(), victimPos.z(),
                SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0f, 1.2f);
        sl.playSound(null, victimPos.x(), victimPos.y(), victimPos.z(),
                SoundEvents.GHAST_SCREAM, SoundSource.PLAYERS, 0.6f, 0.7f);
    }

    public boolean isOutlawMode() {
        return outlawMode;
    }

    // ==================== 开局玩家 ====================

    public Map<UUID, String> getStartPlayers() {
        return startPlayers;
    }

    public int getTotalPlayerCount() {
        return totalPlayerCount;
    }

    // ==================== 死亡感知 ====================

    public void addDeathLocation(Vec3 pos) {
        deathLocations.add(new double[]{pos.x, pos.y, pos.z});
        if (deathLocations.size() > MAX_DEATH_LOCATIONS) {
            deathLocations.remove(0);
        }
        deathLocationTimer = 0;
    }

    public List<double[]> getDeathLocations() {
        return deathLocations;
    }

    // ==================== CCA 序列化 ====================

    @Override
    public Player getPlayer() { return player; }

    @Override
    public boolean shouldSyncWith(ServerPlayer spectator) { return true; }

    @Override
    public void clear() { init(); }

    @Override
    public void writeToSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        writeToNbt(tag, registryLookup);
    }

    @Override
    public void readFromSyncNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        readFromNbt(tag, registryLookup);
    }

    @Override
    public void writeToNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        // 标记玩家
        CompoundTag marksTag = new CompoundTag();
        for (Map.Entry<UUID, String> entry : markedPlayers.entrySet()) {
            marksTag.putString(entry.getKey().toString(), entry.getValue());
        }
        tag.put("markedPlayers", marksTag);

        // 开局玩家
        CompoundTag startTag = new CompoundTag();
        for (Map.Entry<UUID, String> entry : startPlayers.entrySet()) {
            startTag.putString(entry.getKey().toString(), entry.getValue());
        }
        tag.put("startPlayers", startTag);
        tag.putInt("totalPlayerCount", totalPlayerCount);

        // 亡命徒模式
        tag.putBoolean("outlawMode", outlawMode);
        tag.putInt("outlawKillCount", outlawKillCount);
        tag.putBoolean("initialized", initialized);

        // 死亡位置
        if (!deathLocations.isEmpty()) {
            var list = new net.minecraft.nbt.ListTag();
            for (double[] loc : deathLocations) {
                var posTag = new CompoundTag();
                posTag.putDouble("x", loc[0]);
                posTag.putDouble("y", loc[1]);
                posTag.putDouble("z", loc[2]);
                list.add(posTag);
            }
            tag.put("deathLocations", list);
        }
    }

    @Override
    public void readFromNbt(CompoundTag tag, HolderLookup.Provider registryLookup) {
        markedPlayers.clear();
        if (tag.contains("markedPlayers", Tag.TAG_COMPOUND)) {
            CompoundTag marksTag = tag.getCompound("markedPlayers");
            for (String key : marksTag.getAllKeys()) {
                try {
                    markedPlayers.put(UUID.fromString(key), marksTag.getString(key));
                } catch (Exception ignored) {}
            }
        }

        startPlayers.clear();
        if (tag.contains("startPlayers", Tag.TAG_COMPOUND)) {
            CompoundTag startTag = tag.getCompound("startPlayers");
            for (String key : startTag.getAllKeys()) {
                try {
                    startPlayers.put(UUID.fromString(key), startTag.getString(key));
                } catch (Exception ignored) {}
            }
        }
        totalPlayerCount = tag.getInt("totalPlayerCount");
        outlawMode = tag.getBoolean("outlawMode");
        outlawKillCount = tag.getInt("outlawKillCount");
        initialized = tag.getBoolean("initialized");

        deathLocations.clear();
        if (tag.contains("deathLocations", Tag.TAG_LIST)) {
            var list = tag.getList("deathLocations", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag posTag = list.getCompound(i);
                deathLocations.add(new double[]{
                        posTag.getDouble("x"),
                        posTag.getDouble("y"),
                        posTag.getDouble("z")
                });
            }
        }
    }
}
